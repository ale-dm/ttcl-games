package com.ttcl.games.stats;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaComparacion;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.PuntoSerie;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cálculo de estadísticas a partir de participaciones. Funciones puras: no tocan la base de datos. */
public final class Estadisticas {

    public static final int LONGITUD_FORMA = 10;
    public static final int PARTIDAS_RECIENTES = 10;

    /** Recuentos que no se promedian: con ellos se calculan porcentajes sobre el total (entry_pct, dano_min...). */
    private static final Set<String> RECUENTOS = Set.of(
            "entry_intentos", "entry_ganados", "clutch_intentos", "clutch_ganados", "rondas", "minutos");

    private static final Comparator<FilaParticipacion> MAS_RECIENTE_PRIMERO =
            Comparator.comparing(FilaParticipacion::jugadaEn).reversed();

    private Estadisticas() {}

    // ─── Resumen ────────────────────────────────────────────────────────────

    public static ResumenJuego resumir(Juego juego, List<FilaParticipacion> filas) {
        List<FilaParticipacion> ordenadas = filas.stream().sorted(MAS_RECIENTE_PRIMERO).toList();
        int victorias = (int) filas.stream().filter(f -> Boolean.TRUE.equals(f.gano())).count();
        int derrotas = (int) filas.stream().filter(f -> Boolean.FALSE.equals(f.gano())).count();
        int conResultado = victorias + derrotas;

        long sumaKills = suma(filas, FilaParticipacion::kills);
        long sumaMuertes = suma(filas, FilaParticipacion::muertes);
        long sumaAsist = suma(filas, FilaParticipacion::asistencias);

        Map<String, Double> datosMedios = new LinkedHashMap<>();
        for (String clave : juego.metricasPropias()) {
            Double valor = metricaDerivada(juego, clave, filas, sumaKills, sumaMuertes, sumaAsist);
            if (valor == null) {
                valor = media(filas.stream().map(f -> numero(f.datos().get(clave))).toList());
            }
            if (valor != null) {
                datosMedios.put(clave, valor);
            }
        }
        // Lo que no está en la lista del juego también se promedia (al final), por si una fuente trae más campos.
        Map<String, List<Double>> extra = new TreeMap<>();
        for (FilaParticipacion f : filas) {
            f.datos().forEach((clave, valor) -> {
                Double n = numero(valor);
                if (n != null && !RECUENTOS.contains(clave) && !juego.metricasPropias().contains(clave)) {
                    extra.computeIfAbsent(clave, k -> new ArrayList<>()).add(n);
                }
            });
        }
        extra.forEach((clave, valores) -> datosMedios.put(clave, media(valores)));

        return new ResumenJuego(
                juego,
                filas.size(),
                victorias,
                derrotas,
                conResultado == 0 ? null : redondear(100.0 * victorias / conResultado, 1),
                sumaMuertes > 0 ? redondear((double) sumaKills / sumaMuertes, 2) : null,
                media(filas.stream().map(f -> entero(f.kills())).toList()),
                media(filas.stream().map(f -> entero(f.muertes())).toList()),
                media(filas.stream().map(f -> entero(f.asistencias())).toList()),
                datosMedios,
                ordenadas.stream()
                        .limit(LONGITUD_FORMA)
                        .map(f -> f.gano() == null ? "?" : f.gano() ? "V" : "D")
                        .collect(Collectors.joining()),
                ordenadas.isEmpty() ? null : ordenadas.getFirst().jugadaEn());
    }

    /** Métricas que salen de totales y no de la media por partida (un 3/4 y un 0/1 no son un 37,5 % de media). */
    private static Double metricaDerivada(
            Juego juego, String clave, List<FilaParticipacion> filas, long kills, long muertes, long asist) {
        return switch (juego) {
            case CS2 -> switch (clave) {
                case "entry_pct" -> porcentaje(filas, "entry_ganados", "entry_intentos");
                case "clutch_pct" -> porcentaje(filas, "clutch_ganados", "clutch_intentos");
                default -> null;
            };
            case SMITE2 -> switch (clave) {
                case "kda" -> muertes > 0 ? redondear((double) (kills + asist) / muertes, 2) : null;
                case "dano_min" -> porMinuto(filas, "dano");
                case "oro_min" -> porMinuto(filas, "oro");
                default -> null;
            };
        };
    }

    private static Double porcentaje(List<FilaParticipacion> filas, String parte, String total) {
        double p = 0;
        double t = 0;
        for (FilaParticipacion f : filas) {
            Double vp = numero(f.datos().get(parte));
            Double vt = numero(f.datos().get(total));
            if (vp != null && vt != null) {
                p += vp;
                t += vt;
            }
        }
        return t > 0 ? redondear(100 * p / t, 1) : null;
    }

    private static Double porMinuto(List<FilaParticipacion> filas, String campo) {
        double valor = 0;
        double minutos = 0;
        for (FilaParticipacion f : filas) {
            Double v = numero(f.datos().get(campo));
            Double m = numero(f.datos().get("minutos"));
            if (v != null && m != null && m > 0) {
                valor += v;
                minutos += m;
            }
        }
        return minutos > 0 ? redondear(valor / minutos, 1) : null;
    }

    /** Las {@code n} participaciones más recientes. */
    public static List<FilaParticipacion> recientes(List<FilaParticipacion> filas, int n) {
        return filas.stream().sorted(MAS_RECIENTE_PRIMERO).limit(n).toList();
    }

    // ─── Equipo ─────────────────────────────────────────────────────────────

