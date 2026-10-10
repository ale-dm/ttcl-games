package com.ttcl.games.stats;

import com.ttcl.games.stats.Modelos.CalorMapa;
import com.ttcl.games.stats.Modelos.EtiquetaZona;
import com.ttcl.games.stats.Modelos.FilaCompra;
import com.ttcl.games.stats.Modelos.FilaLado;
import com.ttcl.games.stats.Modelos.FilaRonda;
import com.ttcl.games.stats.Modelos.MapaMuertes;
import com.ttcl.games.stats.Modelos.MetricasRondas;
import com.ttcl.games.stats.Modelos.Punto;
import com.ttcl.games.stats.Modelos.PuntoMuerte;
import com.ttcl.games.stats.Modelos.ResumenDemos;
import com.ttcl.games.stats.Modelos.ZonaMuerte;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Lo que dicen las rondas de las demos analizadas (P12): KAST, trades, duelos de apertura, asistencias de flash,
 * utilidad, CT frente a T, economía, dónde muere y un rating propio. Funciones puras: no tocan la base de datos.
 */
public final class Rondas {

    /** Zonas que se dan de cada mapa (las de más muertes sin trade). */
    public static final int MAX_ZONAS = 8;
    /** Puntos del fondo del mapa de calor como mucho (con más, el dibujo no gana nada y pesa). */
    public static final int MAX_FONDO = 2500;
    /** Muertes en una zona para poner su nombre en el dibujo. */
    public static final int MIN_ETIQUETA = 5;
    public static final List<String> LADOS = List.of("CT", "T");
    public static final List<String> COMPRAS = List.of("pistola", "eco", "forzada", "completa");

    private Rondas() {}

    /** Todo lo de un jugador; {@code otros}, las rondas de cada uno del resto del equipo (para la media). */
    public static ResumenDemos resumen(List<FilaRonda> filas, List<List<FilaRonda>> otros) {
        List<MetricasRondas> deOtros = otros.stream().filter(o -> !o.isEmpty()).map(Rondas::metricas).toList();
        return new ResumenDemos(metricas(filas), mediaEquipo(deOtros), lados(filas), economia(filas), mapas(filas));
    }

    public static MetricasRondas metricas(List<FilaRonda> filas) {
        int rondas = filas.size();
        int partidas = (int) filas.stream().map(FilaRonda::partidaId).distinct().count();
        int kills = suma(filas, FilaRonda::kills);
        int asistencias = suma(filas, FilaRonda::asistencias);
        int muertes = cuenta(filas, FilaRonda::murio);
        int tradeadas = cuenta(filas, f -> f.murio() && f.tradeado());
        int aperturas = cuenta(filas, f -> f.apertura() != null);
        int ganadas = cuenta(filas, f -> "ganada".equals(f.apertura()));
        int trades = suma(filas, FilaRonda::trades);
        int conResultado = cuenta(filas, f -> f.gano() != null);
        Double kast = rondas == 0 ? null : pct(cuenta(filas, FilaRonda::kast), rondas);
        Double adr = porRonda(suma(filas, FilaRonda::dano), rondas, 1);
        Double kpr = porRonda(kills, rondas, 2);
        Double dpr = porRonda(muertes, rondas, 2);
        Double apr = porRonda(asistencias, rondas, 2);
        return new MetricasRondas(
                partidas,
                rondas,
                rating(kast, kpr, dpr, apr, adr),
                kast,
                adr,
                kpr,
                dpr,
                aperturas,
                ganadas,
                aperturas == 0 ? null : pct(ganadas, aperturas),
                trades,
                partidas == 0 ? null : redondear((double) trades / partidas, 2),
                muertes,
                tradeadas,
                muertes == 0 ? null : pct(tradeadas, muertes),
                partidas == 0 ? null : redondear((double) suma(filas, FilaRonda::asistenciasFlash) / partidas, 2),
                porRonda(suma(filas, FilaRonda::danoUtilidad), rondas, 1),
                conResultado == 0 ? null : pct(cuenta(filas, f -> Boolean.TRUE.equals(f.gano())), conResultado));
    }

    /**
     * Rating propio al estilo del Rating 2.0 de HLTV, con la aproximación que circula de su fórmula (HLTV no la publica):
     * {@code 0,0073·KAST + 0,3591·KPR − 0,5329·DPR + 0,2372·Impacto + 0,0032·ADR + 0,1587}, con
     * {@code Impacto = 2,13·KPR + 0,42·APR − 0,41}. Un jugador normal anda por 1,00. Null sin rondas.
     */
    public static Double rating(Double kast, Double kpr, Double dpr, Double apr, Double adr) {
        if (kast == null || kpr == null || dpr == null || apr == null || adr == null) {
            return null;
        }
        double impacto = 2.13 * kpr + 0.42 * apr - 0.41;
        return redondear(0.0073 * kast + 0.3591 * kpr - 0.5329 * dpr + 0.2372 * impacto + 0.0032 * adr + 0.1587, 2);
    }

