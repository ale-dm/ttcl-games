package com.ttcl.games.carga;

import com.ttcl.games.dominio.Captura;
import com.ttcl.games.dominio.Captura.MapaCaptura;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Repositorios.CapturaRepo;
import com.ttcl.games.dominio.Ronda;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Capturas;
import com.ttcl.games.stats.Modelos.FilaRonda;
import com.ttcl.games.stats.Modelos.MetricasRondas;
import com.ttcl.games.stats.Rondas;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Capturas de csstats de ejemplo (P13): como si cada jugador de CS2 hubiera subido una captura de su perfil hace 40, 26,
 * 12 y 1 días. Salen de sus propias partidas hasta ese día (y de las rondas de sus demos para el rating y el KAST), así
 * que al comparar dos, el tramo cuadra con lo que jugó entre medias. No usa números al azar: no cambia nada más.
 */
final class CapturasDemo {

    static final List<Integer> DIAS = List.of(40, 26, 12, 1);
    /** Premier de partida de cada uno (sube o baja según lo que gane después). */
    private static final Map<String, Integer> PREMIER = Map.of("j1", 11_200, "j2", 9_400, "j3", 8_100);

    private final CapturaRepo capturas;
    private final ZoneId zona;

    CapturasDemo(CapturaRepo capturas, ZoneId zona) {
        this.capturas = capturas;
        this.zona = zona;
    }

    /** Devuelve cuántas capturas ha guardado. */
    int generar(List<Participacion> participaciones, List<Ronda> rondas, LocalDate hoy) {
        Map<Jugador, List<Participacion>> porJugador = participaciones.stream()
                .filter(p -> p.getJuego() == Juego.CS2)
                .collect(Collectors.groupingBy(Participacion::getJugador, LinkedHashMap::new, Collectors.toList()));
        int total = 0;
        for (var e : porJugador.entrySet()) {
            Jugador j = e.getKey();
            List<FilaRonda> suyas = rondas.stream()
                    .filter(r -> r.getJugador().getId().equals(j.getId()))
                    .map(FilaRonda::de)
                    .toList();
            int mejor = 0;
            for (int dias : DIAS) {
                Instant cuando = hoy.minusDays(dias).atTime(22, 30).atZone(zona).toInstant();
                List<Participacion> antes = e.getValue().stream()
                        .filter(p -> p.getPartida().getJugadaEn().isBefore(cuando))
                        .toList();
                if (antes.isEmpty()) {
                    continue;
                }
                Map<String, Object> datos = datos(j.getSlug(), antes,
                        suyas.stream().filter(r -> r.jugadaEn().isBefore(cuando)).toList());
                mejor = Math.max(mejor, ((Number) datos.get("premier_actual")).intValue());
                datos.put("premier_mejor", mejor);
                capturas.save(new Captura(j, Juego.CS2, Capturas.CSSTATS, cuando, datos, Captura.MANO, mapas(antes)));
                total++;
            }
        }
        return total;
    }

