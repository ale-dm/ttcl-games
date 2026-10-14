package com.ttcl.games.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Capturas de las estadísticas de cada uno en csstats.gg (P13): qué números se guardan de cada captura y cómo se
 * comparan dos. csstats da totales acumulados (partidas, kills, muertes...), así que con dos capturas se sabe cómo
 * jugó solo entre medias (el tramo): kills nuevas entre muertes nuevas, victorias nuevas entre partidas nuevas...
 * Funciones puras: no tocan la base de datos.
 */
public final class Capturas {

    public static final String CSSTATS = "csstats";
    /** Con qué captura se compara la última para el Duende: la última que tenga al menos estos días más. */
    public static final Duration SEPARACION = Duration.ofDays(7);
    public static final int MAX_MAPAS = 12;
    private static final double MAXIMO = 1_000_000_000;

    /**
     * Un número de la captura.
     *
     * @param grupo general, totales, clutch, entradas o rango (la web los agrupa así)
     * @param tipo "total" si se acumula (partidas, kills...) o "valor" si es lo del momento (K/D, rating, ADR...)
     * @param formato "int", "dec" o "pct" (de 0 a 100)
     * @param mejor "alto", "bajo" o "neutral": hacia dónde es mejor que cambie
     */
    public record Campo(String clave, String grupo, String tipo, String formato, String mejor) {}

    private static Campo valor(String clave, String grupo, String formato, String mejor) {
        return new Campo(clave, grupo, "valor", formato, mejor);
    }

    private static Campo total(String clave, String grupo) {
        return new Campo(clave, grupo, "total", "int", "neutral");
    }

    /** Lo que se guarda de una captura del perfil de csstats, en el orden en que se enseña. */
    public static final List<Campo> CAMPOS = List.of(
            valor("rating", "general", "dec", "alto"),
            valor("kd", "general", "dec", "alto"),
            valor("winrate", "general", "pct", "alto"),
            valor("hs_pct", "general", "pct", "alto"),
            valor("adr", "general", "int", "alto"),
            valor("kast", "general", "pct", "alto"),
            total("partidas", "totales"),
            total("victorias", "totales"),
            total("derrotas", "totales"),
            total("empates", "totales"),
            total("kills", "totales"),
            total("muertes", "totales"),
            total("asistencias", "totales"),
            total("headshots", "totales"),
            total("dano", "totales"),
            total("rondas", "totales"),
            valor("clutch_pct", "clutch", "pct", "alto"),
            total("clutch_1v1_ganados", "clutch"),
            total("clutch_1v1_perdidos", "clutch"),
            total("clutch_1v2_ganados", "clutch"),
            total("clutch_1v2_perdidos", "clutch"),
            total("clutch_1v3_ganados", "clutch"),
            total("clutch_1v3_perdidos", "clutch"),
            total("clutch_1v4_ganados", "clutch"),
            total("clutch_1v4_perdidos", "clutch"),
            total("clutch_1v5_ganados", "clutch"),
            total("clutch_1v5_perdidos", "clutch"),
            valor("entry_ronda", "entradas", "pct", "neutral"),
            valor("entry_exito", "entradas", "pct", "alto"),
            valor("entry_exito_t", "entradas", "pct", "alto"),
            valor("entry_exito_ct", "entradas", "pct", "alto"),
            valor("entry_intentos", "entradas", "pct", "neutral"),
            valor("entry_intentos_t", "entradas", "pct", "neutral"),
            valor("entry_intentos_ct", "entradas", "pct", "neutral"),
            valor("premier_actual", "rango", "int", "alto"),
            valor("premier_mejor", "rango", "int", "alto"),
            valor("premier_victorias", "rango", "int", "neutral"),
            valor("competitive_victorias", "rango", "int", "neutral"),
            valor("wingman_victorias", "rango", "int", "neutral"));

    private static final Map<String, Campo> POR_CLAVE = new LinkedHashMap<>();

    static {
        CAMPOS.forEach(c -> POR_CLAVE.put(c.clave(), c));
    }

    private Capturas() {}

    public static List<String> claves() {
        return List.copyOf(POR_CLAVE.keySet());
    }