    /** Media del resto del equipo (cada jugador pesa igual); partidas y rondas, sumadas. Null si no hay nadie. */
    public static MetricasRondas mediaEquipo(List<MetricasRondas> otros) {
        if (otros.isEmpty()) {
            return null;
        }
        return new MetricasRondas(
                otros.stream().mapToInt(MetricasRondas::partidas).sum(),
                otros.stream().mapToInt(MetricasRondas::rondas).sum(),
                media(otros, MetricasRondas::rating, 2),
                media(otros, MetricasRondas::kast, 1),
                media(otros, MetricasRondas::adr, 1),
                media(otros, MetricasRondas::kpr, 2),
                media(otros, MetricasRondas::dpr, 2),
                otros.stream().mapToInt(MetricasRondas::aperturas).sum(),
                otros.stream().mapToInt(MetricasRondas::aperturasGanadas).sum(),
                media(otros, MetricasRondas::aperturaPct, 1),
                otros.stream().mapToInt(MetricasRondas::trades).sum(),
                media(otros, MetricasRondas::tradesPartida, 2),
                otros.stream().mapToInt(MetricasRondas::muertes).sum(),
                otros.stream().mapToInt(MetricasRondas::muertesTradeadas).sum(),
                media(otros, MetricasRondas::tradeadasPct, 1),
                media(otros, MetricasRondas::flashPartida, 2),
                media(otros, MetricasRondas::utilidadRonda, 1),
                media(otros, MetricasRondas::winrateRondas, 1));
    }

    /** De CT y de T (los que tengan rondas). */
    public static List<FilaLado> lados(List<FilaRonda> filas) {
        List<FilaLado> lista = new ArrayList<>();
        for (String lado : LADOS) {
            List<FilaRonda> delLado = filas.stream().filter(f -> lado.equals(f.lado())).toList();
            if (!delLado.isEmpty()) {
                MetricasRondas m = metricas(delLado);
                lista.add(new FilaLado(lado, m.rondas(), ganadas(delLado), m.winrateRondas(), m.rating(), m.kast(), m.adr()));
            }
        }
        return lista;
    }

    /** Por tipo de compra (los que tengan rondas). */
    public static List<FilaCompra> economia(List<FilaRonda> filas) {
        List<FilaCompra> lista = new ArrayList<>();
        for (String compra : COMPRAS) {
            List<FilaRonda> deEsa = filas.stream().filter(f -> compra.equals(f.compra())).toList();
            if (!deEsa.isEmpty()) {
                MetricasRondas m = metricas(deEsa);
                lista.add(new FilaCompra(compra, m.rondas(), ganadas(deEsa), m.winrateRondas(), m.kpr()));
            }
        }
        return lista;
    }

    /** Sus muertes por mapa (el de más muertes primero) y, en cada uno, las zonas con más muertes sin trade. */
    public static List<MapaMuertes> mapas(List<FilaRonda> filas) {
        Map<String, List<FilaRonda>> porMapa = filas.stream()
                .filter(f -> f.mapa() != null)
                .collect(Collectors.groupingBy(FilaRonda::mapa, LinkedHashMap::new, Collectors.toList()));
        return porMapa.entrySet().stream()
                .map(e -> {
                    List<FilaRonda> muertes = e.getValue().stream().filter(FilaRonda::murio).toList();
                    Map<String, List<FilaRonda>> porZona = muertes.stream()
                            .filter(f -> f.muerteZona() != null)
                            .collect(Collectors.groupingBy(FilaRonda::muerteZona, LinkedHashMap::new, Collectors.toList()));
                    List<ZonaMuerte> zonas = porZona.entrySet().stream()
                            .map(z -> new ZonaMuerte(z.getKey(), z.getValue().size(), cuenta(z.getValue(), f -> !f.tradeado())))
                            .sorted(Comparator.comparingInt(ZonaMuerte::sinTrade).reversed()
                                    .thenComparing(Comparator.comparingInt(ZonaMuerte::muertes).reversed())
                                    .thenComparing(ZonaMuerte::zona))
                            .limit(MAX_ZONAS)
                            .toList();
                    int partidas = (int) e.getValue().stream().map(FilaRonda::partidaId).distinct().count();
                    return new MapaMuertes(e.getKey(), partidas, muertes.size(), cuenta(muertes, f -> !f.tradeado()), zonas);
                })
                .sorted(Comparator.comparingInt(MapaMuertes::muertes).reversed().thenComparing(MapaMuertes::mapa))
                .toList();
    }