    private static Map<String, Object> datos(String slug, List<Participacion> antes, List<FilaRonda> rondas) {
        int victorias = 0;
        int derrotas = 0;
        long kills = 0;
        long muertes = 0;
        long asistencias = 0;
        long headshots = 0;
        long rondasTotal = 0;
        long dano = 0;
        long clutchIntentos = 0;
        long clutchGanados = 0;
        long entryIntentos = 0;
        long entryGanados = 0;
        for (Participacion p : antes) {
            if (Boolean.TRUE.equals(p.getGano())) {
                victorias++;
            } else if (Boolean.FALSE.equals(p.getGano())) {
                derrotas++;
            }
            int k = valor(p.getKills());
            kills += k;
            muertes += valor(p.getMuertes());
            asistencias += valor(p.getAsistencias());
            headshots += Math.round(k * numero(p.getDatos().get("hs_pct")) / 100);
            int r = (int) numero(p.getDatos().get("rondas"));
            rondasTotal += r;
            dano += Math.round(numero(p.getDatos().get("adr")) * r);
            clutchIntentos += (long) numero(p.getDatos().get("clutch_intentos"));
            clutchGanados += (long) numero(p.getDatos().get("clutch_ganados"));
            entryIntentos += (long) numero(p.getDatos().get("entry_intentos"));
            entryGanados += (long) numero(p.getDatos().get("entry_ganados"));
        }
        long clutchPerdidos = clutchIntentos - clutchGanados;
        long ganados1v1 = Math.round(clutchGanados * 0.75);
        long perdidos1v1 = Math.round(clutchPerdidos * 0.35);
        MetricasRondas m = Rondas.metricas(rondas);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("rating", m.rating());
        d.put("kd", redondear((double) kills / Math.max(1, muertes), 2));
        d.put("winrate", redondear(100.0 * victorias / Math.max(1, victorias + derrotas), 1));
        d.put("hs_pct", redondear(100.0 * headshots / Math.max(1, kills), 1));
        d.put("adr", (double) Math.round((double) dano / Math.max(1, rondasTotal)));
        d.put("kast", m.kast());
        d.put("partidas", antes.size());
        d.put("victorias", victorias);
        d.put("derrotas", derrotas);
        d.put("empates", antes.size() - victorias - derrotas);
        d.put("kills", kills);
        d.put("muertes", muertes);
        d.put("asistencias", asistencias);
        d.put("headshots", headshots);
        d.put("dano", dano);
        d.put("rondas", rondasTotal);
        d.put("clutch_pct", redondear(100.0 * clutchGanados / Math.max(1, clutchIntentos), 1));
        d.put("clutch_1v1_ganados", ganados1v1);
        d.put("clutch_1v1_perdidos", perdidos1v1);
        d.put("clutch_1v2_ganados", clutchGanados - ganados1v1);
        d.put("clutch_1v2_perdidos", clutchPerdidos - perdidos1v1);
        d.put("entry_ronda", redondear(100.0 * entryGanados / Math.max(1, rondasTotal), 1));
        d.put("entry_exito", redondear(100.0 * entryGanados / Math.max(1, entryIntentos), 1));
        d.put("entry_intentos", redondear(100.0 * entryIntentos / Math.max(1, rondasTotal), 1));
        d.put("premier_actual", PREMIER.getOrDefault(slug, 9_000) + 25 * (victorias - derrotas));
        d.put("premier_victorias", victorias / 2);
        d.values().removeIf(v -> v == null);
        return d;
    }

    /** Los cuatro mapas más jugados con sus partidas, y los cuatro de más winrate (con 3 partidas o más) con el suyo. */
    private static List<MapaCaptura> mapas(List<Participacion> antes) {
        Map<String, List<Participacion>> porMapa = antes.stream()
                .filter(p -> p.getPartida().getModo() != null)
                .collect(Collectors.groupingBy(p -> p.getPartida().getModo()));
        List<MapaCaptura> mapas = new ArrayList<>();
        porMapa.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Participacion>> x) -> x.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(4)
                .forEach(x -> mapas.add(new MapaCaptura(x.getKey(), x.getValue().size(), null)));
        porMapa.entrySet().stream()
                .filter(x -> x.getValue().size() >= 3)
                .map(x -> new MapaCaptura(x.getKey(), null, redondear(100.0
                        * x.getValue().stream().filter(p -> Boolean.TRUE.equals(p.getGano())).count()
                        / x.getValue().size(), 0)))
                .sorted(Comparator.comparing(MapaCaptura::winrate).reversed().thenComparing(MapaCaptura::mapa))
                .limit(4)
                .forEach(mapas::add);
        // Un mapa puede estar en las dos listas: en una fila, con las dos cosas.
        Map<String, MapaCaptura> juntos = new LinkedHashMap<>();
        for (MapaCaptura m : mapas) {
            MapaCaptura previo = juntos.get(m.mapa());
            juntos.put(m.mapa(), previo == null ? m : new MapaCaptura(m.mapa(),
                    previo.partidas() != null ? previo.partidas() : m.partidas(),
                    previo.winrate() != null ? previo.winrate() : m.winrate()));
        }
        return List.copyOf(juntos.values());
    }

    private static int valor(Integer n) {
        return n == null ? 0 : n;
    }

    private static double numero(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double redondear(double valor, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(valor * f) / f;
    }
}
