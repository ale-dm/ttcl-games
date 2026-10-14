package com.ttcl.games.servicio;

import com.ttcl.games.dominio.Captura;
import com.ttcl.games.dominio.Captura.MapaCaptura;
import com.ttcl.games.dominio.CapturaImagen;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Repositorios.CapturaImagenRepo;
import com.ttcl.games.dominio.Repositorios.CapturaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.duende.DuendeCliente;
import com.ttcl.games.duende.DuendeModelos.PeticionLectura;
import com.ttcl.games.duende.DuendeModelos.RespuestaLectura;
import com.ttcl.games.duende.DuendeNoDisponibleException;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.Vistas.CapturaVista;
import com.ttcl.games.servicio.Vistas.LecturaCaptura;
import com.ttcl.games.stats.Capturas;
import com.ttcl.games.stats.Capturas.Evolucion;
import com.ttcl.games.stats.Capturas.Foto;
import com.ttcl.games.stats.Capturas.MapaLeido;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capturas de las estadísticas de cada uno en csstats.gg (P13). Se sube la captura, el Duende la lee con Gemini (si
 * hay clave) y la web enseña los números para revisarlos; al guardar, se apuntan con la fecha de carga y la imagen.
 * Comparando dos capturas se ve cómo ha ido entre medias. Sin usuarios (llegarán con P5), cualquiera puede subir o
 * borrar capturas, como pasa con las valoraciones.
 */
@Service
@Transactional(readOnly = true)
public class CapturasServicio {

    private static final Logger log = LoggerFactory.getLogger(CapturasServicio.class);
    /** Tamaño máximo de la imagen (también en spring.servlet.multipart). */
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final Set<String> TIPOS = Set.of("image/png", "image/jpeg", "image/webp");

    private final JugadorRepo jugadores;
    private final CapturaRepo capturas;
    private final CapturaImagenRepo imagenes;
    private final DuendeCliente duende;

    public CapturasServicio(
            JugadorRepo jugadores, CapturaRepo capturas, CapturaImagenRepo imagenes, DuendeCliente duende) {
        this.jugadores = jugadores;
        this.capturas = capturas;
        this.imagenes = imagenes;
        this.duende = duende;
    }

    /** Una imagen subida: su tipo y sus bytes. */
    public record Imagen(String tipo, byte[] datos) {

        /** La imagen con el tipo que dicen sus primeros bytes (no el que diga el navegador), o null si no es ninguno. */
        public static Imagen de(byte[] datos) {
            return new Imagen(tipoDe(datos), datos);
        }
    }