    /**
     * Solo los números que valen: claves conocidas, números finitos, no negativos y razonables (los porcentajes, hasta
     * 100), en el orden de {@link #CAMPOS}. Lo demás se descarta sin más (lo que lea Gemini puede traer cualquier cosa).
     */
    public static Map<String, Double> limpiar(Map<String, ?> datos) {
        Map<String, Double> limpios = new LinkedHashMap<>();
        if (datos == null) {
            return limpios;
        }
        for (Campo c : CAMPOS) {
            Double n = Estadisticas.numero(datos.get(c.clave()));
            if (n == null || n < 0 || n > MAXIMO || (c.formato().equals("pct") && n > 100)) {
                continue;
            }
            limpios.put(c.clave(), c.formato().equals("int") ? (double) Math.round(n) : Estadisticas.redondear(n, 2));
        }
        return limpios;
    }

    /** Un mapa de una captura: sus partidas o su winrate (lo que se vea). */
    public record MapaLeido(String mapa, Integer partidas, Double winrate) {}

    /** Los mapas que valen (con nombre y algún número), sin repetir y como mucho {@value #MAX_MAPAS}. */
    public static List<MapaLeido> limpiarMapas(List<MapaLeido> mapas) {
        Map<String, MapaLeido> porNombre = new LinkedHashMap<>();
        if (mapas == null) {
            return List.of();
        }
        for (MapaLeido m : mapas) {
            if (m == null || m.mapa() == null || m.mapa().isBlank() || porNombre.size() >= MAX_MAPAS) {
                continue;
            }
            String nombre = m.mapa().trim().toLowerCase();
            nombre = nombre.length() > 100 ? nombre.substring(0, 100) : nombre;
            Integer partidas = m.partidas() != null && m.partidas() >= 0 ? m.partidas() : null;
            Double winrate = m.winrate() != null && m.winrate() >= 0 && m.winrate() <= 100 ? m.winrate() : null;
            if (partidas == null && winrate == null) {
                continue;
            }
            MapaLeido previo = porNombre.get(nombre);
            porNombre.put(nombre, new MapaLeido(nombre,
                    partidas != null ? partidas : previo != null ? previo.partidas() : null,
                    winrate != null ? winrate : previo != null ? previo.winrate() : null));
        }
        return List.copyOf(porNombre.values());
    }

    // ─── Comparación ────────────────────────────────────────────────────────

    /** Una captura ya leída de la base: lo que hace falta para comparar. */
    public record Foto(long id, Instant cargadaEn, Map<String, Double> datos) {}

    /**
     * Un número en las dos capturas.
     *
     * @param mejora 1 si ha ido a mejor, -1 si a peor, 0 si no cambia o da igual (los totales siempre suben)
     */
    public record FilaEvolucion(
            String clave, String grupo, String tipo, String formato, Double antes, Double despues, Double diferencia,
            int mejora) {}

    /**
     * Cómo jugó entre las dos capturas, sacado de la diferencia de los totales. Cada número, null si falta en alguna
     * de las dos.
     *
     * @param kd kills nuevas entre muertes nuevas
     * @param hsPct headshots nuevos entre kills nuevas
     * @param adr daño nuevo entre rondas nuevas
     * @param clutch1v1 % de 1v1 ganados entre los nuevos
     */
    public record Tramo(
            int partidas, Integer victorias, Integer derrotas, Integer empates, Double winrate, Double kd, Double hsPct,
            Double adr, Double killsPartida, Double asistenciasPartida, Double clutch1v1) {}

    /**
     * Dos capturas comparadas.
     *
     * @param dias días entre una y otra
     * @param tramo cómo jugó entre medias, o null si no hay partidas nuevas (o los totales bajan: otra cuenta)
     */
    public record Evolucion(
            long desdeId, Instant desde, long hastaId, Instant hasta, int dias, List<FilaEvolucion> filas,
            Tramo tramo) {}

