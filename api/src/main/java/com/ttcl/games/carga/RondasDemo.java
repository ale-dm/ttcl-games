package com.ttcl.games.carga;

import com.ttcl.games.dominio.Demo;
import com.ttcl.games.dominio.MuerteMapa;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Partida;
import com.ttcl.games.dominio.Repositorios.DemoRepo;
import com.ttcl.games.dominio.Repositorios.MuerteMapaRepo;
import com.ttcl.games.dominio.Repositorios.RondaRepo;
import com.ttcl.games.dominio.Ronda;
import com.ttcl.games.juego.Juego;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rondas de ejemplo de las partidas de CS2 (P12): lo que diría el trabajador de análisis de cada demo. Cuadran con cada
 * partida (las kills, muertes y asistencias de cada uno, su ADR, su daño de utilidad y el marcador) y tienen su propio
 * generador, así que no cambian ningún otro número de ejemplo. Las zonas y coordenadas de cada mapa son inventadas:
 * con demos de verdad salen las del juego.
 *
 * <p>Para que el Duende tenga de qué hablar: Jugador 1 rinde mucho menos de T que de CT, Jugador 2 (soporte) da muchas
 * asistencias de flash y le tradean casi siempre, y Jugador 3 (entry) abre muchas rondas, pierde la mayoría de esos
 * duelos y muere una y otra vez en el mismo sitio de cada mapa sin que nadie le tradee.
 */
final class RondasDemo {

    /** Una zona de un mapa: dónde está y cuánto se muere en ella de T y de CT. */
    record Zona(String nombre, double x, double y, double pesoT, double pesoCt) {}

    /**
     * Cómo juega cada uno, ronda a ronda.
     *
     * @param killsT cuánto pesan sus kills en las rondas de T (en las de CT, 2 menos esto)
     * @param muertesT cuánto más muere en las rondas de T
     * @param apertura probabilidad de estar en el primer duelo de la ronda, y de ganarlo
     * @param tradeado probabilidad de que le tradeen al morir
     * @param flash probabilidad de que una asistencia sea de flash
     */
    record Estilo(double killsT, double muertesT, double apertura, double exitoApertura, double tradeado, double flash) {}

    private static final Map<String, Estilo> ESTILOS = Map.of(
            "j1", new Estilo(0.62, 1.45, 0.17, 0.56, 0.3, 0.18),
            "j2", new Estilo(1.0, 1.0, 0.07, 0.48, 0.42, 0.6),
            "j3", new Estilo(1.0, 1.0, 0.36, 0.34, 0.3, 0.12));
    private static final Estilo NORMAL = new Estilo(1.0, 1.0, 0.15, 0.5, 0.3, 0.2);

    /** Donde muere Jugador 3 en cada mapa, casi siempre sin trade. */
    private static final Map<String, String> TRAMPA = Map.of(
            "de_mirage", "TRamp", "de_inferno", "Banana", "de_nuke", "Outside", "de_ancient", "Mid",
            "de_anubis", "Canal", "de_dust2", "LongDoors", "de_train", "Ivy");

