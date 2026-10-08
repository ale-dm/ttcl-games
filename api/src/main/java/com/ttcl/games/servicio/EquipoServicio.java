package com.ttcl.games.servicio;

import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.duende.DuendeModelos.JuegoContexto;
import com.ttcl.games.duende.DuendeModelos.JugadorContexto;
import com.ttcl.games.duende.DuendeModelos.JugadorRef;
import com.ttcl.games.duende.DuendeModelos.PeticionInsights;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.Vistas.BusquedaVista;
import com.ttcl.games.servicio.Vistas.Comparacion;
import com.ttcl.games.servicio.Vistas.CuentaVista;
import com.ttcl.games.servicio.Vistas.DetalleJuego;
import com.ttcl.games.servicio.Vistas.FilaRanking;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.PaginaPartidas;
import com.ttcl.games.servicio.Vistas.PartidaVista;
import com.ttcl.games.servicio.Vistas.Ranking;
import com.ttcl.games.stats.Estadisticas;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de lectura para la web y para el Duende. Carga el equipo entero de una vez y calcula en memoria: un
 * grupo de amigos tiene cientos o pocos miles de partidas, y así cada página es una sola ida a la base.
 */
@Service
@Transactional(readOnly = true)
public class EquipoServicio {

    private static final int PUNTOS_GRAFICA = 20;

    private final JugadorRepo jugadores;
    private final CuentaRepo cuentas;
    private final ParticipacionRepo participaciones;

    public EquipoServicio(JugadorRepo jugadores, CuentaRepo cuentas, ParticipacionRepo participaciones) {
        this.jugadores = jugadores;
        this.cuentas = cuentas;
        this.participaciones = participaciones;
    }

    /** Foto del equipo: jugadores, cuentas y participaciones por jugador y juego. */
    record Instantanea(
            List<Jugador> jugadores,
            Map<Long, List<Cuenta>> cuentas,
            Map<Long, Map<Juego, List<FilaParticipacion>>> filas,
            Map<Long, List<String>> nombresPorPartida) {

        List<FilaParticipacion> filas(Jugador j, Juego juego) {
            return filas.getOrDefault(j.getId(), Map.of()).getOrDefault(juego, List.of());
        }

        List<FilaParticipacion> todas(Jugador j) {
            return filas.getOrDefault(j.getId(), Map.of()).values().stream().flatMap(List::stream).toList();
        }

        Jugador porSlug(String slug) {
            return jugadores.stream()
                    .filter(j -> j.getSlug().equals(slug))
                    .findFirst()
                    .orElseThrow(() -> new NoEncontradoException("No hay ningún jugador con el slug \"" + slug + "\"."));
        }
    }

    Instantanea instantanea() {
        List<Jugador> lista = jugadores.findAllByOrderByNombreAsc();
        Map<Long, List<Cuenta>> porJugador = new HashMap<>();
        for (Cuenta c : cuentas.findAllConJugador()) {
            porJugador.computeIfAbsent(c.getJugador().getId(), k -> new ArrayList<>()).add(c);
        }
        Map<Long, Map<Juego, List<FilaParticipacion>>> filas = new HashMap<>();
        Map<Long, List<String>> nombres = new HashMap<>();
        for (Participacion p : participaciones.findAllCompletas()) {
            filas.computeIfAbsent(p.getJugador().getId(), k -> new EnumMap<>(Juego.class))
                    .computeIfAbsent(p.getJuego(), k -> new ArrayList<>())
                    .add(FilaParticipacion.de(p));
            nombres.computeIfAbsent(p.getPartida().getId(), k -> new ArrayList<>()).add(p.getJugador().getNombre());
        }
        return new Instantanea(lista, porJugador, filas, nombres);
    }

    // ─── Vistas de jugador ──────────────────────────────────────────────────

    private static List<CuentaVista> cuentasVista(Instantanea foto, Jugador j) {
        return foto.cuentas().getOrDefault(j.getId(), List.of()).stream()
                .map(c -> new CuentaVista(c.getJuego(), c.getNombreExterno(), c.getUltimaSync()))
                .toList();
    }