    /** Un punto del fondo del mapa: dónde murió alguien, con el nombre de la zona para leer. */
    public record PuntoFondo(double x, double y, String zona) {}

    /**
     * Mapa de calor de sus muertes en {@code mapa}. El fondo (muertes de todos en ese mapa) se aclara a uno de cada n
     * si pasa de {@value #MAX_FONDO} puntos; los nombres de las zonas van en la mediana de sus muertes.
     */
    public static CalorMapa calor(String mapa, List<String> mapas, List<FilaRonda> filas, List<PuntoFondo> fondo) {
        List<PuntoMuerte> muertes = filas.stream()
                .filter(f -> mapa.equals(f.mapa()) && f.murio() && f.muerteX() != null && f.muerteY() != null)
                .map(f -> new PuntoMuerte(f.muerteX(), f.muerteY(), f.muerteZona(), f.tradeado()))
                .toList();
        int paso = Math.max(1, (int) Math.ceil((double) fondo.size() / MAX_FONDO));
        List<Punto> puntos = new ArrayList<>();
        for (int i = 0; i < fondo.size(); i += paso) {
            puntos.add(new Punto(fondo.get(i).x(), fondo.get(i).y()));
        }
        // Las etiquetas salen del fondo; si aún no hay fondo, de sus propias muertes.
        List<PuntoFondo> conZona = fondo.isEmpty()
                ? muertes.stream().map(m -> new PuntoFondo(m.x(), m.y(), m.zona())).toList()
                : fondo;
        List<EtiquetaZona> zonas = conZona.stream()
                .filter(p -> p.zona() != null)
                .collect(Collectors.groupingBy(PuntoFondo::zona, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_ETIQUETA)
                .map(e -> new EtiquetaZona(e.getKey(),
                        mediana(e.getValue().stream().map(PuntoFondo::x).toList()),
                        mediana(e.getValue().stream().map(PuntoFondo::y).toList())))
                .sorted(Comparator.comparing(EtiquetaZona::zona))
                .toList();
        return new CalorMapa(mapa, mapas, muertes, puntos, zonas);
    }

    /** Los mapas con sus rondas, el de más rondas primero. */
    public static List<String> mapasJugados(List<FilaRonda> filas) {
        return filas.stream()
                .filter(f -> f.mapa() != null)
                .collect(Collectors.groupingBy(FilaRonda::mapa, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * La zona tal y como la da el juego ("TopofMid", "BombsiteA", "CTSpawn") para leer: "Top of Mid", "Bombsite A",
     * "CT Spawn". Null si no hay.
     */
    public static String nombreZona(String zona) {
        if (zona == null || zona.isBlank()) {
            return null;
        }
        return zona.trim()
                .replaceAll("(?<=[A-Z][a-z]{2,20})of(?=[A-Z])", " of ")
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2")
                .replaceAll("\\s+", " ");
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private static int ganadas(List<FilaRonda> filas) {
        return cuenta(filas, f -> Boolean.TRUE.equals(f.gano()));
    }

    private static int suma(List<FilaRonda> filas, ToIntFunction<FilaRonda> campo) {
        return filas.stream().mapToInt(campo).sum();
    }

    private static int cuenta(List<FilaRonda> filas, Predicate<FilaRonda> condicion) {
        return (int) filas.stream().filter(condicion).count();
    }

    private static Double porRonda(int total, int rondas, int decimales) {
        return rondas == 0 ? null : redondear((double) total / rondas, decimales);
    }

    private static double pct(int parte, int total) {
        return redondear(100.0 * parte / total, 1);
    }

    private static Double media(List<MetricasRondas> lista, Function<MetricasRondas, Double> campo, int decimales) {
        List<Double> valores = lista.stream().map(campo).filter(Objects::nonNull).toList();
        return valores.isEmpty()
                ? null
                : redondear(valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow(), decimales);
    }

    private static double mediana(List<Double> valores) {
        List<Double> orden = valores.stream().sorted().toList();
        int n = orden.size();
        return n % 2 == 1 ? orden.get(n / 2) : (orden.get(n / 2 - 1) + orden.get(n / 2)) / 2;
    }

    private static double redondear(double valor, int decimales) {
        return Estadisticas.redondear(valor, decimales);
    }
}