    private static final Map<String, List<Zona>> ZONAS = Map.of(
            "de_mirage", List.of(
                    new Zona("TSpawn", 1250, 0, 0.2, 0.3), new Zona("TRamp", 600, -1100, 1.2, 0.6),
                    new Zona("Palace", 150, -2150, 0.8, 0.5), new Zona("BombsiteA", -450, -2050, 1.4, 1.6),
                    new Zona("Jungle", -1150, -1200, 0.8, 1.2), new Zona("CTSpawn", -1650, -1850, 0.3, 0.6),
                    new Zona("TopofMid", 300, -400, 0.8, 1.1), new Zona("Middle", -500, -650, 1.0, 1.0),
                    new Zona("Connector", -850, -900, 0.6, 0.9), new Zona("Apartments", -700, 650, 1.0, 0.7),
                    new Zona("BombsiteB", -2100, 350, 1.3, 1.5), new Zona("Market", -2200, -350, 0.5, 0.8)),
            "de_inferno", List.of(
                    new Zona("TSpawn", -1600, 400, 0.2, 0.3), new Zona("SecondMid", -600, 300, 0.7, 0.8),
                    new Zona("Apartments", 300, 800, 1.1, 0.8), new Zona("Balcony", 900, 400, 0.8, 1.0),
                    new Zona("BombsiteA", 1900, 300, 1.4, 1.5), new Zona("Pit", 2200, -300, 0.6, 1.0),
                    new Zona("Arch", 1500, 1200, 0.5, 0.9), new Zona("Middle", 300, 0, 0.9, 0.9),
                    new Zona("Banana", 200, 1600, 1.3, 1.2), new Zona("BombsiteB", 400, 2600, 1.3, 1.5),
                    new Zona("Construction", 1100, 2400, 0.6, 0.8), new Zona("CTSpawn", 2200, 1500, 0.3, 0.5)),
            "de_nuke", List.of(
                    new Zona("TSpawn", -1800, -800, 0.2, 0.3), new Zona("Outside", -300, -1500, 1.2, 0.9),
                    new Zona("Lobby", -500, -500, 0.9, 0.6), new Zona("Hut", 400, -700, 1.0, 1.1),
                    new Zona("Ramp", 300, 200, 1.0, 1.0), new Zona("BombsiteA", 700, -300, 1.4, 1.5),
                    new Zona("Heaven", 900, -900, 0.6, 1.0), new Zona("Vents", 900, 300, 0.5, 0.7),
                    new Zona("BombsiteB", 800, 700, 1.2, 1.4), new Zona("Secret", 1200, -1500, 0.7, 0.8),
                    new Zona("CTSpawn", 1700, -400, 0.3, 0.5)),
            "de_ancient", List.of(
                    new Zona("TSpawn", -400, -2100, 0.2, 0.3), new Zona("Mid", -300, -500, 1.3, 1.1),
                    new Zona("Donut", 400, -200, 0.9, 1.0), new Zona("BombsiteA", -900, 700, 1.4, 1.5),
                    new Zona("Temple", -1300, -300, 0.8, 0.9), new Zona("Cave", 1300, -700, 0.8, 0.8),
                    new Zona("BombsiteB", 1100, 500, 1.3, 1.5), new Zona("Elbow", 300, 600, 0.6, 0.9),
                    new Zona("CTSpawn", 0, 1300, 0.3, 0.5)),
            "de_anubis", List.of(
                    new Zona("TSpawn", 0, -2000, 0.2, 0.3), new Zona("Canal", 100, -800, 1.3, 1.0),
                    new Zona("Bridge", 300, -200, 0.9, 1.0), new Zona("Mid", -300, -400, 1.0, 0.9),
                    new Zona("BombsiteA", 1100, 500, 1.4, 1.5), new Zona("Connector", 300, 600, 0.7, 0.9),
                    new Zona("BombsiteB", -1000, 400, 1.3, 1.5), new Zona("Ruins", -1200, -700, 0.8, 0.8),
                    new Zona("CTSpawn", 0, 1400, 0.3, 0.5)),
            "de_dust2", List.of(
                    new Zona("TSpawn", -500, -1000, 0.2, 0.3), new Zona("LongDoors", 500, -600, 1.3, 1.0),
                    new Zona("LongA", 1400, 600, 1.0, 1.0), new Zona("BombsiteA", 1100, 2400, 1.4, 1.5),
                    new Zona("CTSpawn", 300, 2100, 0.3, 0.6), new Zona("Catwalk", 300, 1200, 0.8, 0.9),
                    new Zona("Middle", -400, 1000, 0.9, 1.0), new Zona("TopofMid", -300, 0, 0.7, 0.8),
                    new Zona("Tunnels", -1800, 900, 0.9, 0.7), new Zona("BombsiteB", -1500, 2500, 1.3, 1.5),
                    new Zona("OutsideTunnel", -1900, 0, 0.5, 0.5)),
            "de_train", List.of(
                    new Zona("TSpawn", -2000, 100, 0.2, 0.3), new Zona("Ivy", -900, 1300, 1.3, 1.0),
                    new Zona("TMain", -900, -300, 0.9, 0.8), new Zona("BombsiteA", 300, 300, 1.4, 1.5),
                    new Zona("Connector", 600, -500, 0.6, 0.8), new Zona("PopDog", 900, -100, 0.7, 0.9),
                    new Zona("BombsiteB", 400, -1200, 1.3, 1.5), new Zona("Ladder", 1100, 900, 0.5, 0.8),
                    new Zona("CTSpawn", 1600, 300, 0.3, 0.5)));

