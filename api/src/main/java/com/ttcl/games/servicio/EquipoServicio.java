package com.ttcl.games.servicio;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.ConsejoDado;
import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Muestra;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Repositorios.ConsejoDadoRepo;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.MuerteMapaRepo;
import com.ttcl.games.dominio.Repositorios.MuestraRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.RondaRepo;
import com.ttcl.games.dominio.Ronda;
import com.ttcl.games.duende.DuendeModelos.JuegoContexto;
import com.ttcl.games.duende.DuendeModelos.JugadorContexto;
import com.ttcl.games.duende.DuendeModelos.JugadorRef;
import com.ttcl.games.duende.DuendeModelos.PeticionInsights;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.Vistas.BusquedaVista;
import com.ttcl.games.servicio.Vistas.Comparacion;
import com.ttcl.games.servicio.Vistas.ConsultaPartidas;
import com.ttcl.games.servicio.Vistas.CuentaVista;
import com.ttcl.games.servicio.Vistas.DetalleJuego;
import com.ttcl.games.servicio.Vistas.FilaRanking;
import com.ttcl.games.servicio.Vistas.GruposJuego;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.Novedad;
import com.ttcl.games.servicio.Vistas.PaginaPartidas;
import com.ttcl.games.servicio.Vistas.PartidaVista;
import com.ttcl.games.servicio.Vistas.Ranking;
import com.ttcl.games.servicio.Vistas.RondasPartida;
import com.ttcl.games.stats.Estadisticas;
import com.ttcl.games.stats.FiltroPartidas;
import com.ttcl.games.stats.Informes;
import com.ttcl.games.stats.Modelos.CalorMapa;
import com.ttcl.games.stats.Modelos.ConsejoAnterior;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.FilaRonda;
import com.ttcl.games.stats.Modelos.FilaSemana;
import com.ttcl.games.stats.Modelos.Hecho;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.Presencia;
import com.ttcl.games.stats.Modelos.ResumenDemos;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Modelos.ResumenPeriodo;
import com.ttcl.games.stats.Modelos.SemanaJuego;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import com.ttcl.games.stats.Periodo;
import com.ttcl.games.stats.Rondas;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    /** Novedades que se dan de una vez (P11); las demás, en la siguiente consulta. */
    public static final int MAX_NOVEDADES = 100;
    static final Duration SEMANA = Duration.ofDays(7);

    private final JugadorRepo jugadores;
    private final CuentaRepo cuentas;
    private final ParticipacionRepo participaciones;
    private final ConsejoDadoRepo consejosDados;
    private final MuestraRepo muestras;
    private final RondaRepo rondas;
    private final MuerteMapaRepo muertesMapa;
    /** Zona del equipo: con ella se sabe a qué hora del día se jugó cada partida. */
    private final ZoneId zona;

    public EquipoServicio(
            JugadorRepo jugadores, CuentaRepo cuentas, ParticipacionRepo participaciones,
            ConsejoDadoRepo consejosDados, MuestraRepo muestras, RondaRepo rondas, MuerteMapaRepo muertesMapa,
            TtclProperties props) {
        this.jugadores = jugadores;
        this.cuentas = cuentas;
        this.participaciones = participaciones;
        this.consejosDados = consejosDados;
        this.muestras = muestras;
        this.rondas = rondas;
        this.muertesMapa = muertesMapa;
        this.zona = props.zona();
    }

    /**
     * Foto del equipo: jugadores, cuentas, participaciones por jugador y juego, quién jugó cada partida y los consejos
     * que se les han dado. Se puede recortar a un periodo ({@link #desde}); {@code juegos} sigue diciendo a qué ha
     * jugado cada uno alguna vez.
     */
    record Instantanea(
            List<Jugador> jugadores,
            Map<Long, List<Cuenta>> cuentas,
            Map<Long, Map<Juego, List<FilaParticipacion>>> filas,
            Map<Juego, Map<Long, List<Presencia>>> presencias,
            Map<Long, Set<Juego>> juegos,
            Map<Long, Map<Juego, List<ConsejoAnterior>>> consejos) {

        List<FilaParticipacion> filas(Jugador j, Juego juego) {
            return filas.getOrDefault(j.getId(), Map.of()).getOrDefault(juego, List.of());
        }

        /** Consejos que se le dieron en ese juego (en una foto recortada, ninguno: el seguimiento es con todas). */
        List<ConsejoAnterior> consejos(Jugador j, Juego juego) {
            return consejos.getOrDefault(j.getId(), Map.of()).getOrDefault(juego, List.of());
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
            return new Instantanea(jugadores, cuentas, recortadas, presentes, juegos, Map.of());
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

        Optional<Cuenta> cuenta(Jugador j, Juego juego) {
            return cuentas.getOrDefault(j.getId(), List.of()).stream().filter(c -> c.getJuego() == juego).findFirst();
        }

        /** Rol del jugador en ese juego, o null si no lo ha dicho. */
        String rol(Jugador j, Juego juego) {
            return cuenta(j, juego).map(Cuenta::getRol).orElse(null);
        }

        /** Nivel de FACEIT de su cuenta de ese juego, o null. */
        Integer nivel(Jugador j, Juego juego) {
            return cuenta(j, juego).map(Cuenta::getNivel).orElse(null);
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
        Map<Long, Map<Juego, List<ConsejoAnterior>>> consejos = new HashMap<>();
        for (ConsejoDado c : consejosDados.findAllDesde(Instant.now().minus(Estadisticas.VENTANA_SEGUIMIENTO))) {
            consejos.computeIfAbsent(c.getJugador().getId(), k -> new EnumMap<>(Juego.class))
                    .computeIfAbsent(c.getJuego(), k -> new ArrayList<>())
                    .add(new ConsejoAnterior(c.getInsight(), c.getMetrica(), c.getValor(), c.getDadoEn()));
        }
        return new Instantanea(lista, porJugador, filas, presencias, juegos, consejos);
    }

    /** La foto con solo las partidas del periodo (contado hacia atrás desde ahora). */
    Instantanea instantanea(Periodo periodo) {
        return instantanea().desde(periodo.inicio(Instant.now()));
    }

    // ─── Vistas de jugador ──────────────────────────────────────────────────

    private static List<CuentaVista> cuentasVista(Instantanea foto, Jugador j) {
        return foto.cuentas().getOrDefault(j.getId(), List.of()).stream()
                .map(c -> new CuentaVista(
                        c.getJuego(), c.getNombreExterno(), c.getRol(), c.getNivel(), c.getElo(), c.getUltimaSync()))
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

    /**
     * Todo lo de un jugador en un juego. {@code muestras} son las partidas de jugadores de cada nivel (P8), para
     * compararle con los de su nivel, y {@code rondas}, las de las demos analizadas (P12);
     * {@link MuestrasPorNivel#NINGUNA} y {@link RondasEquipo#NINGUNA} si no hacen falta.
     */
    private JuegoContexto contexto(
            Instantanea foto, Jugador j, Juego juego, List<ResumenPeriodo> periodos, MuestrasPorNivel muestras,
            RondasEquipo rondas) {
        List<FilaParticipacion> filas = foto.filas(j, juego);
        ResumenJuego resumen = Estadisticas.resumir(juego, filas);
        Integer nivel = foto.nivel(j, juego);
        Integer elo = foto.cuenta(j, juego).map(Cuenta::getElo).orElse(null);
        return new JuegoContexto(
                juego,
                foto.rol(j, juego),
                resumen,
                Estadisticas.resumir(juego, Estadisticas.recientes(filas, Estadisticas.PARTIDAS_RECIENTES)),
                mediasSin(foto, j, juego),
                Estadisticas.desglose(juego, filas),
                Estadisticas.sinergias(juego, j.getSlug(), filas, foto.presencias(juego)),
                Estadisticas.sesiones(juego, filas, zona),
                periodos,
                Estadisticas.seguimiento(juego, filas, foto.consejos(j, juego), Instant.now()),
                Estadisticas.comparativaNivel(juego, nivel, elo, resumen, muestras.de(juego, nivel)),
                juego == Juego.CS2 ? rondas.resumen(foto, j) : null);
    }

    /** Las rondas de las demos analizadas de cada jugador del equipo, por id de jugador (P12). */
    record RondasEquipo(Map<Long, List<FilaRonda>> porJugador) {
        static final RondasEquipo NINGUNA = new RondasEquipo(Map.of());

        List<FilaRonda> de(Jugador j) {
            return porJugador.getOrDefault(j.getId(), List.of());
        }

        /** Lo de sus demos frente al resto del equipo de la foto, o null si no tiene ninguna analizada. */
        ResumenDemos resumen(Instantanea foto, Jugador j) {
            List<FilaRonda> suyas = de(j);
            if (suyas.isEmpty()) {
                return null;
            }
            List<List<FilaRonda>> otros = foto.jugadores().stream()
                    .filter(o -> !o.getId().equals(j.getId()))
                    .map(this::de)
                    .toList();
            return Rondas.resumen(suyas, otros);
        }
    }

    /** Las rondas analizadas de las partidas jugadas desde {@code inicio} (null: todas), por jugador. */
    private RondasEquipo rondas(Instant inicio) {
        Map<Long, List<FilaRonda>> porJugador = new HashMap<>();
        for (Ronda r : rondas.findDesde(inicio == null ? Instant.EPOCH : inicio)) {
            porJugador.computeIfAbsent(r.getJugador().getId(), k -> new ArrayList<>()).add(FilaRonda.de(r));
        }
        porJugador.values().forEach(l -> l.sort(
                Comparator.comparing(FilaRonda::jugadaEn).thenComparingLong(FilaRonda::partidaId)
                        .thenComparingInt(FilaRonda::ronda)));
        return new RondasEquipo(porJugador);
    }

    /** Partidas de jugadores que no son del equipo, por juego y nivel (P8). */
    record MuestrasPorNivel(Map<Juego, Map<Integer, List<FilaParticipacion>>> porJuego) {
        static final MuestrasPorNivel NINGUNA = new MuestrasPorNivel(Map.of());

        List<FilaParticipacion> de(Juego juego, Integer nivel) {
            return nivel == null ? List.of() : porJuego.getOrDefault(juego, Map.of()).getOrDefault(nivel, List.of());
        }
    }

    /** Las muestras de los niveles que tiene alguien del equipo (las demás no hacen falta). */
    private MuestrasPorNivel muestras(Instantanea foto) {
        Map<Juego, Set<Integer>> niveles = new EnumMap<>(Juego.class);
        foto.cuentas().values().stream()
                .flatMap(List::stream)
                .filter(c -> c.getNivel() != null)
                .forEach(c -> niveles.computeIfAbsent(c.getJuego(), k -> new HashSet<>()).add(c.getNivel()));
        Map<Juego, Map<Integer, List<FilaParticipacion>>> porJuego = new EnumMap<>(Juego.class);
        niveles.forEach((juego, lista) -> porJuego.put(juego, muestras.findByJuegoAndNivelIn(juego, lista).stream()
                .collect(Collectors.groupingBy(
                        Muestra::getNivel, Collectors.mapping(EquipoServicio::fila, Collectors.toList())))));
        return new MuestrasPorNivel(porJuego);
    }

    /** Una muestra como una participación más, para calcular con las mismas funciones. */
    private static FilaParticipacion fila(Muestra m) {
        return new FilaParticipacion(m.getId(), m.getJuego(), m.getFecha().atStartOfDay(ZoneOffset.UTC).toInstant(),
                null, m.getMapa(), m.getGano(), m.getKills(), m.getMuertes(), m.getAsistencias(), m.getDatos());
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
        JuegoContexto c = contexto(foto, j, juego, List.of(), MuestrasPorNivel.NINGUNA, RondasEquipo.NINGUNA);
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
        Set<Long> analizadas = analizadas(j);
        List<PartidaVista> items = filas.stream()
                .skip(Math.max(0, offset))
                .limit(Math.clamp(limite, 1, 100))
                .map(f -> partidaVista(foto, j, f, analizadas))
                .toList();
        return new PaginaPartidas(items, filas.size());
    }

    /** Las partidas de un jugador con la demo analizada (P12). */
    private Set<Long> analizadas(Jugador j) {
        return Set.copyOf(rondas.partidasAnalizadas(j));
    }

    /**
     * Una partida con los compañeros del equipo que estaban y si su demo está analizada (sin comentario del Duende: lo
     * pone DuendeServicio).
     */
    private static PartidaVista partidaVista(Instantanea foto, Jugador j, FilaParticipacion f, Set<Long> analizadas) {
        return new PartidaVista(
                f.partidaId(), f.juego(), f.jugadaEn(), f.modo(), f.gano(), f.kills(), f.muertes(), f.asistencias(),
                f.datos(),
                foto.presencias(f.juego()).getOrDefault(f.partidaId(), List.of()).stream()
                        .filter(p -> !p.slug().equals(j.getSlug()))
                        .map(Presencia::nombre)
                        .toList(),
                analizadas.contains(f.partidaId()),
                null);
    }

    /**
     * Lo especial de cada una de esas partidas de un jugador (P10), por id. Con todas sus partidas aunque la página
     * enseñe un periodo: el informe de una partida no depende de lo que se esté viendo.
     */
    public Map<Long, List<Hecho>> hechos(String slug, Collection<Long> partidaIds) {
        Instantanea foto = instantanea();
        Jugador j = foto.porSlug(slug);
        Set<Long> ids = Set.copyOf(partidaIds);
        Map<Long, List<Hecho>> hechos = new HashMap<>();
        for (Juego juego : Juego.values()) {
            List<FilaParticipacion> filas = foto.filas(j, juego);
            filas.stream()
                    .filter(f -> ids.contains(f.partidaId()))
                    .forEach(f -> hechos.put(f.partidaId(), Informes.hechos(juego, f, filas)));
        }
        return hechos;
    }

    /** Una novedad con lo que tiene de especial, para que el Duende la cuente. */
    public record NovedadConHechos(Novedad novedad, List<Hecho> hechos) {}

    /** Las novedades de (desde, hasta]; {@code hasta} es hasta dónde se ha llegado (antes, si había demasiadas). */
    public record LoteNovedades(Instant desde, Instant hasta, List<NovedadConHechos> items) {}

    /**
     * Lo guardado entre {@code desde} (sin incluir) y {@code hasta}, una novedad por jugador y partida, la primera
     * guardada primero (P11). Como mucho unas {@value #MAX_NOVEDADES}, sin partir las guardadas en el mismo instante.
     */
    public LoteNovedades novedades(Instant desde, Instant hasta) {
        List<Participacion> guardadas = participaciones.findGuardadasEntre(desde, hasta);
        if (guardadas.size() > MAX_NOVEDADES) {
            Instant corte = guardadas.get(MAX_NOVEDADES - 1).getPartida().getGuardadaEn();
            guardadas = guardadas.stream().filter(p -> !p.getPartida().getGuardadaEn().isAfter(corte)).toList();
            hasta = corte;
        }
        if (guardadas.isEmpty()) {
            return new LoteNovedades(desde, hasta, List.of());
        }
        Instantanea foto = instantanea();
        List<NovedadConHechos> items = guardadas.stream()
                .map(p -> {
                    Jugador j = p.getJugador();
                    FilaParticipacion f = FilaParticipacion.de(p);
                    // Una novedad acaba de guardarse: su demo se analiza después.
                    Novedad novedad =
                            new Novedad(new JugadorRef(j.getSlug(), j.getNombre()), partidaVista(foto, j, f, Set.of()));
                    return new NovedadConHechos(novedad, Informes.hechos(f.juego(), f, foto.filas(j, f.juego())));
                })
                .toList();
        return new LoteNovedades(desde, hasta, items);
    }

    /** Los últimos 7 días de cada juego con partidas: cada jugador, el mejor y el peor (P11). */
    public List<SemanaJuego> semana(Instant ahora) {
        Instantanea foto = instantanea().desde(ahora.minus(SEMANA));
        List<SemanaJuego> juegos = new ArrayList<>();
        for (Juego juego : Juego.values()) {
            List<FilaSemana> filas = foto.jugadores().stream()
                    .filter(j -> !foto.filas(j, juego).isEmpty())
                    .map(j -> {
                        ResumenJuego r = Estadisticas.resumir(juego, foto.filas(j, juego));
                        return new FilaSemana(j.getSlug(), j.getNombre(), r.partidas(), r.victorias(), r.winrate(), r.kd());
                    })
                    .toList();
            if (!filas.isEmpty()) {
                juegos.add(Informes.semana(juego, foto.presencias(juego).size(), filas));
            }
        }
        return juegos;
    }

    /**
     * Las partidas de un jugador en un juego que pasan el filtro: su resumen, la media del resto del equipo con el
     * mismo filtro y las {@code limite} más recientes (P9). Sin ninguna, el resumen va con 0 partidas.
     */
    public ConsultaPartidas consulta(String slug, Juego juego, FiltroPartidas filtro, int limite) {
        Instantanea foto = instantanea();
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, juego);
        List<FilaParticipacion> filas = filtro.aplicar(juego, foto.filas(j, juego), zona);
        List<ResumenJuego> otros = foto.jugadores().stream()
                .filter(o -> !o.getId().equals(j.getId()))
                .map(o -> filtro.aplicar(juego, foto.filas(o, juego), zona))
                .filter(f -> !f.isEmpty())
                .map(f -> Estadisticas.resumir(juego, f))
                .toList();
        Set<Long> analizadas = analizadas(j);
        return new ConsultaPartidas(
                juego,
                Estadisticas.resumir(juego, filas),
                Estadisticas.mediasEquipo(otros),
                filas.stream().limit(Math.clamp(limite, 1, 20)).map(f -> partidaVista(foto, j, f, analizadas)).toList());
    }

    // ─── Demos (P12) ────────────────────────────────────────────────────────

    /**
     * Lo de sus demos analizadas del periodo, con la media del resto del equipo en ese periodo. Sin partidas
     * analizadas, todo a 0 y vacío; el 404 es solo para quien no ha jugado nunca a CS2.
     */
    public ResumenDemos demos(String slug, Periodo periodo) {
        Instant inicio = periodo.inicio(Instant.now());
        Instantanea foto = instantanea().desde(inicio);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, Juego.CS2);
        ResumenDemos resumen = rondas(inicio).resumen(foto, j);
        return resumen != null ? resumen : Rondas.resumen(List.of(), List.of());
    }

    /**
     * Mapa de calor de sus muertes en un mapa (sin {@code mapa}, en el que más rondas tiene analizadas), en el periodo.
     * El fondo son las muertes de todos en ese mapa, sin decir quién.
     */
    public CalorMapa calor(String slug, String mapa, Periodo periodo) {
        Instant inicio = periodo.inicio(Instant.now());
        Instantanea foto = instantanea().desde(inicio);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, Juego.CS2);
        List<FilaRonda> suyas = rondas(inicio).de(j);
        List<String> mapas = Rondas.mapasJugados(suyas);
        String elegido = mapa != null && !mapa.isBlank()
                ? mapas.stream().filter(m -> m.equalsIgnoreCase(mapa.trim())).findFirst().orElse(mapa.trim())
                : mapas.stream().findFirst().orElse(null);
        if (elegido == null) {
            return new CalorMapa(null, mapas, List.of(), List.of(), List.of());
        }
        List<Rondas.PuntoFondo> fondo = muertesMapa.findByMapa(elegido).stream()
                .map(m -> new Rondas.PuntoFondo(m.getX(), m.getY(), Rondas.nombreZona(m.getZona())))
                .toList();
        return Rondas.calor(elegido, mapas, suyas, fondo);
    }

    /** Lo que hizo en cada ronda de una partida analizada. 404 si no la jugó o no está analizada. */
    public RondasPartida rondasPartida(String slug, long partidaId) {
        Jugador j = jugadores.findBySlug(slug)
                .orElseThrow(() -> new NoEncontradoException("No hay ningún jugador con el slug \"" + slug + "\"."));
        List<FilaRonda> filas = rondas.findDePartida(partidaId, j).stream().map(FilaRonda::de).toList();
        if (filas.isEmpty()) {
            throw new NoEncontradoException("La demo de esa partida no está analizada.");
        }
        return new RondasPartida(partidaId, filas.getFirst().mapa(), Rondas.metricas(filas), filas);
    }

    /** Hoy en la zona del equipo ("2026-10-10"), para que el chat sepa a qué días se refiere "ayer" o "este mes". */
    public String hoy() {
        return LocalDate.now(zona).toString();
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
        Instant inicio = periodo.inicio(Instant.now());
        Instantanea foto = instantanea().desde(inicio);
        Jugador j = foto.porSlug(slug);
        foto.exigirPartidas(j, juego);
        RondasEquipo rondasPeriodo = juego == Juego.CS2 ? rondas(inicio) : RondasEquipo.NINGUNA;
        return peticion(foto, j, juego, lang, muestras(foto), rondasPeriodo);
    }

    private PeticionInsights peticion(
            Instantanea foto, Jugador j, Juego juego, String lang, MuestrasPorNivel muestras, RondasEquipo rondas) {
        JuegoContexto c = contexto(foto, j, juego, List.of(), muestras, rondas);
        return new PeticionInsights(lang, new JugadorRef(j.getSlug(), j.getNombre()), juego, c.rol(), c.resumen(),
                c.reciente(), c.equipo(), c.desglose(), c.sinergias(), c.sesiones(), c.seguimiento(), c.nivel(),
                c.demos());
    }

    /** Peticiones de recomendaciones de todo el equipo (una por jugador y juego), para las tarjetas. */
    public List<PeticionInsights> peticionesEquipo(Juego soloJuego, String lang) {
        Instantanea foto = instantanea();
        MuestrasPorNivel muestras = muestras(foto);
        RondasEquipo todas = soloJuego == null || soloJuego == Juego.CS2 ? rondas(null) : RondasEquipo.NINGUNA;
        List<PeticionInsights> lista = new ArrayList<>();
        for (Jugador j : foto.jugadores()) {
            for (Juego juego : Juego.values()) {
                if ((soloJuego == null || soloJuego == juego) && !foto.filas(j, juego).isEmpty()) {
                    lista.add(peticion(foto, j, juego, lang, muestras, todas));
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
        MuestrasPorNivel muestras = muestras(foto);
        RondasEquipo todas = rondas(null);
        Instant ahora = Instant.now();
        Map<Periodo, Instantanea> recortes = new EnumMap<>(Periodo.class);
        Periodo.RECORTADOS.forEach(p -> recortes.put(p, foto.desde(p.inicio(ahora))));
        return foto.jugadores().stream()
                .map(j -> new JugadorContexto(
                        j.getSlug(),
                        j.getNombre(),
                        Arrays.stream(Juego.values())
                                .filter(juego -> !foto.filas(j, juego).isEmpty())
                                .map(juego -> contexto(foto, j, juego, periodos(recortes, j, juego), muestras, todas))
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
