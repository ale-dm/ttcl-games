package com.ttcl.games.servicio;

import com.ttcl.games.config.TtclProperties;
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
import com.ttcl.games.servicio.Vistas.GruposJuego;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.PaginaPartidas;
import com.ttcl.games.servicio.Vistas.PartidaVista;
import com.ttcl.games.servicio.Vistas.Ranking;
import com.ttcl.games.stats.Estadisticas;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.Presencia;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Modelos.ResumenPeriodo;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import com.ttcl.games.stats.Periodo;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /** Zona del equipo: con ella se sabe a qué hora del día se jugó cada partida. */
    private final ZoneId zona;

    public EquipoServicio(
            JugadorRepo jugadores, CuentaRepo cuentas, ParticipacionRepo participaciones, TtclProperties props) {
        this.jugadores = jugadores;
        this.cuentas = cuentas;
        this.participaciones = participaciones;
        this.zona = props.zona();
    }

    /**
     * Foto del equipo: jugadores, cuentas, participaciones por jugador y juego, y quién jugó cada partida. Se puede
     * recortar a un periodo ({@link #desde}); {@code juegos} sigue diciendo a qué ha jugado cada uno alguna vez.
     */
    record Instantanea(
            List<Jugador> jugadores,
            Map<Long, List<Cuenta>> cuentas,
            Map<Long, Map<Juego, List<FilaParticipacion>>> filas,
            Map<Juego, Map<Long, List<Presencia>>> presencias,
            Map<Long, Set<Juego>> juegos) {

        List<FilaParticipacion> filas(Jugador j, Juego juego) {
            return filas.getOrDefault(j.getId(), Map.of()).getOrDefault(juego, List.of());
        }

        /** Si tiene partidas de ese juego, aunque no sean del periodo de la foto. */
        boolean juega(Jugador j, Juego juego) {
            return juegos.getOrDefault(j.getId(), Set.of()).contains(juego);
        }

        /** La misma foto solo con las partidas jugadas desde {@code inicio}; con null, igual. */
        Instantanea desde(Instant inicio) {
            if (inicio == null) {
                return this;
            }
            Map<Long, Map<Juego, List<FilaParticipacion>>> recortadas = new HashMap<>();
            Set<Long> partidas = new HashSet<>();
            filas.forEach((jugador, porJuego) -> porJuego.forEach((juego, lista) -> {
                List<FilaParticipacion> dentro = Estadisticas.desde(lista, inicio);
                if (!dentro.isEmpty()) {
                    recortadas.computeIfAbsent(jugador, k -> new EnumMap<>(Juego.class)).put(juego, dentro);
                    dentro.forEach(f -> partidas.add(f.partidaId()));
                }
            }));
            Map<Juego, Map<Long, List<Presencia>>> presentes = new EnumMap<>(Juego.class);
            presencias.forEach((juego, porPartida) -> porPartida.forEach((id, lista) -> {
                if (partidas.contains(id)) {
                    presentes.computeIfAbsent(juego, k -> new HashMap<>()).put(id, lista);
                }
            }));
            return new Instantanea(jugadores, cuentas, recortadas, presentes, juegos);
        }

        /** Lanza un 404 si no tiene ni una partida de ese juego (en un periodo sin partidas, los datos van vacíos). */
        void exigirPartidas(Jugador j, Juego juego) {
            if (!juega(j, juego)) {
                throw new NoEncontradoException(
                        j.getNombre() + " no tiene partidas guardadas de " + juego.nombre() + ".");
            }
        }

        /** Quién del equipo jugó cada partida de un juego, por id de partida. */
        Map<Long, List<Presencia>> presencias(Juego juego) {
            return presencias.getOrDefault(juego, Map.of());
        }

        List<FilaParticipacion> todas(Jugador j) {
            return filas.getOrDefault(j.getId(), Map.of()).values().stream().flatMap(List::stream).toList();
        }

        /** Rol del jugador en ese juego, o null si no lo ha dicho. */
        String rol(Jugador j, Juego juego) {
            return cuentas.getOrDefault(j.getId(), List.of()).stream()
                    .filter(c -> c.getJuego() == juego)
                    .map(Cuenta::getRol)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
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
        Map<Juego, Map<Long, List<Presencia>>> presencias = new EnumMap<>(Juego.class);
        for (Participacion p : participaciones.findAllCompletas()) {
            filas.computeIfAbsent(p.getJugador().getId(), k -> new EnumMap<>(Juego.class))
                    .computeIfAbsent(p.getJuego(), k -> new ArrayList<>())
                    .add(FilaParticipacion.de(p));
            presencias.computeIfAbsent(p.getJuego(), k -> new HashMap<>())
                    .computeIfAbsent(p.getPartida().getId(), k -> new ArrayList<>())
                    .add(new Presencia(p.getJugador().getSlug(), p.getJugador().getNombre(), p.getGano()));
        }
        Map<Long, Set<Juego>> juegos = new HashMap<>();
        filas.forEach((jugador, porJuego) -> juegos.put(jugador, Set.copyOf(porJuego.keySet())));
        return new Instantanea(lista, porJugador, filas, presencias, juegos);
    }

    /** La foto con solo las partidas del periodo (contado hacia atrás desde ahora). */
    Instantanea instantanea(Periodo periodo) {
        return instantanea().desde(periodo.inicio(Instant.now()));
    }

    // ─── Vistas de jugador ──────────────────────────────────────────────────

    private static List<CuentaVista> cuentasVista(Instantanea foto, Jugador j) {
        return foto.cuentas().getOrDefault(j.getId(), List.of()).stream()
                .map(c -> new CuentaVista(c.getJuego(), c.getNombreExterno(), c.getRol(), c.getUltimaSync()))
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

    /** Perfil con el resumen de cada juego en el periodo (los juegos sin partidas en él no salen). */
    public JugadorVista jugador(String slug, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
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

    private JuegoContexto contexto(Instantanea foto, Jugador j, Juego juego, List<ResumenPeriodo> periodos) {
        List<FilaParticipacion> filas = foto.filas(j, juego);
        return new JuegoContexto(
                juego,
                foto.rol(j, juego),
                Estadisticas.resumir(juego, filas),
                Estadisticas.resumir(juego, Estadisticas.recientes(filas, Estadisticas.PARTIDAS_RECIENTES)),
                mediasSin(foto, j, juego),
                Estadisticas.desglose(juego, filas),
                Estadisticas.sinergias(juego, j.getSlug(), filas, foto.presencias(juego)),
                Estadisticas.sesiones(juego, filas, zona),
                periodos);
    }

    /** Con quién del equipo juega mejor un jugador en un juego (y cómo le va solo), en el periodo. */
    public Sinergias sinergias(String slug, Juego juego, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, juego);
        return Estadisticas.sinergias(juego, slug, foto.filas(j, juego), foto.presencias(juego));
    }

    /** Cómo le va según cuándo juega (orden en la sesión, después de ganar o de perder, hora del día), en el periodo. */
    public Sesiones sesiones(String slug, Juego juego, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, juego);
        return Estadisticas.sesiones(juego, foto.filas(j, juego), zona);
    }

    /** Dúos y tríos del equipo en un juego, o en todos los que tengan partidas si {@code juego} es null. */
    public List<GruposJuego> grupos(Juego juego) {
        Instantanea foto = instantanea();
        return Arrays.stream(Juego.values())
                .filter(g -> (juego == null || juego == g) && !foto.presencias(g).isEmpty())
                .map(g -> new GruposJuego(
                        g, Estadisticas.grupos(foto.presencias(g), 2), Estadisticas.grupos(foto.presencias(g), 3)))
                .toList();
    }

    /**
     * Resumen, últimas partidas, media del resto del equipo (en el mismo periodo), desglose y serie. Si en el periodo
     * no jugó, todo va vacío (0 partidas); el 404 es solo para quien no ha jugado nunca a ese juego.
     */
    public DetalleJuego detalle(String slug, Juego juego, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, juego);
        JuegoContexto c = contexto(foto, j, juego, List.of());
        return new DetalleJuego(juego, c.resumen(), c.reciente(), c.equipo(), c.desglose(),
                Estadisticas.serie(juego, foto.filas(j, juego), PUNTOS_GRAFICA));
    }

    /** Historial de partidas del periodo, la más reciente primero, con los compañeros del equipo que estaban. */
    public PaginaPartidas partidas(String slug, Juego juego, int limite, int offset, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
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
                        foto.presencias(f.juego()).getOrDefault(f.partidaId(), List.of()).stream()
                                .filter(p -> !p.slug().equals(j.getSlug()))
                                .map(Presencia::nombre)
                                .toList()))
                .toList();
        return new PaginaPartidas(items, filas.size());
    }

    // ─── Comparación y ranking ──────────────────────────────────────────────

    /** Cara a cara en el periodo. Quien no jugó en él lleva el resumen a null. */
    public Comparacion comparar(String slugA, String slugB, Juego juego, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
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

    /** Jugadores con partidas en el juego dentro del periodo. La web ordena por la métrica que se elija. */
    public Ranking ranking(Juego juego, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
        List<FilaRanking> filas = foto.jugadores().stream()
                .filter(j -> !foto.filas(j, juego).isEmpty())
                .map(j -> new FilaRanking(j.getSlug(), j.getNombre(), Estadisticas.resumir(juego, foto.filas(j, juego))))
                .toList();
        return new Ranking(juego, Estadisticas.metricas(juego), filas);
    }

    // ─── Para el Duende ─────────────────────────────────────────────────────

    /** Petición de recomendaciones de un jugador en un juego, con las partidas del periodo. */
    public PeticionInsights peticionInsights(String slug, Juego juego, String lang, Periodo periodo) {
        Instantanea foto = instantanea(periodo);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, juego);
        return peticion(foto, j, juego, lang);
    }

    private PeticionInsights peticion(Instantanea foto, Jugador j, Juego juego, String lang) {
        JuegoContexto c = contexto(foto, j, juego, List.of());
        return new PeticionInsights(lang, new JugadorRef(j.getSlug(), j.getNombre()), juego, c.rol(), c.resumen(),
                c.reciente(), c.equipo(), c.desglose(), c.sinergias(), c.sesiones());
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

    /**
     * Todo el equipo con sus datos por juego, para el chat. Con todas las partidas y, además, un resumen de los últimos
     * 7 y 30 días (con la media del equipo en esos días) para preguntas como "¿cómo voy esta semana?".
     */
    public List<JugadorContexto> contextoEquipo() {
        Instantanea foto = instantanea();
        Instant ahora = Instant.now();
        Map<Periodo, Instantanea> recortes = new EnumMap<>(Periodo.class);
        Periodo.RECORTADOS.forEach(p -> recortes.put(p, foto.desde(p.inicio(ahora))));
        return foto.jugadores().stream()
                .map(j -> new JugadorContexto(
                        j.getSlug(),
                        j.getNombre(),
                        Arrays.stream(Juego.values())
                                .filter(juego -> !foto.filas(j, juego).isEmpty())
                                .map(juego -> contexto(foto, j, juego, periodos(recortes, j, juego)))
                                .toList()))
                .toList();
    }

    /** Resumen de cada periodo en el que jugó (los que no tienen partidas no van). */
    private static List<ResumenPeriodo> periodos(Map<Periodo, Instantanea> recortes, Jugador j, Juego juego) {
        List<ResumenPeriodo> lista = new ArrayList<>();
        recortes.forEach((periodo, foto) -> {
            List<FilaParticipacion> filas = foto.filas(j, juego);
            if (!filas.isEmpty()) {
                lista.add(new ResumenPeriodo(periodo, Estadisticas.resumir(juego, filas), mediasSin(foto, j, juego)));
            }
        });
        return lista;
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