    /** Probabilidad de ganar la ronda según la compra (luego se cuadra con el marcador). */
    private static final Map<String, Double> GANAR = Map.of(
            "pistola", 0.5, "eco", 0.15, "forzada", 0.38, "completa", 0.6);

    private final RondaRepo rondas;
    private final MuerteMapaRepo muertes;
    private final DemoRepo demos;
    private final Random rnd = new Random(12);

    RondasDemo(RondaRepo rondas, MuerteMapaRepo muertes, DemoRepo demos) {
        this.rondas = rondas;
        this.muertes = muertes;
        this.demos = demos;
    }

    /** Analiza (de mentira) todas las partidas de CS2. Devuelve cuántas. */
    int generar(List<Participacion> participaciones) {
        Map<Long, List<Participacion>> porPartida = new LinkedHashMap<>();
        participaciones.stream()
                .filter(p -> p.getJuego() == Juego.CS2)
                .sorted(Comparator.comparing((Participacion p) -> p.getPartida().getJugadaEn())
                        .thenComparing(p -> p.getPartida().getId())
                        .thenComparing(p -> p.getJugador().getSlug()))
                .forEach(p -> porPartida.computeIfAbsent(p.getPartida().getId(), k -> new ArrayList<>()).add(p));
        for (List<Participacion> deLaPartida : porPartida.values()) {
            partida(deLaPartida);
        }
        return porPartida.size();
    }

    private void partida(List<Participacion> deLaPartida) {
        Partida partida = deLaPartida.getFirst().getPartida();
        Participacion primera = deLaPartida.getFirst();
        String mapa = partida.getModo();
        List<Zona> zonas = ZONAS.getOrDefault(mapa, ZONAS.get("de_mirage"));
        int total = entero(primera.getDatos().get("rondas"), 22);
        boolean gano = Boolean.TRUE.equals(primera.getGano());
        int nuestras = gano ? 13 : total - 13;

        // Compras y resultado de cada ronda, cuadrando con el marcador: la última es del que gana la partida.
        List<String> compras = new ArrayList<>();
        List<Boolean> ganadas = new ArrayList<>();
        int quedan = gano ? nuestras - 1 : nuestras;
        Boolean anterior = null;
        for (int r = 1; r <= total; r++) {
            String compra = compra(r, anterior);
            boolean gana;
            if (r == total) {
                gana = gano;
            } else {
                int restantes = total - r;
                gana = quedan >= restantes || (quedan > 0 && rnd.nextDouble() < GANAR.get(compra));
                if (gana) {
                    quedan--;
                }
            }
            compras.add(compra);
            ganadas.add(gana);
            anterior = gana;
        }
        String ladoInicial = rnd.nextBoolean() ? "CT" : "T";

        List<double[]> anonimas = new ArrayList<>();
        boolean[] aperturaCogida = new boolean[total + 1];
        for (Participacion p : deLaPartida) {
            jugador(partida, p, total, compras, ganadas, ladoInicial, zonas, mapa, aperturaCogida, anonimas);
        }
        // Las muertes del resto de la partida (unas cuantas por ronda), sin decir de quién: dibujan el mapa.
        for (int r = 1; r <= total; r++) {
            String ladoNuestro = lado(r, ladoInicial);
            for (int i = 0; i < 3; i++) {
                String lado = rnd.nextBoolean() ? ladoNuestro : contrario(ladoNuestro);
                Zona z = zona(zonas, lado, null, 0);
                anonimas.add(new double[] {cerca(z.x()), cerca(z.y()), zonas.indexOf(z)});
            }
        }
        for (double[] m : anonimas) {
            muertes.save(new MuerteMapa(mapa, m[0], m[1], zonas.get((int) m[2]).nombre()));
        }
        Demo demo = new Demo(partida, null);
        demo.analizada();
        demos.save(demo);
    }