    public static Evolucion comparar(Foto antes, Foto despues) {
        List<FilaEvolucion> filas = new ArrayList<>();
        for (Campo c : CAMPOS) {
            Double a = antes.datos().get(c.clave());
            Double d = despues.datos().get(c.clave());
            if (a == null && d == null) {
                continue;
            }
            Double diferencia = a != null && d != null ? Estadisticas.redondear(d - a, 2) : null;
            int mejora = 0;
            if (diferencia != null && diferencia != 0 && c.tipo().equals("valor")) {
                mejora = switch (c.mejor()) {
                    case "alto" -> diferencia > 0 ? 1 : -1;
                    case "bajo" -> diferencia < 0 ? 1 : -1;
                    default -> 0;
                };
            }
            filas.add(new FilaEvolucion(c.clave(), c.grupo(), c.tipo(), c.formato(), a, d, diferencia, mejora));
        }
        int dias = (int) Duration.between(antes.cargadaEn(), despues.cargadaEn()).toDays();
        return new Evolucion(antes.id(), antes.cargadaEn(), despues.id(), despues.cargadaEn(), dias, filas,
                tramo(antes.datos(), despues.datos()));
    }

    /** Cómo jugó entre dos capturas, con la diferencia de los totales. Null sin partidas nuevas o si algo baja. */
    static Tramo tramo(Map<String, Double> antes, Map<String, Double> despues) {
        Double partidas = delta(antes, despues, "partidas");
        if (partidas == null || partidas <= 0) {
            return null;
        }
        for (Campo c : CAMPOS) {
            Double d = delta(antes, despues, c.clave());
            if (c.tipo().equals("total") && d != null && d < 0) {
                return null;
            }
        }
        Double victorias = delta(antes, despues, "victorias");
        Double derrotas = delta(antes, despues, "derrotas");
        Double empates = delta(antes, despues, "empates");
        Double kills = delta(antes, despues, "kills");
        Double muertes = delta(antes, despues, "muertes");
        Double asistencias = delta(antes, despues, "asistencias");
        Double headshots = delta(antes, despues, "headshots");
        Double dano = delta(antes, despues, "dano");
        Double rondas = delta(antes, despues, "rondas");
        Double ganados1v1 = delta(antes, despues, "clutch_1v1_ganados");
        Double perdidos1v1 = delta(antes, despues, "clutch_1v1_perdidos");
        return new Tramo(
                partidas.intValue(),
                entero(victorias),
                entero(derrotas),
                entero(empates),
                victorias != null && derrotas != null ? pct(victorias, victorias + derrotas) : null,
                cociente(kills, muertes, 2),
                kills != null && headshots != null ? pct(headshots, kills) : null,
                cociente(dano, rondas, 1),
                cociente(kills, partidas, 2),
                cociente(asistencias, partidas, 2),
                ganados1v1 != null && perdidos1v1 != null ? pct(ganados1v1, ganados1v1 + perdidos1v1) : null);
    }

    /**
     * Con qué captura se compara la última (para el Duende): la más reciente con al menos {@link #SEPARACION} más que
     * ella; si no hay, la primera. Null con menos de dos.
     *
     * @param fotos de más antigua a más reciente
     */
    public static Foto referencia(List<Foto> fotos) {
        if (fotos.size() < 2) {
            return null;
        }
        Foto ultima = fotos.getLast();
        Instant limite = ultima.cargadaEn().minus(SEPARACION);
        Foto elegida = fotos.getFirst();
        for (Foto f : fotos.subList(0, fotos.size() - 1)) {
            if (!f.cargadaEn().isAfter(limite)) {
                elegida = f;
            }
        }
        return elegida;
    }

    private static Double delta(Map<String, Double> antes, Map<String, Double> despues, String clave) {
        Double a = antes.get(clave);
        Double d = despues.get(clave);
        return a == null || d == null ? null : d - a;
    }

    private static Integer entero(Double n) {
        return n == null ? null : (int) Math.round(n);
    }

    private static Double cociente(Double arriba, Double abajo, int decimales) {
        return arriba == null || abajo == null || abajo <= 0 ? null : Estadisticas.redondear(arriba / abajo, decimales);
    }

    private static Double pct(double parte, double total) {
        return total <= 0 ? null : Estadisticas.redondear(100 * parte / total, 1);
    }

    /** Los números de una captura como dobles (en la base van como JSON). */
    public static Map<String, Double> numeros(Map<String, Object> datos) {
        Map<String, Double> n = new LinkedHashMap<>();
        datos.forEach((k, v) -> {
            Double d = Estadisticas.numero(v);
            if (d != null) {
                n.put(k, d);
            }
        });
        return n;
    }

    /** El campo de una clave, o null si no es de los que se guardan. */
    public static Campo campo(String clave) {
        return POR_CLAVE.get(Objects.requireNonNullElse(clave, ""));
    }
}
