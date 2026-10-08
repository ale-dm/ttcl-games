package com.ttcl.games.carga;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Partida;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.PartidaRepo;
import com.ttcl.games.juego.Juego;
import java.time.Duration;
import java.time.Instant;
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
 * base vacía. Es determinista (semilla fija) y cada jugador tiene un perfil con algo que mejorar y algo que hace
 * bien, para que el Duende tenga de qué hablar.
 */
@Component
@Order(2)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final Duration PERIODO = Duration.ofDays(45);
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

    record Demo(String slug, String nombre, String nickCs2, PerfilCs2 cs2, String nickSmite, PerfilSmite smite) {}

    private static final List<Demo> EQUIPO = List.of(
            // Mata mucho y gana poco; buena puntería; Nuke se le atraganta.
            new Demo("j1", "Jugador 1",
                    "demo_uno", new PerfilCs2(0.84, 0.66, 0.12, 88, 53, 3.0, 0.55, 1.4, 0.28, 110, 0.22, "de_nuke", null),
                    "DemoUno", new PerfilSmite(7.5, 4.2, 8, 930, 540, 11000, 1500, 0.6,
                            List.of("Zeus", "Ra", "Agni", "Poseidon"), null)),
            // Poca kill, muchas asistencias y mucha utilidad; en SMITE es el guardián.
            new Demo("j2", "Jugador 2",
                    "demo_dos", new PerfilCs2(0.6, 0.66, 0.24, 69, 44, 1.2, 0.47, 1.1, 0.22, 215, 0.5, null, "de_mirage"),
                    "DemoDos", new PerfilSmite(3.5, 3.6, 15, 610, 470, 26000, 5200, 0.56,
                            List.of("Ymir", "Athena", "Geb", "Khepri"), null)),
            // Entry que abre mucho pero con poco éxito y poca cabeza; buenos clutches; viene mejorando.
            new Demo("j3", "Jugador 3",
                    "demo_tres", new PerfilCs2(0.72, 0.64, 0.14, 78, 34, 4.6, 0.36, 1.3, 0.42, 85, 0.57, null, "de_ancient"),
                    null, null),
            // Solo SMITE: muere demasiado y farmea poco.
            new Demo("j4", "Jugador 4", null, null,
                    "DemoCuatro", new PerfilSmite(5.5, 7.6, 6, 720, 395, 9000, 700, 0.44,
                            List.of("Loki", "Thanatos", "Fenrir", "Susano"), "Loki")));

    private final TtclProperties props;
    private final JugadorRepo jugadores;
    private final CuentaRepo cuentas;
    private final PartidaRepo partidas;
    private final ParticipacionRepo participaciones;
    private final Random rnd = new Random(2026);

    public DemoSeeder(
            TtclProperties props,
            JugadorRepo jugadores,
            CuentaRepo cuentas,
            PartidaRepo partidas,
            ParticipacionRepo participaciones) {
        this.props = props;
        this.jugadores = jugadores;
        this.cuentas = cuentas;
        this.partidas = partidas;
        this.participaciones = participaciones;
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
                cuenta(j, Juego.CS2, d.nickCs2(), ahora);
            }
            if (d.nickSmite() != null) {
                cuenta(j, Juego.SMITE2, d.nickSmite(), ahora);
            }
        }
        int cs2 = generarCs2(porSlug, ahora);
        int smite = generarSmite(porSlug, ahora);
        log.info("Datos de ejemplo creados: {} jugadores, {} partidas de CS2 y {} de SMITE 2", EQUIPO.size(), cs2, smite);
    }

    private void cuenta(Jugador j, Juego juego, String nick, Instant ahora) {
        Cuenta c = new Cuenta(j, juego, nick);
        c.setExternalId("demo-" + j.getSlug() + "-" + juego.codigo());
        c.setUltimaSync(ahora.minus(Duration.ofMinutes(12)));
        cuentas.save(c);
    }

    // ─── CS2 ────────────────────────────────────────────────────────────────

    private int generarCs2(Map<String, Jugador> porSlug, Instant ahora) {
        List<Demo> jugadoresCs2 = EQUIPO.stream().filter(d -> d.cs2() != null).toList();
        int total = 62;
        for (int i = 0; i < total; i++) {
            List<Demo> grupo;
            Boolean forzado = null;
            if (i >= total - 5) {
                // Las últimas, Jugador 1 en solitario y casi todas perdidas: racha negra.
                grupo = List.of(jugadoresCs2.get(0));
                forzado = i != total - 3 ? Boolean.FALSE : Boolean.TRUE;
            } else if (i >= total - 10) {
                // Antes, Jugador 3 en racha con Jugador 2.
                grupo = List.of(jugadoresCs2.get(1), jugadoresCs2.get(2));
                forzado = Boolean.TRUE;
            } else {
                grupo = grupo(jugadoresCs2);
            }
            String mapa = MAPAS.get(rnd.nextInt(MAPAS.size()));
            double prob = grupo.stream().mapToDouble(d -> d.cs2().ganar()
                    + (mapa.equals(d.cs2().mapaMalo()) ? -0.3 : 0)
                    + (mapa.equals(d.cs2().mapaBueno()) ? 0.2 : 0)).average().orElse(0.5);
            boolean gano = forzado != null ? forzado : rnd.nextDouble() < prob;
            int rondasRival = gano ? 4 + rnd.nextInt(9) : 13;
            int rondasNuestras = gano ? 13 : 3 + rnd.nextInt(10);
            int rondas = rondasNuestras + rondasRival;

            Partida partida = partidas.save(new Partida(Juego.CS2, "demo-cs2-%04d".formatted(i),
                    momento(ahora, i, total), (int) (rondas * 105 + rnd.nextInt(240)), mapa));
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
        return total;
    }

    // ─── SMITE 2 ────────────────────────────────────────────────────────────

    private int generarSmite(Map<String, Jugador> porSlug, Instant ahora) {
        List<Demo> jugadoresSmite = EQUIPO.stream().filter(d -> d.smite() != null).toList();
        Demo cuatro = jugadoresSmite.stream().filter(d -> d.slug().equals("j4")).findFirst().orElseThrow();
        int total = 54;
        for (int i = 0; i < total; i++) {
            List<Demo> grupo = i >= total - 5 ? List.of(cuatro) : grupo(jugadoresSmite);
            Map<String, String> dios = new LinkedHashMap<>();
            for (Demo d : grupo) {
                dios.put(d.slug(), d.smite().dioses().get(rnd.nextInt(d.smite().dioses().size())));
            }
            double prob = grupo.stream().mapToDouble(d -> d.smite().ganar()
                    + (dios.get(d.slug()).equals(d.smite().diosMalo()) ? -0.3 : 0)).average().orElse(0.5);
            boolean gano = i >= total - 5 ? i == total - 4 : rnd.nextDouble() < prob;
            String cola = COLAS.get(rnd.nextInt(COLAS.size()));
            double minutos = redondear(cola.equals("Conquest") ? 24 + rnd.nextDouble() * 14 : 14 + rnd.nextDouble() * 8, 1);

            Partida partida = partidas.save(new Partida(Juego.SMITE2, "demo-smite2-%04d".formatted(i),
                    momento(ahora, i, total).minus(Duration.ofMinutes(47)), (int) (minutos * 60), cola));
            for (Demo d : grupo) {
                PerfilSmite p = d.smite();
                double efecto = gano ? 1.1 : 0.9;
                double escala = minutos / 30;
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("dios", dios.get(d.slug()));
                datos.put("dano", (int) Math.max(1000, normal(p.danoMin() * minutos * efecto, p.danoMin() * 3)));
                datos.put("mitigado", positivo(normal(p.mitigado() * escala, p.mitigado() * 0.15)));
                datos.put("curacion", positivo(normal(p.curacion() * escala, p.curacion() * 0.2)));
                datos.put("oro", (int) Math.max(1000, normal(p.oroMin() * minutos * efecto, 400)));
                datos.put("minutos", minutos);
                guardar(partida, porSlug.get(d.slug()), gano,
                        positivo(normal(p.kills() * escala * efecto, 2)),
                        positivo(normal(p.muertes() * escala / efecto, 1.6)),
                        positivo(normal(p.asist() * escala * efecto, 2.5)),
                        datos);
            }
        }
        return total;
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    /** Quién juega cada partida: a veces el grupo entero, a veces dos, a veces uno solo. */
    private List<Demo> grupo(List<Demo> candidatos) {
        double r = rnd.nextDouble();
        if (r < 0.45 || candidatos.size() == 1) {
            return candidatos;
        }
        List<Demo> mezcla = new ArrayList<>(candidatos);
        Collections.shuffle(mezcla, rnd);
        return mezcla.subList(0, r < 0.8 ? Math.min(2, mezcla.size()) : 1);
    }

    /** Fecha de la partida i de n, repartidas en el periodo, la última hace un par de horas. */
    private Instant momento(Instant ahora, int i, int n) {
        Instant inicio = ahora.minus(PERIODO);
        long paso = PERIODO.minusHours(2).toSeconds() / n;
        return inicio.plusSeconds(paso * i + rnd.nextLong(paso / 2));
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
        int ganados = 0;
        for (int i = 0; i < intentos; i++) {
            if (rnd.nextDouble() < exito) {
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