    private void jugador(
            Partida partida, Participacion p, int total, List<String> compras, List<Boolean> ganadas,
            String ladoInicial, List<Zona> zonas, String mapa, boolean[] aperturaCogida, List<double[]> anonimas) {
        String slug = p.getJugador().getSlug();
        Estilo e = ESTILOS.getOrDefault(slug, NORMAL);
        int kills = p.getKills() == null ? 0 : p.getKills();
        int muertesTotal = Math.min(total, p.getMuertes() == null ? 0 : p.getMuertes());
        int asist = p.getAsistencias() == null ? 0 : p.getAsistencias();
        double adr = numero(p.getDatos().get("adr"), 75);
        int utilidad = (int) numero(p.getDatos().get("dano_utilidad"), 80);

        // Muertes: más en las rondas perdidas (y en las de T, si le pasa).
        double[] pesoMuerte = new double[total + 1];
        for (int r = 1; r <= total; r++) {
            pesoMuerte[r] = (ganadas.get(r - 1) ? 1.0 : 3.0) * ("T".equals(lado(r, ladoInicial)) ? e.muertesT() : 1);
        }
        boolean[] murio = new boolean[total + 1];
        for (int i = 0; i < muertesTotal; i++) {
            murio[elegir(pesoMuerte, murio, false)] = true;
        }
        // Kills: más en las ganadas, menos en las eco, y según el lado.
        double[] pesoKill = new double[total + 1];
        for (int r = 1; r <= total; r++) {
            double lado = "T".equals(lado(r, ladoInicial)) ? e.killsT() : 2 - e.killsT();
            pesoKill[r] = (ganadas.get(r - 1) ? 1.6 : 0.7) * lado * ("eco".equals(compras.get(r - 1)) ? 0.5 : 1);
        }
        int[] k = repartir(kills, pesoKill, 5);
        double[] uno = new double[total + 1];
        Arrays.fill(uno, 1, total + 1, 1.0);
        int[] a = repartir(asist, uno, 3);
        // Daño: por kills y asistencias, cuadrado con su ADR; la utilidad, una parte de ese daño.
        double[] base = new double[total + 1];
        double suma = 0;
        for (int r = 1; r <= total; r++) {
            base[r] = k[r] * 85 + a[r] * 35 + rnd.nextDouble() * 30;
            suma += base[r];
        }
        int danoTotal = (int) Math.round(adr * total);
        double[] pesoUtilidad = new double[total + 1];
        for (int r = 1; r <= total; r++) {
            pesoUtilidad[r] = "eco".equals(compras.get(r - 1)) ? 0.2 : 0.5 + rnd.nextDouble();
        }
        int[] u = repartir(utilidad, pesoUtilidad, 300);

        for (int r = 1; r <= total; r++) {
            String lado = lado(r, ladoInicial);
            int dano = (int) Math.min(500, Math.round(suma > 0 ? base[r] / suma * danoTotal : 0));
            int danoUtil = Math.min(dano, u[r]);
            int flash = 0;
            for (int i = 0; i < a[r]; i++) {
                if (rnd.nextDouble() < e.flash()) {
                    flash++;
                }
            }
            // El primer duelo: si le toca y no es ya de un compañero, gana si tiene kill y pierde si muere sin matar.
            String apertura = null;
            if (!aperturaCogida[r] && rnd.nextDouble() < e.apertura()) {
                boolean gana = rnd.nextDouble() < e.exitoApertura();
                if (gana && k[r] > 0) {
                    apertura = "ganada";
                } else if (!gana && murio[r] && k[r] == 0) {
                    apertura = "perdida";
                }
                aperturaCogida[r] = apertura != null;
            }
            Double x = null;
            Double y = null;
            String zona = null;
            boolean tradeado = false;
            if (murio[r]) {
                String trampa = slug.equals("j3") ? TRAMPA.get(mapa) : null;
                Zona z = zona(zonas, lado, trampa, "T".equals(lado) ? 0.55 : 0.25);
                x = cerca(z.x());
                y = cerca(z.y());
                zona = z.nombre();
                double pTrade = z.nombre().equals(trampa) ? 0.1 : e.tradeado();
                tradeado = "perdida".equals(apertura) ? rnd.nextDouble() < pTrade * 0.6 : rnd.nextDouble() < pTrade;
                anonimas.add(new double[] {x, y, zonas.indexOf(z)});
            }
            int trades = 0;
            for (int i = 0; i < k[r]; i++) {
                if (rnd.nextDouble() < 0.22) {
                    trades++;
                }
            }
            String compra = compras.get(r - 1);
            boolean kast = k[r] > 0 || a[r] > 0 || !murio[r] || tradeado;
            rondas.save(new Ronda(partida, p.getJugador(), r, lado, ganadas.get(r - 1), k[r], a[r], flash, dano,
                    danoUtil, murio[r], x, y, zona, tradeado, trades, apertura, equipamiento(compra, lado), compra,
                    kast));
        }
    }

