package com.ttcl.games.stats;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.FilaSemana;
import com.ttcl.games.stats.Modelos.Hecho;
import com.ttcl.games.stats.Modelos.SemanaJuego;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Informe de cada partida (P10): lo que tiene de especial frente al historial del propio jugador. Solo mira las
 * partidas de antes, así que el informe de una partida no cambia aunque luego se jueguen más. Funciones puras.
 */
public final class Informes {

    /** Partidas seguidas con el mismo resultado para que sea una racha. */
    public static final int MIN_RACHA = 3;
    /** Partidas en los 30 días anteriores para hablar del mejor o el peor del mes (con menos, salía casi siempre). */
    public static final int MIN_PARTIDAS_MES = 10;
    /** Partidas de antes para hablar de su media o de un estreno. */
    public static final int MIN_HISTORIAL = 10;
    /** Cuánto (en proporción) tiene que separarse de su media la métrica principal. */
    public static final double DESVIO_MEDIA = 0.3;
    /** Partidas en la semana para poder ser el mejor o el peor (P11). */
    public static final int MIN_PARTIDAS_SEMANA = 3;
    static final Duration MES = Duration.ofDays(30);

    private Informes() {}

    /** Métricas de las que se buscan récords del mes: todas son mejores cuanto más altas. */
    static List<String> metricasRecord(Juego juego) {
        return switch (juego) {
            case CS2 -> List.of("kills_media", "adr", "hs_pct", "kd");
            case SMITE2 -> List.of("kills_media", "kda", "dano");
        };
    }

    /** La métrica que resume una partida, para compararla con su media: el ADR en CS2, el KDA en SMITE 2. */
    static String principal(Juego juego) {
        return juego == Juego.CS2 ? "adr" : "kda";
    }

    /**
     * Lo especial de {@code partida} frente a {@code historial} (las partidas del jugador en ese juego; puede incluir
     * la propia y las de después, que no cuentan): rachas, rachas rotas, rachas en ese mapa o dios, estrenos, el
     * mejor o el peor del mes y si se ha ido mucho de su media.
     */
    public static List<Hecho> hechos(Juego juego, FilaParticipacion partida, List<FilaParticipacion> historial) {
        List<FilaParticipacion> antes = historial.stream()
                .filter(f -> f.jugadaEn().isBefore(partida.jugadaEn()) && f.partidaId() != partida.partidaId())
                .sorted(Comparator.comparing(FilaParticipacion::jugadaEn).reversed())
                .toList();
        String clave = clave(juego, partida);
        List<Hecho> hechos = new ArrayList<>();

        if (partida.gano() != null) {
            boolean gano = partida.gano();
            int seguidas = 1 + iguales(antes, gano);
            if (seguidas >= MIN_RACHA) {
                hechos.add(new Hecho(gano ? "racha_victorias" : "racha_derrotas", null, null, null, seguidas, null));
            } else {
                int rota = iguales(antes, !gano);
                if (rota >= MIN_RACHA) {
                    hechos.add(new Hecho(gano ? "fin_racha_derrotas" : "fin_racha_victorias", null, null, null, rota,
                            null));
                }
            }
            if (clave != null) {
                List<FilaParticipacion> aqui = antes.stream().filter(f -> clave.equals(clave(juego, f))).toList();
                int enLaClave = 1 + iguales(aqui, gano);
                if (enLaClave >= MIN_RACHA) {
                    hechos.add(new Hecho(gano ? "racha_victorias_clave" : "racha_derrotas_clave", null, null, null,
                            enLaClave, clave));
                }
            }
        }

        if (clave != null && antes.size() >= MIN_HISTORIAL && antes.stream().noneMatch(f -> clave.equals(clave(juego, f)))) {
            hechos.add(new Hecho("estreno_clave", null, null, null, null, clave));
        }

        Instant haceUnMes = partida.jugadaEn().minus(MES);
        List<FilaParticipacion> mes = antes.stream().filter(f -> !f.jugadaEn().isBefore(haceUnMes)).toList();
        List<String> conRecord = new ArrayList<>();
        if (mes.size() >= MIN_PARTIDAS_MES) {
            for (String metrica : metricasRecord(juego)) {
                Double valor = Estadisticas.valorPartida(partida, metrica);
                List<Double> previos = mes.stream()
                        .map(f -> Estadisticas.valorPartida(f, metrica))
                        .filter(Objects::nonNull)
                        .toList();
                if (valor == null || previos.size() < MIN_PARTIDAS_MES) {
                    continue;
                }
                double max = previos.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                double min = previos.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                if (valor > max) {
                    hechos.add(new Hecho("mejor_mes", metrica, redondear(valor), redondear(max), previos.size(), null));
                    conRecord.add(metrica);
                } else if (valor < min) {
                    hechos.add(new Hecho("peor_mes", metrica, redondear(valor), redondear(min), previos.size(), null));
                    conRecord.add(metrica);
                }
            }
        }

        String metrica = principal(juego);
        Double valor = Estadisticas.valorPartida(partida, metrica);
        List<Double> previos = antes.stream().map(f -> Estadisticas.valorPartida(f, metrica)).filter(Objects::nonNull).toList();
        if (valor != null && previos.size() >= MIN_HISTORIAL && !conRecord.contains(metrica)) {
            double media = previos.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            if (media > 0 && valor >= media * (1 + DESVIO_MEDIA)) {
                hechos.add(new Hecho("sobre_media", metrica, redondear(valor), redondear(media), previos.size(), null));
            } else if (media > 0 && valor <= media * (1 - DESVIO_MEDIA)) {
                hechos.add(new Hecho("bajo_media", metrica, redondear(valor), redondear(media), previos.size(), null));
            }
        }
        return hechos;
    }

