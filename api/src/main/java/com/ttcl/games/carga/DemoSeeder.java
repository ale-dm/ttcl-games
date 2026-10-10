package com.ttcl.games.carga;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.ConsejoDado;
import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Muestra;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Partida;
import com.ttcl.games.dominio.Repositorios.ConsejoDadoRepo;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.DemoRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.MuerteMapaRepo;
import com.ttcl.games.dominio.Repositorios.MuestraRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.PartidaRepo;
import com.ttcl.games.dominio.Repositorios.RondaRepo;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Estadisticas;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos de ejemplo para ver la web sin claves de FACEIT ni de Hi-Rez. Solo actúa con {@code ttcl.demo=true} y la
 * base vacía. Es determinista (semilla fija; las horas, en la zona del equipo) y cada jugador tiene un perfil con algo
 * que mejorar y algo que hace bien, para que el Duende tenga de qué hablar. Se juega por sesiones: el mismo grupo,
 * varias partidas seguidas. Todas las partidas de CS2 tienen su demo analizada (P12, {@link RondasDemo}).
 */
@Component
@Order(2)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final Period PERIODO = Period.ofDays(45);
    /** Hace cuántos días se le avisó a Jugador 4 de que moría demasiado (P6). */
    private static final int DIAS_CONSEJO_J4 = 25;
    private static final List<String> MAPAS =
            List.of("de_mirage", "de_inferno", "de_nuke", "de_ancient", "de_anubis", "de_dust2", "de_train");
    private static final List<String> COLAS = List.of("Conquest", "Conquest", "Conquest", "Arena", "Joust");

    /** Por ronda: kills, muertes, asistencias. Por partida: ADR, HS%, entradas, clutches, utilidad. */
    record PerfilCs2(
            double kpr, double dpr, double apr, double adr, double hs, double entradas, double exitoEntrada,
            double clutches, double exitoClutch, double utilidad, double ganar, String mapaMalo, String mapaBueno) {}

    /** Por partida: kills, muertes, asistencias. Por minuto: daño y oro. */
    record PerfilSmite(
            double kills, double muertes, double asist, double danoMin, double oroMin, double mitigado,
            double curacion, double ganar, List<String> dioses, String diosMalo) {}

    /** Nivel y ELO de FACEIT (P8). */
    record NivelDemo(int nivel, int elo) {}

    record Demo(
            String slug, String nombre, String nickCs2, String rolCs2, PerfilCs2 cs2, String nickSmite,
            String rolSmite, PerfilSmite smite, NivelDemo faceit) {}

    private static final List<Demo> EQUIPO = List.of(
            // Rifler que mata mucho y gana poco; buena puntería; Nuke se le atraganta. En SMITE juega magos (mid).
            // Nivel 7 de FACEIT.
            new Demo("j1", "Jugador 1",
                    "demo_uno", "rifler",
                    new PerfilCs2(0.84, 0.66, 0.12, 88, 53, 3.0, 0.55, 1.4, 0.28, 110, 0.22, "de_nuke", null),
                    "DemoUno", "mid", new PerfilSmite(7.5, 4.2, 8, 930, 540, 11000, 1500, 0.6,
                            List.of("Zeus", "Ra", "Agni", "Poseidon"), null), new NivelDemo(7, 1438)),
            // Soporte: poca kill, muchas asistencias y mucha utilidad; en SMITE es el guardián. Con su rol, el
            // Duende no le regaña por las kills. Nivel 6.
            new Demo("j2", "Jugador 2",
                    "demo_dos", "soporte",
                    new PerfilCs2(0.6, 0.66, 0.24, 69, 44, 1.2, 0.47, 1.1, 0.22, 215, 0.5, null, "de_mirage"),
                    "DemoDos", "guardian", new PerfilSmite(3.5, 3.6, 15, 610, 470, 26000, 5200, 0.56,
                            List.of("Ymir", "Athena", "Geb", "Khepri"), null), new NivelDemo(6, 1287)),
            // Entry que abre mucho pero con poco éxito y poca cabeza; buenos clutches; viene mejorando. Se tiltea: a
            // partir de la 3ª partida seguida gana mucho menos. Nivel 5.
            new Demo("j3", "Jugador 3",
                    "demo_tres", "entry",
                    new PerfilCs2(0.72, 0.64, 0.14, 78, 34, 4.6, 0.36, 1.3, 0.42, 85, 0.57, null, "de_ancient"),
                    null, null, null, new NivelDemo(5, 1164)),
            // Solo SMITE, de jungla con asesinos: muere demasiado y farmea poco. Por la tarde rinde mucho más. Desde
            // que el Duende le avisó de las muertes, muere todavía más.
            new Demo("j4", "Jugador 4", null, null, null,
                    "DemoCuatro", "jungla", new PerfilSmite(5.5, 7.6, 6, 720, 395, 9000, 700, 0.44,
                            List.of("Loki", "Thanatos", "Fenrir", "Susano"), "Loki"), null));

    /** Niveles de los que se generan partidas de otros jugadores (P8), y cuántas de cada uno. */
    private static final int NIVEL_MIN_MUESTRAS = 4;
    private static final int NIVEL_MAX_MUESTRAS = 8;
    private static final int MUESTRAS_POR_NIVEL = 180;

    private final TtclProperties props;
    private final JugadorRepo jugadores;
    private final CuentaRepo cuentas;
    private final PartidaRepo partidas;
    private final ParticipacionRepo participaciones;
    private final ConsejoDadoRepo consejos;
    private final MuestraRepo muestras;
    private final RondaRepo rondas;
    private final MuerteMapaRepo muertesMapa;
    private final DemoRepo demos;
    private final Random rnd = new Random(2026);

    public DemoSeeder(
            TtclProperties props,
            JugadorRepo jugadores,
            CuentaRepo cuentas,
            PartidaRepo partidas,
            ParticipacionRepo participaciones,
            ConsejoDadoRepo consejos,
            MuestraRepo muestras,
            RondaRepo rondas,
            MuerteMapaRepo muertesMapa,
            DemoRepo demos) {
        this.props = props;
        this.jugadores = jugadores;
        this.cuentas = cuentas;
        this.partidas = partidas;
        this.participaciones = participaciones;
        this.consejos = consejos;
        this.muestras = muestras;
        this.rondas = rondas;
        this.muertesMapa = muertesMapa;
        this.demos = demos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.demo() || jugadores.count() > 0) {
            return;
        }
        Instant ahora = Instant.now();
        Map<String, Jugador> porSlug = new LinkedHashMap<>();
        for (Demo d : EQUIPO) {
            Jugador j = jugadores.save(new Jugador(d.slug(), d.nombre(), true));
            porSlug.put(d.slug(), j);
            if (d.nickCs2() != null) {
                cuenta(j, Juego.CS2, d.nickCs2(), d.rolCs2(), d.faceit(), ahora);
            }
            if (d.nickSmite() != null) {
                cuenta(j, Juego.SMITE2, d.nickSmite(), d.rolSmite(), null, ahora);
            }
        }
        LocalDate hoy = ahora.atZone(props.zona()).toLocalDate();
        int cs2 = generarCs2(porSlug, hoy);
        int smite = generarSmite(porSlug, hoy);
        // Consejos de hace unos días (P6): a Jugador 3 se le avisó del ADR y ha mejorado mucho desde entonces; a
        // Jugador 4, de que moría demasiado, y desde entonces muere aún más.
        apuntarConsejo(porSlug.get("j3"), Juego.CS2, "debil_adr", "adr", ahora.minus(Duration.ofDays(12)));
        apuntarConsejo(porSlug.get("j4"), Juego.SMITE2, "debil_muertes_media", "muertes_media",
                ahora.minus(Duration.ofDays(DIAS_CONSEJO_J4)));
        int otros = generarMuestras(hoy);
        // Las rondas de cada demo (P12), con su propio generador: al final, para no mover ningún otro número.
        int analizadas = new RondasDemo(rondas, muertesMapa, demos).generar(participaciones.findAllCompletas());
        log.info("Datos de ejemplo creados: {} jugadores, {} partidas de CS2 ({} con la demo analizada) y {} de "
                + "SMITE 2, y {} partidas de otros jugadores de FACEIT", EQUIPO.size(), cs2, analizadas, smite, otros);
    }

    private void cuenta(Jugador j, Juego juego, String nick, String rol, NivelDemo faceit, Instant ahora) {
        Cuenta c = new Cuenta(j, juego, nick);
        c.setRol(rol);
        if (faceit != null) {
            c.setNivel(faceit.nivel(), faceit.elo());
        }
        c.setExternalId("demo-" + j.getSlug() + "-" + juego.codigo());
        c.setUltimaSync(ahora.minus(Duration.ofMinutes(12)));
        cuentas.save(c);
    }

    // ─── Sesiones ───────────────────────────────────────────────────────────

    /**
     * Unas cuantas partidas seguidas del mismo grupo.
     *
     * @param resultados resultado de cada partida, o null para sortearlos
     */
    record SesionDemo(List<Demo> grupo, int partidas, LocalDateTime inicio, List<Boolean> resultados) {}

    /**
     * Cuándo empieza cada sesión: probabilidad de que sea por la mañana, por la tarde y por la noche; el resto, de
     * madrugada.
     */
    record Horario(double manana, double tarde, double noche) {}

    /** Al CS2 se juega sobre todo por la noche; al SMITE 2, también mucho por la tarde. */
    private static final Horario HORARIO_CS2 = new Horario(0.1, 0.3, 0.45);
    private static final Horario HORARIO_SMITE = new Horario(0.1, 0.45, 0.35);

    /**
     * Sesiones repartidas por el periodo, una cada uno o dos días, hasta llegar a {@code minimo} partidas. Deja libres
     * los últimos días para las rachas del final.
     */
    private List<SesionDemo> plan(List<Demo> candidatos, int minimo, LocalDate hoy, Horario horario) {
        List<SesionDemo> sesiones = new ArrayList<>();
        LocalDate dia = hoy.minus(PERIODO);
        int partidasPlan = 0;
        while (partidasPlan < minimo && dia.isBefore(hoy.minusDays(5))) {
            double r = rnd.nextDouble();
            int largo = r < 0.15 ? 1 : r < 0.4 ? 2 : r < 0.7 ? 3 : r < 0.9 ? 4 : 5;
            sesiones.add(new SesionDemo(grupo(candidatos), largo, hora(dia, horario), null));
            partidasPlan += largo;
            dia = dia.plusDays(rnd.nextDouble() < 0.4 ? 2 : 1);
        }
        return sesiones;
    }

    /** Hora de empezar: mañana de 10 a 13 h, tarde de 16 a 19 h, noche de 20 a 23 h, madrugada de 0 a 2 h. */
    private LocalDateTime hora(LocalDate dia, Horario horario) {
        double r = rnd.nextDouble();
        int minuto = rnd.nextInt(60);
        if (r < horario.manana()) {
            return dia.atTime(10 + rnd.nextInt(3), minuto);
        }
        if (r < horario.manana() + horario.tarde()) {
            return dia.atTime(16 + rnd.nextInt(3), minuto);
        }
        if (r < horario.manana() + horario.tarde() + horario.noche()) {
            return dia.atTime(20 + rnd.nextInt(3), minuto);
        }
        return dia.plusDays(1).atTime(rnd.nextInt(2), minuto);
    }

    /** Pausa entre dos partidas de la misma sesión: de 2 a 15 minutos. */
    private int pausa() {
        return 120 + rnd.nextInt(780);
    }

    // ─── CS2 ────────────────────────────────────────────────────────────────

    private int generarCs2(Map<String, Jugador> porSlug, LocalDate hoy) {
        List<Demo> jugadoresCs2 = EQUIPO.stream().filter(d -> d.cs2() != null).toList();
        List<SesionDemo> sesiones = new ArrayList<>(plan(jugadoresCs2, 75, hoy, HORARIO_CS2));
        // Al final, Jugador 3 en racha con Jugador 2, en sesiones cortas...
        List<Demo> dos = List.of(jugadoresCs2.get(1), jugadoresCs2.get(2));
        sesiones.add(new SesionDemo(dos, 2, hoy.minusDays(4).atTime(21, 10), List.of(true, true)));
        sesiones.add(new SesionDemo(dos, 2, hoy.minusDays(3).atTime(20, 40), List.of(true, true)));
        sesiones.add(new SesionDemo(dos, 1, hoy.minusDays(2).atTime(21, 30), List.of(true)));
        // ...y, lo último, Jugador 1 en solitario y casi todas perdidas: racha negra.
        sesiones.add(new SesionDemo(List.of(jugadoresCs2.get(0)), 5, hoy.minusDays(1).atTime(17, 5),
                List.of(false, false, true, false, false)));
        int total = sesiones.stream().mapToInt(SesionDemo::partidas).sum();
        int i = 0;
        for (SesionDemo sesion : sesiones) {
            Instant inicio = sesion.inicio().atZone(props.zona()).toInstant();
            for (int n = 1; n <= sesion.partidas(); n++, i++) {
                List<Demo> grupo = sesion.grupo();
                // Jugador 3 se tiltea: a partir de la 3ª partida seguida se le va la cabeza.
                double tilt = n >= 3 ? -0.45 : 0;
                String mapa = MAPAS.get(rnd.nextInt(MAPAS.size()));
                double prob = grupo.stream().mapToDouble(d -> d.cs2().ganar()
                        + (mapa.equals(d.cs2().mapaMalo()) ? -0.3 : 0)
                        + (mapa.equals(d.cs2().mapaBueno()) ? 0.2 : 0)
                        + (d.slug().equals("j3") ? tilt : 0)).average().orElse(0.5);
                boolean gano = sesion.resultados() != null ? sesion.resultados().get(n - 1) : rnd.nextDouble() < prob;
                int rondasRival = gano ? 4 + rnd.nextInt(9) : 13;
                int rondasNuestras = gano ? 13 : 3 + rnd.nextInt(10);
                int rondas = rondasNuestras + rondasRival;
                int duracion = rondas * 105 + rnd.nextInt(240);

                Partida partida = guardarPartida(
                        new Partida(Juego.CS2, "demo-cs2-%04d".formatted(i), inicio, duracion, mapa));
                inicio = inicio.plusSeconds(duracion + pausa());
                for (Demo d : grupo) {
                    PerfilCs2 p = d.cs2();
                    // Jugador 3 ha mejorado en las últimas partidas.
                    double mejora = d.slug().equals("j3") && i >= total - 18 ? 1.3 : 1.0;
                    double efecto = gano ? 1.08 : 0.92;
                    int kills = positivo(normal(p.kpr() * rondas * efecto * mejora, 2.8));
                    int muertes = Math.min(rondas, positivo(normal(p.dpr() * rondas / efecto / mejora, 2.4)));
                    int asist = positivo(normal(p.apr() * rondas, 1.6));
                    int entradas = positivo(normal(p.entradas(), 1.2));
                    int clutches = positivo(normal(p.clutches(), 0.9));
                    Map<String, Object> datos = new LinkedHashMap<>();
                    datos.put("mapa", mapa);
                    datos.put("marcador", rondasNuestras + " / " + rondasRival);
                    datos.put("rondas", rondas);
                    datos.put("adr", redondear(Math.max(20, normal(p.adr() * efecto * mejora, 11)), 1));
                    datos.put("hs_pct", redondear(Math.clamp(normal(p.hs(), 8), 5, 95), 1));
                    datos.put("kr", redondear((double) kills / rondas, 2));
                    datos.put("mvps", positivo(normal(kills / 6.0, 1)));
                    datos.put("multikills", positivo(normal(kills / 9.0, 0.8)));
                    datos.put("entry_intentos", entradas);
                    datos.put("entry_ganados", binomial(entradas, p.exitoEntrada()));
                    datos.put("clutch_intentos", clutches);
                    datos.put("clutch_ganados", binomial(clutches, p.exitoClutch()));
                    datos.put("dano_utilidad", positivo(normal(p.utilidad(), 35)));
                    guardar(partida, porSlug.get(d.slug()), gano, kills, muertes, asist, datos);
                }
            }
        }
        return total;
    }

    // ─── SMITE 2 ────────────────────────────────────────────────────────────

    private int generarSmite(Map<String, Jugador> porSlug, LocalDate hoy) {
        List<Demo> jugadoresSmite = EQUIPO.stream().filter(d -> d.smite() != null).toList();
        Demo cuatro = jugadoresSmite.stream().filter(d -> d.slug().equals("j4")).findFirst().orElseThrow();
        List<SesionDemo> sesiones = new ArrayList<>(plan(jugadoresSmite, 65, hoy, HORARIO_SMITE));
        // Lo último, Jugador 4 en solitario y casi todas perdidas.
        sesiones.add(new SesionDemo(List.of(cuatro), 5, hoy.minusDays(1).atTime(19, 20),
                List.of(false, true, false, false, false)));
        int total = sesiones.stream().mapToInt(SesionDemo::partidas).sum();
        int i = 0;
        for (SesionDemo sesion : sesiones) {
            Instant inicio = sesion.inicio().atZone(props.zona()).toInstant();
            for (int n = 1; n <= sesion.partidas(); n++, i++) {
                List<Demo> grupo = sesion.grupo();
                // Jugador 4 rinde mucho más por la tarde que el resto del día.
                boolean tarde = Estadisticas.franja(inicio.atZone(props.zona()).getHour()).equals("tarde");
                Map<String, String> dios = new LinkedHashMap<>();
                for (Demo d : grupo) {
                    dios.put(d.slug(), d.smite().dioses().get(rnd.nextInt(d.smite().dioses().size())));
                }
                double prob = grupo.stream().mapToDouble(d -> d.smite().ganar()
                        + (dios.get(d.slug()).equals(d.smite().diosMalo()) ? -0.3 : 0)
                        + (tarde && d == cuatro ? 0.4 : 0)).average().orElse(0.5);
                boolean gano = sesion.resultados() != null ? sesion.resultados().get(n - 1) : rnd.nextDouble() < prob;
                String cola = COLAS.get(rnd.nextInt(COLAS.size()));
                double minutos = redondear(
                        cola.equals("Conquest") ? 24 + rnd.nextDouble() * 14 : 14 + rnd.nextDouble() * 8, 1);

                Partida partida = guardarPartida(new Partida(Juego.SMITE2, "demo-smite2-%04d".formatted(i), inicio,
                        (int) (minutos * 60), cola));
                inicio = inicio.plusSeconds((long) (minutos * 60) + pausa());
                // Desde que el Duende le avisó, Jugador 4 muere todavía más.
                boolean trasElAviso = sesion.inicio().toLocalDate().isAfter(hoy.minusDays(DIAS_CONSEJO_J4));
                for (Demo d : grupo) {
                    PerfilSmite p = d.smite();
                    double efecto = gano ? 1.1 : 0.9;
                    double escala = minutos / 30;
                    double muere = d == cuatro && trasElAviso ? 1.25 : 1.0;
                    Map<String, Object> datos = new LinkedHashMap<>();
                    datos.put("dios", dios.get(d.slug()));
                    datos.put("dano", (int) Math.max(1000, normal(p.danoMin() * minutos * efecto, p.danoMin() * 3)));
                    datos.put("mitigado", positivo(normal(p.mitigado() * escala, p.mitigado() * 0.15)));
                    datos.put("curacion", positivo(normal(p.curacion() * escala, p.curacion() * 0.2)));
                    datos.put("oro", (int) Math.max(1000, normal(p.oroMin() * minutos * efecto, 400)));
                    datos.put("minutos", minutos);
                    guardar(partida, porSlug.get(d.slug()), gano,
                            positivo(normal(p.kills() * escala * efecto, 2)),
                            positivo(normal(p.muertes() * escala / efecto * muere, 1.6)),
                            positivo(normal(p.asist() * escala * efecto, 2.5)),
                            datos);
                }
            }
        }
        return total;
    }

    // ─── Otros jugadores de FACEIT (P8) ─────────────────────────────────────

    /**
     * Partidas de jugadores que no son del equipo, de cada nivel: lo que se guardaría al sincronizar con FACEIT. Con
     * su propio generador, para no cambiar ni un número de lo demás. A más nivel, más kills, menos muertes, más daño,
     * más cabeza y más utilidad.
     */
    private int generarMuestras(LocalDate hoy) {
        Random r = new Random(8);
        int total = 0;
        for (int nivel = NIVEL_MIN_MUESTRAS; nivel <= NIVEL_MAX_MUESTRAS; nivel++) {
            int n = nivel - 5;
            for (int i = 0; i < MUESTRAS_POR_NIVEL; i++, total++) {
                boolean gano = r.nextBoolean();
                int rondas = 13 + 3 + r.nextInt(10);
                int kills = positivo((0.63 + 0.035 * n) * rondas + r.nextGaussian() * 3);
                int muertes = Math.min(rondas, positivo((0.7 - 0.02 * n) * rondas + r.nextGaussian() * 2.5));
                int entradas = positivo(2.5 + r.nextGaussian() * 1.3);
                int clutches = positivo(1.2 + r.nextGaussian() * 0.9);
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("rondas", rondas);
                datos.put("adr", redondear(Math.max(20, 72 + 3.5 * n + r.nextGaussian() * 18), 1));
                datos.put("hs_pct", redondear(Math.clamp(43 + 1.2 * n + r.nextGaussian() * 10, 5, 95), 1));
                datos.put("kr", redondear((double) kills / rondas, 2));
                datos.put("entry_intentos", entradas);
                datos.put("entry_ganados", binomial(r, entradas, 0.47 + 0.01 * n));
                datos.put("clutch_intentos", clutches);
                datos.put("clutch_ganados", binomial(r, clutches, 0.24 + 0.01 * n));
                datos.put("dano_utilidad", positivo(85 + 9 * n + r.nextGaussian() * 40));
                muestras.save(new Muestra(Juego.CS2, nivel, MAPAS.get(r.nextInt(MAPAS.size())),
                        hoy.minusDays(r.nextInt(PERIODO.getDays())), gano,
                        kills, muertes, positivo(0.14 * rondas + r.nextGaussian() * 1.6), datos));
            }
        }
        return total;
    }

    // ─── Consejos ───────────────────────────────────────────────────────────

    /** Apunta un consejo como si el Duende lo hubiera dado ese día, con el valor que tenía la métrica entonces. */
    private void apuntarConsejo(Jugador jugador, Juego juego, String insight, String metrica, Instant dadoEn) {
        List<FilaParticipacion> antes = participaciones.findAllCompletas().stream()
                .filter(p -> p.getJugador().getId().equals(jugador.getId()) && p.getJuego() == juego)
                .map(FilaParticipacion::de)
                .filter(f -> f.jugadaEn().isBefore(dadoEn))
                .toList();
        Double valor = Estadisticas.valor(Estadisticas.resumir(juego, antes), metrica);
        consejos.save(new ConsejoDado(jugador, juego, insight, metrica, valor, "alto", dadoEn));
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    /** Quién juega cada sesión: a veces el grupo entero, a veces dos, a veces uno solo. */
    private List<Demo> grupo(List<Demo> candidatos) {
        double r = rnd.nextDouble();
        if (r < 0.45 || candidatos.size() == 1) {
            return candidatos;
        }
        List<Demo> mezcla = new ArrayList<>(candidatos);
        Collections.shuffle(mezcla, rnd);
        return mezcla.subList(0, r < 0.8 ? Math.min(2, mezcla.size()) : 1);
    }

    /** Como si se hubiera sincronizado al acabar (P11: así las novedades de ejemplo son las partidas del último día). */
    private Partida guardarPartida(Partida partida) {
        partida.setGuardadaEn(partida.getJugadaEn().plusSeconds(partida.getDuracionSeg()));
        return partidas.save(partida);
    }

    private void guardar(Partida partida, Jugador jugador, boolean gano, int kills, int muertes, int asist,
            Map<String, Object> datos) {
        Participacion p = new Participacion(partida, jugador);
        p.actualizar(gano, kills, muertes, asist, datos);
        participaciones.save(p);
    }

    private double normal(double media, double desviacion) {
        return media + rnd.nextGaussian() * desviacion;
    }

    private static int positivo(double valor) {
        return (int) Math.max(0, Math.round(valor));
    }

    private int binomial(int intentos, double exito) {
        return binomial(rnd, intentos, exito);
    }

    private static int binomial(Random r, int intentos, double exito) {
        int ganados = 0;
        for (int i = 0; i < intentos; i++) {
            if (r.nextDouble() < exito) {
                ganados++;
            }
        }
        return ganados;
    }

    private static double redondear(double valor, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(valor * f) / f;
    }
}