    /** PNG, JPEG o WebP por sus primeros bytes; null si no es ninguno de los tres. */
    static String tipoDe(byte[] d) {
        if (d == null || d.length < 12) {
            return null;
        }
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B'
                && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /**
     * Los números revisados de una captura, para guardarla.
     *
     * @param datos clave → número (las de {@link Capturas#CAMPOS}; las demás se ignoran)
     * @param leidaPor "gemini" si se partió de lo que leyó Gemini; si no, "mano"
     */
    public record NuevaCaptura(Map<String, Object> datos, List<MapaLeido> mapas, String leidaPor) {}

    /** Lo que se puede guardar de una captura, en orden y con su grupo, para montar el formulario. */
    public List<Capturas.Campo> campos() {
        return Capturas.CAMPOS;
    }

    /** Las capturas de un jugador en un juego, la primera cargada primero. */
    public List<CapturaVista> lista(String slug, Juego juego) {
        Jugador j = jugador(slug);
        exigirCs2(juego);
        List<CapturaVista> lista = new ArrayList<>();
        for (Captura c : capturas.findByJugadorAndJuegoOrderByCargadaEnAscIdAsc(j, juego)) {
            lista.add(vista(c, imagenes.existsById(c.getId())));
        }
        return lista;
    }

    /**
     * Lee una captura con el Duende (Gemini) para rellenar el formulario. No guarda nada. Si el Duende no responde o no
     * puede leerla, todo va vacío con el motivo, para rellenarlo a mano.
     */
    public LecturaCaptura leer(String slug, Imagen imagen) {
        jugador(slug);
        validar(imagen);
        try {
            RespuestaLectura r = duende.leerCaptura(new PeticionLectura(
                    Base64.getEncoder().encodeToString(imagen.datos()), imagen.tipo(), Capturas.claves()));
            Map<String, Double> datos = Capturas.limpiar(r.campos());
            return new LecturaCaptura(
                    r.origen() == null ? "ninguno" : r.origen(), r.modelo(), r.motivo(), datos,
                    Capturas.limpiarMapas(r.mapas()));
        } catch (DuendeNoDisponibleException e) {
            log.warn("No se pudo leer la captura: {}", e.getMessage());
            return new LecturaCaptura("ninguno", null, "duende", Map.of(), List.of());
        }
    }

    /** Guarda una captura revisada, con la fecha de ahora y la imagen si viene. */
    @Transactional
    public CapturaVista guardar(String slug, Juego juego, NuevaCaptura nueva, Imagen imagen) {
        Jugador j = jugador(slug);
        exigirCs2(juego);
        Map<String, Double> datos = Capturas.limpiar(nueva == null ? null : nueva.datos());
        if (datos.isEmpty()) {
            throw new IllegalArgumentException("Una captura sin ningún número no se guarda.");
        }
        if (imagen != null) {
            validar(imagen);
        }
        List<MapaCaptura> mapas = Capturas.limpiarMapas(nueva.mapas()).stream()
                .map(m -> new MapaCaptura(m.mapa(), m.partidas(), m.winrate()))
                .toList();
        String leidaPor = Captura.GEMINI.equals(nueva.leidaPor()) ? Captura.GEMINI : Captura.MANO;
        Captura c = capturas.save(new Captura(
                j, juego, Capturas.CSSTATS, Instant.now(), new LinkedHashMap<>(datos), leidaPor, mapas));
        if (imagen != null) {
            imagenes.save(new CapturaImagen(c.getId(), imagen.tipo(), imagen.datos()));
        }
        return vista(c, imagen != null);
    }

    /** La imagen de una captura de ese jugador, o 404. */
    public CapturaImagen imagen(String slug, long id) {
        Captura c = suya(slug, id);
        return imagenes.findById(c.getId())
                .orElseThrow(() -> new NoEncontradoException("Esa captura no tiene imagen."));
    }

    /** Borra una captura de ese jugador (por si se subió mal). */
    @Transactional
    public void borrar(String slug, long id) {
        Captura c = suya(slug, id);
        imagenes.deleteById(c.getId());
        capturas.delete(c);
    }

    /**
     * Compara dos capturas de un jugador (por defecto, la primera y la última). 404 si tiene menos de dos o si alguna
     * no es suya.
     */
    public Evolucion comparar(String slug, Juego juego, Long desde, Long hasta) {
        Jugador j = jugador(slug);
        exigirCs2(juego);
        List<Captura> lista = capturas.findByJugadorAndJuegoOrderByCargadaEnAscIdAsc(j, juego);
        if (lista.size() < 2) {
            throw new NoEncontradoException("Hacen falta al menos dos capturas para comparar.");
        }
        Captura a = desde == null ? lista.getFirst() : de(lista, desde);
        Captura b = hasta == null ? lista.getLast() : de(lista, hasta);
        if (a.getCargadaEn().isAfter(b.getCargadaEn())) {
            Captura t = a;
            a = b;
            b = t;
        }
        return Capturas.comparar(foto(a), foto(b));
    }

    /**
     * La última captura de cada jugador comparada con su referencia ({@link Capturas#referencia}), por id de jugador,
     * para el Duende. Solo los que tienen dos o más.
     */
    public Map<Long, Evolucion> evoluciones(Juego juego) {
        Map<Long, List<Foto>> porJugador = new HashMap<>();
        for (Captura c : capturas.findDeJuego(juego)) {
            porJugador.computeIfAbsent(c.getJugador().getId(), k -> new ArrayList<>()).add(foto(c));
        }
        Map<Long, Evolucion> evoluciones = new HashMap<>();
        porJugador.forEach((id, fotos) -> {
            Foto referencia = Capturas.referencia(fotos);
            if (referencia != null) {
                evoluciones.put(id, Capturas.comparar(referencia, fotos.getLast()));
            }
        });
        return evoluciones;
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private Jugador jugador(String slug) {
        return jugadores.findBySlug(slug)
                .orElseThrow(() -> new NoEncontradoException("No hay ningún jugador con el slug \"" + slug + "\"."));
    }

    /** Las capturas son de csstats, que es de CS2. */
    private static void exigirCs2(Juego juego) {
        if (juego != Juego.CS2) {
            throw new IllegalArgumentException("Las capturas de csstats son de CS2.");
        }
    }

    private Captura suya(String slug, long id) {
        Jugador j = jugador(slug);
        return capturas.findById(id)
                .filter(c -> c.getJugador().getId().equals(j.getId()))
                .orElseThrow(() -> new NoEncontradoException("No hay ninguna captura así de " + j.getNombre() + "."));
    }

    private static Captura de(List<Captura> lista, long id) {
        return lista.stream()
                .filter(c -> c.getId() == id)
                .findFirst()
                .orElseThrow(() -> new NoEncontradoException("No hay ninguna captura así de ese jugador."));
    }

    private static void validar(Imagen imagen) {
        if (imagen == null || imagen.datos() == null || imagen.datos().length == 0) {
            throw new IllegalArgumentException("Falta la imagen.");
        }
        if (imagen.tipo() == null || !TIPOS.contains(imagen.tipo())) {
            throw new IllegalArgumentException("La captura tiene que ser PNG, JPEG o WebP.");
        }
        if (imagen.datos().length > MAX_BYTES) {
            throw new IllegalArgumentException("La captura pasa de 8 MB.");
        }
    }

    static Foto foto(Captura c) {
        return new Foto(c.getId(), c.getCargadaEn(), Capturas.numeros(c.getDatos()));
    }

    private static CapturaVista vista(Captura c, boolean imagen) {
        return new CapturaVista(c.getId(), c.getJuego(), c.getFuente(), c.getCargadaEn(), c.getLeidaPor(),
                Capturas.numeros(c.getDatos()),
                c.getMapas().stream().map(m -> new MapaLeido(m.mapa(), m.partidas(), m.winrate())).toList(), imagen);
    }
}