    private static List<ResumenJuego> resumenes(Instantanea foto, Jugador j) {
        List<ResumenJuego> lista = new ArrayList<>();
        for (Juego juego : Juego.values()) {
            List<FilaParticipacion> filas = foto.filas(j, juego);
            if (!filas.isEmpty()) {
                lista.add(Estadisticas.resumir(juego, filas));
            }
        }
        return lista;
    }

    private static JugadorVista vista(Instantanea foto, Jugador j) {
        return new JugadorVista(j.getSlug(), j.getNombre(), j.isDemo(), cuentasVista(foto, j), resumenes(foto, j));
    }

    /** Todo el equipo con sus resúmenes, por nombre. */
    public List<JugadorVista> equipo() {
        Instantanea foto = instantanea();
        return foto.jugadores().stream().map(j -> vista(foto, j)).toList();
    }

    public JugadorVista jugador(String slug) {
        Instantanea foto = instantanea();
        return vista(foto, foto.porSlug(slug));
    }

    /** Busca por nombre, slug o nick de cualquier cuenta, sin distinguir mayúsculas ni tildes. Vacío: todos. */
    public List<BusquedaVista> buscar(String texto) {
        String q = normalizar(texto);
        Instantanea foto = instantanea();
        return foto.jugadores().stream()
                .filter(j -> q.isEmpty()
                        || normalizar(j.getNombre()).contains(q)
                        || normalizar(j.getSlug()).contains(q)
                        || foto.cuentas().getOrDefault(j.getId(), List.of()).stream()
                                .anyMatch(c -> normalizar(c.getNombreExterno()).contains(q)))
                .limit(q.isEmpty() ? Long.MAX_VALUE : 10)
                .map(j -> new BusquedaVista(j.getSlug(), j.getNombre(), cuentasVista(foto, j)))
                .toList();
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    // ─── Detalle por juego ──────────────────────────────────────────────────

    /** Media del resto del equipo en un juego, sin contar a {@code jugador}. */
    private static MediasEquipo mediasSin(Instantanea foto, Jugador jugador, Juego juego) {
        List<ResumenJuego> otros = foto.jugadores().stream()
                .filter(o -> !o.getId().equals(jugador.getId()))
                .map(o -> foto.filas(o, juego))
                .filter(f -> !f.isEmpty())
                .map(f -> Estadisticas.resumir(juego, f))
                .toList();
        return Estadisticas.mediasEquipo(otros);
    }

    private static JuegoContexto contexto(Instantanea foto, Jugador j, Juego juego) {
        List<FilaParticipacion> filas = foto.filas(j, juego);
        return new JuegoContexto(
                juego,
                Estadisticas.resumir(juego, filas),
                Estadisticas.resumir(juego, Estadisticas.recientes(filas, Estadisticas.PARTIDAS_RECIENTES)),
                mediasSin(foto, j, juego),
                Estadisticas.desglose(juego, filas));
    }

    public DetalleJuego detalle(String slug, Juego juego) {
        Instantanea foto = instantanea();
        Jugador j = foto.porSlug(slug);
        List<FilaParticipacion> filas = foto.filas(j, juego);
        if (filas.isEmpty()) {
            throw new NoEncontradoException(j.getNombre() + " no tiene partidas guardadas de " + juego.nombre() + ".");
        }
        JuegoContexto c = contexto(foto, j, juego);
        return new DetalleJuego(juego, c.resumen(), c.reciente(), c.equipo(), c.desglose(),
                Estadisticas.serie(juego, filas, PUNTOS_GRAFICA));
    }

    /** Historial de partidas, la más reciente primero, con los compañeros del equipo que estaban. */
    public PaginaPartidas partidas(String slug, Juego juego, int limite, int offset) {
        Instantanea foto = instantanea();
        Jugador j = foto.porSlug(slug);
        List<FilaParticipacion> filas = (juego == null ? foto.todas(j) : foto.filas(j, juego)).stream()
                .sorted(Comparator.comparing(FilaParticipacion::jugadaEn).reversed())
                .toList();
        List<PartidaVista> items = filas.stream()
                .skip(Math.max(0, offset))
                .limit(Math.clamp(limite, 1, 100))
                .map(f -> new PartidaVista(
                        f.partidaId(), f.juego(), f.jugadaEn(), f.modo(), f.gano(), f.kills(), f.muertes(),
                        f.asistencias(), f.datos(),
                        foto.nombresPorPartida().getOrDefault(f.partidaId(), List.of()).stream()
                                .filter(n -> !n.equals(j.getNombre()))
                                .toList()))
                .toList();
        return new PaginaPartidas(items, filas.size());
    }

    // ─── Comparación y ranking ──────────────────────────────────────────────

    public Comparacion comparar(String slugA, String slugB, Juego juego) {
        Instantanea foto = instantanea();
        Jugador a = foto.porSlug(slugA);
        Jugador b = foto.porSlug(slugB);
        List<FilaParticipacion> fa = foto.filas(a, juego);
        List<FilaParticipacion> fb = foto.filas(b, juego);
        ResumenJuego ra = fa.isEmpty() ? null : Estadisticas.resumir(juego, fa);
        ResumenJuego rb = fb.isEmpty() ? null : Estadisticas.resumir(juego, fb);
        return new Comparacion(
                new JugadorRef(a.getSlug(), a.getNombre()),
                new JugadorRef(b.getSlug(), b.getNombre()),
                juego,
                ra,
                rb,
                ra == null || rb == null ? List.of() : Estadisticas.comparar(ra, rb));
    }

    /** Jugadores con partidas en el juego. La web ordena por la métrica que se elija. */
    public Ranking ranking(Juego juego) {
        Instantanea foto = instantanea();
        List<FilaRanking> filas = foto.jugadores().stream()
                .filter(j -> !foto.filas(j, juego).isEmpty())
                .map(j -> new FilaRanking(j.getSlug(), j.getNombre(), Estadisticas.resumir(juego, foto.filas(j, juego))))
                .toList();
        return new Ranking(juego, Estadisticas.metricas(juego), filas);
    }

    // ─── Para el Duende ─────────────────────────────────────────────────────

    /** Petición de recomendaciones de un jugador en un juego. */
    public PeticionInsights peticionInsights(String slug, Juego juego, String lang) {
        Instantanea foto = instantanea();
        Jugador j = foto.porSlug(slug);
        if (foto.filas(j, juego).isEmpty()) {
            throw new NoEncontradoException(j.getNombre() + " no tiene partidas guardadas de " + juego.nombre() + ".");
        }
        return peticion(foto, j, juego, lang);
    }

    private static PeticionInsights peticion(Instantanea foto, Jugador j, Juego juego, String lang) {
        JuegoContexto c = contexto(foto, j, juego);
        return new PeticionInsights(lang, new JugadorRef(j.getSlug(), j.getNombre()), juego, c.resumen(),
                c.reciente(), c.equipo(), c.desglose());
    }

    /** Peticiones de recomendaciones de todo el equipo (una por jugador y juego), para las tarjetas. */
    public List<PeticionInsights> peticionesEquipo(Juego soloJuego, String lang) {
        Instantanea foto = instantanea();
        List<PeticionInsights> lista = new ArrayList<>();
        for (Jugador j : foto.jugadores()) {
            for (Juego juego : Juego.values()) {
                if ((soloJuego == null || soloJuego == juego) && !foto.filas(j, juego).isEmpty()) {
                    lista.add(peticion(foto, j, juego, lang));
                }
            }
        }
        return lista;
    }

    /** Todo el equipo con sus datos por juego, para el chat. */
    public List<JugadorContexto> contextoEquipo() {
        Instantanea foto = instantanea();
        return foto.jugadores().stream()
                .map(j -> new JugadorContexto(
                        j.getSlug(),
                        j.getNombre(),
                        Arrays.stream(Juego.values())
                                .filter(juego -> !foto.filas(j, juego).isEmpty())
                                .map(juego -> contexto(foto, j, juego))
                                .toList()))
                .toList();
    }

    /** Comprueba que los slugs existen (el chat no debe hablar de alguien que no está). */
    public List<String> slugsValidos(List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        Map<String, Jugador> porSlug = new LinkedHashMap<>();
        jugadores.findAll().forEach(j -> porSlug.put(j.getSlug(), j));
        return slugs.stream().filter(Objects::nonNull).distinct().filter(porSlug::containsKey).limit(2).toList();
    }
}