    /** La compra de la ronda según cómo fue la anterior: tras perder, más ecos y forzadas. */
    private String compra(int ronda, Boolean anterior) {
        if (ronda == 1 || ronda == 13) {
            return "pistola";
        }
        if (ronda == 2 || ronda == 14) {
            return Boolean.TRUE.equals(anterior) ? "forzada" : "eco";
        }
        double r = rnd.nextDouble();
        if (Boolean.TRUE.equals(anterior)) {
            return r < 0.88 ? "completa" : "forzada";
        }
        return r < 0.3 ? "eco" : r < 0.55 ? "forzada" : "completa";
    }

    private int equipamiento(String compra, String lado) {
        int ct = "CT".equals(lado) ? 400 : 0;
        return switch (compra) {
            case "pistola" -> 650 + rnd.nextInt(350);
            case "eco" -> 200 + rnd.nextInt(1200);
            case "forzada" -> 1800 + rnd.nextInt(1500) + ct / 2;
            default -> 3900 + rnd.nextInt(1500) + ct;
        };
    }

    /** CT o T en la ronda: se cambia en la 13 (y en la prórroga se vuelve al de la primera parte). */
    private static String lado(int ronda, String inicial) {
        return ronda >= 13 && ronda <= 24 ? contrario(inicial) : inicial;
    }

    private static String contrario(String lado) {
        return "CT".equals(lado) ? "T" : "CT";
    }

    /** Una zona según dónde se muere de ese lado; con {@code trampa}, esa con probabilidad {@code pTrampa}. */
    private Zona zona(List<Zona> zonas, String lado, String trampa, double pTrampa) {
        if (trampa != null && rnd.nextDouble() < pTrampa) {
            for (Zona z : zonas) {
                if (z.nombre().equals(trampa)) {
                    return z;
                }
            }
        }
        double total = zonas.stream().mapToDouble(z -> "T".equals(lado) ? z.pesoT() : z.pesoCt()).sum();
        double r = rnd.nextDouble() * total;
        for (Zona z : zonas) {
            r -= "T".equals(lado) ? z.pesoT() : z.pesoCt();
            if (r <= 0) {
                return z;
            }
        }
        return zonas.getLast();
    }

    private double cerca(double centro) {
        return Math.round((centro + rnd.nextGaussian() * 170) * 10) / 10.0;
    }

    /** Un índice (desde 1) al azar según los pesos, sin repetir los ya {@code usados} si {@code repetir} es false. */
    private int elegir(double[] pesos, boolean[] usados, boolean repetir) {
        double total = 0;
        for (int i = 1; i < pesos.length; i++) {
            if (repetir || !usados[i]) {
                total += pesos[i];
            }
        }
        double r = rnd.nextDouble() * total;
        int ultimo = 1;
        for (int i = 1; i < pesos.length; i++) {
            if (!repetir && usados[i]) {
                continue;
            }
            ultimo = i;
            r -= pesos[i];
            if (r <= 0) {
                return i;
            }
        }
        return ultimo;
    }

    /** Reparte {@code n} entre las rondas según los pesos, con un tope por ronda (si no cabe, lo que quepa). */
    private int[] repartir(int n, double[] pesos, int tope) {
        int[] cuenta = new int[pesos.length];
        boolean[] llenas = new boolean[pesos.length];
        int libres = pesos.length - 1;
        for (int i = 0; i < n && libres > 0; i++) {
            int r = elegir(pesos, llenas, false);
            cuenta[r]++;
            if (cuenta[r] >= tope) {
                llenas[r] = true;
                libres--;
            }
        }
        return cuenta;
    }

    private static int entero(Object valor, int defecto) {
        return valor instanceof Number n ? n.intValue() : defecto;
    }

    private static double numero(Object valor, double defecto) {
        return valor instanceof Number n ? n.doubleValue() : defecto;
    }
}