    /**
     * La semana de un juego (P11): cada jugador, el que más partidas primero, y el mejor y el peor entre los que tienen
     * al menos {@value #MIN_PARTIDAS_SEMANA} partidas (más winrate y, a igualdad, más K/D). Con uno solo, no hay peor.
     */
    public static SemanaJuego semana(Juego juego, int partidasEquipo, List<FilaSemana> filas) {
        Comparator<FilaSemana> orden = Comparator.comparing(FilaSemana::winrate)
                .thenComparing(f -> f.kd() == null ? 0 : f.kd());
        List<FilaSemana> candidatos = filas.stream()
                .filter(f -> f.partidas() >= MIN_PARTIDAS_SEMANA && f.winrate() != null)
                .toList();
        FilaSemana mejor = candidatos.stream().max(orden).orElse(null);
        FilaSemana peor = candidatos.size() < 2 ? null : candidatos.stream().min(orden).orElse(null);
        List<FilaSemana> ordenadas = filas.stream()
                .sorted(Comparator.comparingInt(FilaSemana::partidas).reversed().thenComparing(FilaSemana::nombre))
                .toList();
        return new SemanaJuego(juego, partidasEquipo, ordenadas, mejor, peor);
    }

    /** Cuántas de las primeras de {@code antes} (la más reciente primero) tienen ese resultado, hasta la primera que no. */
    private static int iguales(List<FilaParticipacion> antes, boolean gano) {
        int n = 0;
        for (FilaParticipacion f : antes) {
            if (!Boolean.valueOf(gano).equals(f.gano())) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Mapa (CS2) o dios (SMITE 2), como en el desglose. */
    static String clave(Juego juego, FilaParticipacion f) {
        Object valor = f.datos().get(juego.claveDesglose());
        String clave = valor != null ? String.valueOf(valor) : f.modo();
        return clave == null || clave.isBlank() ? null : clave;
    }

    private static double redondear(double valor) {
        return Estadisticas.redondear(valor, 2);
    }
}
