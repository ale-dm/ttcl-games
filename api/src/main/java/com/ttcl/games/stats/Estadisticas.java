package com.ttcl.games.stats;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.ComparativaNivel;
import com.ttcl.games.stats.Modelos.ConsejoAnterior;
import com.ttcl.games.stats.Modelos.FilaComparacion;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.FilaMomento;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.FilaSinergia;
import com.ttcl.games.stats.Modelos.Grupo;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.MetricaNivel;
import com.ttcl.games.stats.Modelos.Miembro;
import com.ttcl.games.stats.Modelos.Presencia;
import com.ttcl.games.stats.Modelos.PuntoSerie;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Modelos.SeguimientoConsejo;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
    /** Partidas juntos para que un compañero, un dúo o un trío cuente (y "solo", para la fila de solo). */
    public static final int MIN_PARTIDAS_SINERGIA = 3;
    /** Pausa a partir de la cual la siguiente partida ya es otra sesión. */
    public static final Duration PAUSA_SESION = Duration.ofMinutes(45);
    /** Hasta cuándo se mira atrás para ver si un consejo ha funcionado. */
    public static final Duration VENTANA_SEGUIMIENTO = Duration.ofDays(60);
    /** Partidas de un nivel con dato de una métrica para decir qué es lo normal en él (P8). */
    public static final int MIN_MUESTRAS_NIVEL = 50;

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

    /** Las participaciones jugadas desde {@code inicio} (incluido) o, si es null, todas. */
    public static List<FilaParticipacion> desde(List<FilaParticipacion> filas, Instant inicio) {
        return inicio == null ? filas : filas.stream().filter(f -> !f.jugadaEn().isBefore(inicio)).toList();
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

    // ─── Sinergias ──────────────────────────────────────────────────────────

    /**
     * Cómo le va a un jugador según con quién del equipo juega: por cada compañero, sus partidas juntos y, para
     * comparar, las demás; y solo, sin nadie del equipo. Solo cuenta como compañero quien estaba en su bando (mismo
     * resultado): en FACEIT dos del equipo pueden caer en bandos contrarios. Filas con menos de
     * {@value #MIN_PARTIDAS_SINERGIA} partidas, fuera.
     *
     * @param presencias quién del equipo jugó cada partida, por id de partida (incluido el propio jugador)
     */
    public static Sinergias sinergias(
            Juego juego, String slug, List<FilaParticipacion> filas, Map<Long, List<Presencia>> presencias) {
        Map<String, String> nombres = new LinkedHashMap<>();
        Map<String, List<FilaParticipacion>> con = new LinkedHashMap<>();
        List<FilaParticipacion> solo = new ArrayList<>();
        for (FilaParticipacion f : filas) {
            List<Presencia> companeros = presencias.getOrDefault(f.partidaId(), List.of()).stream()
                    .filter(p -> !p.slug().equals(slug) && Objects.equals(p.gano(), f.gano()))
                    .toList();
            if (companeros.isEmpty()) {
                solo.add(f);
            }
            for (Presencia p : companeros) {
                nombres.putIfAbsent(p.slug(), p.nombre());
                con.computeIfAbsent(p.slug(), k -> new ArrayList<>()).add(f);
            }
        }
        List<FilaSinergia> lista = con.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_PARTIDAS_SINERGIA)
                .map(e -> filaSinergia(juego, e.getKey(), nombres.get(e.getKey()), e.getValue(), filas))
                .sorted(Comparator.comparingInt(FilaSinergia::partidas).reversed()
                        .thenComparing(FilaSinergia::nombre))
                .toList();
        FilaSinergia filaSolo =
                solo.size() >= MIN_PARTIDAS_SINERGIA ? filaSinergia(juego, null, null, solo, filas) : null;
        return new Sinergias(filaSolo, lista);
    }

    private static FilaSinergia filaSinergia(
            Juego juego, String slug, String nombre, List<FilaParticipacion> con, List<FilaParticipacion> todas) {
        Set<Long> ids = con.stream().map(FilaParticipacion::partidaId).collect(Collectors.toSet());
        List<FilaParticipacion> sin = todas.stream().filter(f -> !ids.contains(f.partidaId())).toList();
        ResumenJuego r = resumir(juego, con);
        Double winrateSin = sin.isEmpty() ? null : resumir(juego, sin).winrate();
        return new FilaSinergia(slug, nombre, r.partidas(), r.victorias(), r.winrate(), r.kd(), sin.size(), winrateSin);
    }

    /**
     * Dúos ({@code tamano} 2) o tríos (3) del equipo: partidas en las que estaban juntos en el mismo bando, aunque
     * hubiera alguien más. Con al menos {@value #MIN_PARTIDAS_SINERGIA} partidas; el mejor winrate primero y, a
     * igualdad, el que más ha jugado.
     *
     * @param presencias quién del equipo jugó cada partida de un juego, por id de partida
     */
    public static List<Grupo> grupos(Map<Long, List<Presencia>> presencias, int tamano) {
        Map<List<String>, List<Boolean>> resultados = new LinkedHashMap<>();
        Map<String, String> nombres = new LinkedHashMap<>();
        for (List<Presencia> partida : presencias.values()) {
            // Un bando por resultado (victoria, derrota o sin dato), con los slugs en orden para que AB y BA sean uno.
            Map<String, List<Presencia>> bandos = partida.stream()
                    .sorted(Comparator.comparing(Presencia::slug))
                    .collect(Collectors.groupingBy(
                            p -> String.valueOf(p.gano()), LinkedHashMap::new, Collectors.toList()));
            for (List<Presencia> bando : bandos.values()) {
                bando.forEach(p -> nombres.putIfAbsent(p.slug(), p.nombre()));
                for (List<Presencia> combinacion : combinaciones(bando, tamano)) {
                    List<String> clave = combinacion.stream().map(Presencia::slug).toList();
                    resultados.computeIfAbsent(clave, k -> new ArrayList<>()).add(combinacion.getFirst().gano());
                }
            }
        }
        return resultados.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_PARTIDAS_SINERGIA)
                .map(e -> {
                    int victorias = (int) e.getValue().stream().filter(Boolean.TRUE::equals).count();
                    int derrotas = (int) e.getValue().stream().filter(Boolean.FALSE::equals).count();
                    Double winrate = victorias + derrotas == 0
                            ? null
                            : redondear(100.0 * victorias / (victorias + derrotas), 1);
                    List<Miembro> miembros = e.getKey().stream().map(s -> new Miembro(s, nombres.get(s))).toList();
                    return new Grupo(miembros, e.getValue().size(), victorias, winrate);
                })
                .sorted(Comparator.comparing(Grupo::winrate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingInt(Grupo::partidas).reversed()))
                .toList();
    }

    /** Todas las combinaciones de {@code k} elementos, en el orden de la lista. */
    private static <T> List<List<T>> combinaciones(List<T> lista, int k) {
        if (k == 0) {
            return List.of(List.of());
        }
        List<List<T>> resultado = new ArrayList<>();
        for (int i = 0; i <= lista.size() - k; i++) {
            T primero = lista.get(i);
            for (List<T> resto : combinaciones(lista.subList(i + 1, lista.size()), k - 1)) {
                List<T> combinacion = new ArrayList<>(List.of(primero));
                combinacion.addAll(resto);
                resultado.add(combinacion);
            }
        }
        return resultado;
    }

    // ─── Sesiones ───────────────────────────────────────────────────────────

    /**
     * Cómo le va según cuándo juega: la 1ª, la 2ª o de la 3ª en adelante de una sesión, después de ganar o de perder
     * la anterior de la sesión, y por hora del día (en {@code zona}). Una sesión son partidas seguidas, con menos de
     * {@link #PAUSA_SESION} entre el final de una (si no se sabe cuánto duró, su principio) y el principio de la
     * siguiente. Cada fila va con el resto de partidas para comparar; las filas sin partidas no salen.
     */
    public static Sesiones sesiones(Juego juego, List<FilaParticipacion> filas, ZoneId zona) {
        Map<String, List<FilaParticipacion>> porOrden = listas("1", "2", "3+");
        Map<String, List<FilaParticipacion>> trasResultado = listas("victoria", "derrota");
        Map<String, List<FilaParticipacion>> porFranja = listas("manana", "tarde", "noche", "madrugada");
        int sesiones = 0;
        int enSesion = 0;
        FilaParticipacion anterior = null;
        for (FilaParticipacion f : filas.stream().sorted(Comparator.comparing(FilaParticipacion::jugadaEn)).toList()) {
            boolean seguida = anterior != null && pausa(anterior, f).compareTo(PAUSA_SESION) < 0;
            if (!seguida) {
                sesiones++;
                enSesion = 0;
            }
            enSesion++;
            porOrden.get(enSesion == 1 ? "1" : enSesion == 2 ? "2" : "3+").add(f);
            if (seguida && anterior.gano() != null) {
                trasResultado.get(anterior.gano() ? "victoria" : "derrota").add(f);
            }
            porFranja.get(franja(f.jugadaEn().atZone(zona).getHour())).add(f);
            anterior = f;
        }
        return new Sesiones(
                sesiones,
                sesiones == 0 ? null : redondear((double) filas.size() / sesiones, 1),
                filasMomento(juego, porOrden, filas),
                filasMomento(juego, trasResultado, filas),
                filasMomento(juego, porFranja, filas));
    }

    private static Duration pausa(FilaParticipacion anterior, FilaParticipacion siguiente) {
        int duracion = anterior.duracionSeg() == null ? 0 : anterior.duracionSeg();
        return Duration.between(anterior.jugadaEn().plusSeconds(duracion), siguiente.jugadaEn());
    }

    /** Mañana de 6 a 14 h, tarde de 14 a 20 h, noche de 20 a 24 h y madrugada de 0 a 6 h. */
    public static String franja(int hora) {
        if (hora < 6) {
            return "madrugada";
        }
        return hora < 14 ? "manana" : hora < 20 ? "tarde" : "noche";
    }

    /** Una lista vacía por clave, en ese orden. */
    private static Map<String, List<FilaParticipacion>> listas(String... claves) {
        Map<String, List<FilaParticipacion>> mapa = new LinkedHashMap<>();
        for (String clave : claves) {
            mapa.put(clave, new ArrayList<>());
        }
        return mapa;
    }

    private static List<FilaMomento> filasMomento(
            Juego juego, Map<String, List<FilaParticipacion>> grupos, List<FilaParticipacion> todas) {
        return grupos.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> {
                    Set<Long> ids = e.getValue().stream().map(FilaParticipacion::partidaId).collect(Collectors.toSet());
                    List<FilaParticipacion> resto = todas.stream().filter(f -> !ids.contains(f.partidaId())).toList();
                    ResumenJuego r = resumir(juego, e.getValue());
                    return new FilaMomento(e.getKey(), r.partidas(), r.victorias(), r.winrate(), r.kd(), resto.size(),
                            resto.isEmpty() ? null : resumir(juego, resto).winrate());
                })
                .toList();
    }

    // ─── Seguimiento de consejos ────────────────────────────────────────────

    /**
     * Cómo han ido los consejos de los últimos {@link #VENTANA_SEGUIMIENTO} que hablaban de una métrica: por cada
     * recomendación, la primera vez que se dio en ese tiempo (con su valor de entonces) y el valor en las partidas
     * jugadas desde ese momento. El más antiguo primero.
     */
    public static List<SeguimientoConsejo> seguimiento(
            Juego juego, List<FilaParticipacion> filas, List<ConsejoAnterior> consejos, Instant ahora) {
        Instant inicio = ahora.minus(VENTANA_SEGUIMIENTO);
        Map<String, ConsejoAnterior> primero = new LinkedHashMap<>();
        consejos.stream()
                .filter(c -> c.metrica() != null && c.valor() != null && !c.dadoEn().isBefore(inicio))
                .sorted(Comparator.comparing(ConsejoAnterior::dadoEn))
                .forEach(c -> primero.putIfAbsent(c.insight(), c));
        return primero.values().stream()
                .map(c -> {
                    List<FilaParticipacion> desde = filas.stream().filter(f -> f.jugadaEn().isAfter(c.dadoEn())).toList();
                    Double valorDesde = desde.isEmpty() ? null : valor(resumir(juego, desde), c.metrica());
                    int dias = (int) Duration.between(c.dadoEn(), ahora).toDays();
                    return new SeguimientoConsejo(
                            c.insight(), c.metrica(), c.valor(), c.dadoEn(), dias, desde.size(), valorDesde);
                })
                .toList();
    }

    // ─── Nivel (P8) ─────────────────────────────────────────────────────────

    /**
     * Cómo queda {@code resumen} frente a {@code muestras}, partidas de jugadores de su nivel que no son del equipo.
     * Por cada métrica (menos el winrate, que en tu nivel es siempre de un 50 % por cómo se emparejan las partidas),
     * lo normal en ese nivel y su percentil, si hay al menos {@value #MIN_MUESTRAS_NIVEL} partidas con dato. Null sin
     * nivel.
     */
    public static ComparativaNivel comparativaNivel(
            Juego juego, Integer nivel, Integer elo, ResumenJuego resumen, List<FilaParticipacion> muestras) {
        if (nivel == null) {
            return null;
        }
        long kills = suma(muestras, FilaParticipacion::kills);
        long muertes = suma(muestras, FilaParticipacion::muertes);
        long asist = suma(muestras, FilaParticipacion::asistencias);
        List<MetricaNivel> metricas = new ArrayList<>();
        for (String clave : metricas(juego)) {
            if (clave.equals("winrate")) {
                continue;
            }
            // Las que salen de totales (un 3/4 y un 0/1 no son un 37,5 %) se comparan con el total del nivel.
            Double total = metricaDerivada(juego, clave, muestras, kills, muertes, asist);
            if (total != null) {
                if (muestras.size() >= MIN_MUESTRAS_NIVEL) {
                    metricas.add(new MetricaNivel(clave, total, null, muestras.size()));
                }
                continue;
            }
            List<Double> valores = muestras.stream()
                    .map(f -> valorPartida(f, clave))
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            if (valores.size() >= MIN_MUESTRAS_NIVEL) {
                metricas.add(new MetricaNivel(
                        clave, mediana(valores), percentil(valores, valor(resumen, clave)), valores.size()));
            }
        }
        return new ComparativaNivel(nivel, elo, muestras.size(), metricas);
    }

    /** Valor de una métrica en una sola partida. En el K/D, sin muertes cuenta como una (si no, sería infinito). */
    static Double valorPartida(FilaParticipacion f, String metrica) {
        return switch (metrica) {
            case "kills_media" -> entero(f.kills());
            case "muertes_media" -> entero(f.muertes());
            case "asistencias_media" -> entero(f.asistencias());
            case "kd" -> f.kills() == null || f.muertes() == null
                    ? null
                    : (double) f.kills() / Math.max(1, f.muertes());
            default -> numero(f.datos().get(metrica));
        };
    }

    private static double mediana(List<Double> ordenados) {
        int n = ordenados.size();
        double m = n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
        return redondear(m, 2);
    }

    /** Porcentaje de {@code valores} por debajo de {@code tu}; los empates cuentan la mitad. */
    private static Double percentil(List<Double> valores, Double tu) {
        if (tu == null) {
            return null;
        }
        long debajo = valores.stream().filter(v -> v < tu).count();
        long iguales = valores.stream().filter(v -> v.equals(tu)).count();
        return redondear(100.0 * (debajo + iguales / 2.0) / valores.size(), 1);
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