    /** Media de los resúmenes de otros jugadores (cada jugador pesa igual). Null si no hay nadie más. */
    public static MediasEquipo mediasEquipo(List<ResumenJuego> otros) {
        if (otros.isEmpty()) {
            return null;
        }
        Map<String, List<Double>> datos = new LinkedHashMap<>();
        for (ResumenJuego r : otros) {
            r.datosMedios().forEach((k, v) -> datos.computeIfAbsent(k, x -> new ArrayList<>()).add(v));
        }
        Map<String, Double> datosMedios = new LinkedHashMap<>();
        datos.forEach((k, v) -> datosMedios.put(k, media(v)));
        return new MediasEquipo(
                otros.size(),
                mediaDe(otros, ResumenJuego::winrate, 1),
                mediaDe(otros, ResumenJuego::kd, 2),
                mediaDe(otros, ResumenJuego::killsMedia, 2),
                mediaDe(otros, ResumenJuego::muertesMedia, 2),
                mediaDe(otros, ResumenJuego::asistenciasMedia, 2),
                datosMedios);
    }

    private static Double mediaDe(List<ResumenJuego> lista, Function<ResumenJuego, Double> campo, int decimales) {
        List<Double> valores = lista.stream().map(campo).filter(Objects::nonNull).toList();
        if (valores.isEmpty()) {
            return null;
        }
        return redondear(valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow(), decimales);
    }

    // ─── Desglose y serie ───────────────────────────────────────────────────

    /** Partidas, victorias, winrate y K/D por mapa (CS2) o dios (SMITE 2). La más jugada primero. */
    public static List<FilaDesglose> desglose(Juego juego, List<FilaParticipacion> filas) {
        Map<String, List<FilaParticipacion>> grupos = new LinkedHashMap<>();
        for (FilaParticipacion f : filas) {
            Object valor = f.datos().get(juego.claveDesglose());
            String clave = valor != null ? String.valueOf(valor) : f.modo();
            if (clave != null && !clave.isBlank()) {
                grupos.computeIfAbsent(clave, k -> new ArrayList<>()).add(f);
            }
        }
        return grupos.entrySet().stream()
                .map(e -> {
                    ResumenJuego r = resumir(juego, e.getValue());
                    return new FilaDesglose(e.getKey(), r.partidas(), r.victorias(), r.winrate(), r.kd());
                })
                .sorted(Comparator.comparingInt(FilaDesglose::partidas)
                        .reversed()
                        .thenComparing(FilaDesglose::clave))
                .toList();
    }

    /** Las últimas {@code n} partidas en orden cronológico, para la gráfica. */
    public static List<PuntoSerie> serie(Juego juego, List<FilaParticipacion> filas, int n) {
        List<FilaParticipacion> ultimas = new ArrayList<>(recientes(filas, n));
        ultimas.sort(Comparator.comparing(FilaParticipacion::jugadaEn));
        return ultimas.stream()
                .map(f -> {
                    Object clave = f.datos().get(juego.claveDesglose());
                    return new PuntoSerie(f.partidaId(), f.jugadaEn(), f.gano(), f.kills(), f.muertes(),
                            f.asistencias(), clave != null ? String.valueOf(clave) : f.modo());
                })
                .toList();
    }

    // ─── Comparación ────────────────────────────────────────────────────────

    /** Valor de una métrica por su clave: las comunes ("winrate", "kd", "kills_media"...) o las del juego. */
    public static Double valor(ResumenJuego r, String metrica) {
        return switch (metrica) {
            case "partidas" -> (double) r.partidas();
            case "winrate" -> r.winrate();
            case "kd" -> r.kd();
            case "kills_media" -> r.killsMedia();
            case "muertes_media" -> r.muertesMedia();
            case "asistencias_media" -> r.asistenciasMedia();
            default -> r.datosMedios().get(metrica);
        };
    }

    /** Métricas comparables de un juego, en orden de importancia. */
    public static List<String> metricas(Juego juego) {
        List<String> lista = new ArrayList<>(List.of("winrate", "kd"));
        lista.addAll(juego.metricasPropias());
        lista.addAll(List.of("kills_media", "muertes_media", "asistencias_media"));
        return lista;
    }

    /** Compara dos resúmenes del mismo juego, métrica a métrica. */
    public static List<FilaComparacion> comparar(ResumenJuego a, ResumenJuego b) {
        List<FilaComparacion> filas = new ArrayList<>();
        filas.add(new FilaComparacion("partidas", (double) a.partidas(), (double) b.partidas(), "neutral", null));
        for (String metrica : metricas(a.juego())) {
            Double va = valor(a, metrica);
            Double vb = valor(b, metrica);
            if (va == null && vb == null) {
                continue;
            }
            String mejor = metrica.equals("muertes_media") ? "bajo" : "alto";
            String ventaja = null;
            if (va != null && vb != null && !va.equals(vb)) {
                boolean ganaA = mejor.equals("alto") ? va > vb : va < vb;
                ventaja = ganaA ? "a" : "b";
            }
            filas.add(new FilaComparacion(metrica, va, vb, mejor, ventaja));
        }
        return filas;
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    static double redondear(double valor, int decimales) {
        double factor = Math.pow(10, decimales);
        return Math.round(valor * factor) / factor;
    }

    private static Double media(List<Double> valores) {
        List<Double> validos = valores.stream().filter(Objects::nonNull).toList();
        if (validos.isEmpty()) {
            return null;
        }
        return redondear(validos.stream().mapToDouble(Double::doubleValue).average().orElseThrow(), 2);
    }

    private static long suma(List<FilaParticipacion> filas, Function<FilaParticipacion, Integer> campo) {
        return filas.stream().map(campo).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
    }

    private static Double entero(Integer n) {
        return n == null ? null : n.doubleValue();
    }

    static Double numero(Object valor) {
        if (valor instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        return null;
    }
}
