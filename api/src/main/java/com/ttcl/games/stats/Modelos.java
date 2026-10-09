package com.ttcl.games.stats;

import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.juego.Juego;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Tipos de las estadísticas. Son los mismos que viajan en JSON a la web y al Duende (camelCase). */
public final class Modelos {

    private Modelos() {}

    /** Una participación ya leída de la base, sin entidades JPA: lo que necesitan los cálculos. */
    public record FilaParticipacion(
            long partidaId,
            Juego juego,
            Instant jugadaEn,
            String modo,
            Boolean gano,
            Integer kills,
            Integer muertes,
            Integer asistencias,
            Map<String, Object> datos) {

        public static FilaParticipacion de(Participacion p) {
            return new FilaParticipacion(
                    p.getPartida().getId(),
                    p.getJuego(),
                    p.getPartida().getJugadaEn(),
                    p.getPartida().getModo(),
                    p.getGano(),
                    p.getKills(),
                    p.getMuertes(),
                    p.getAsistencias(),
                    p.getDatos());
        }
    }

    /**
     * Resumen de un jugador en un juego.
     *
     * @param winrate porcentaje 0-100 sobre las partidas con resultado conocido
     * @param kd kills totales entre muertes totales
     * @param datosMedios medias de lo específico del juego (adr, hs_pct, entry_pct... o kda, dano_min, oro_min...)
     * @param forma últimos resultados, de más reciente a más antigua: V victoria, D derrota, ? sin dato
     */
    public record ResumenJuego(
            Juego juego,
            int partidas,
            int victorias,
            int derrotas,
            Double winrate,
            Double kd,
            Double killsMedia,
            Double muertesMedia,
            Double asistenciasMedia,
            Map<String, Double> datosMedios,
            String forma,
            Instant ultimaPartida) {}

    /** Media del resto del equipo en un juego (sin el jugador que se está mirando). */
    public record MediasEquipo(
            int jugadores,
            Double winrate,
            Double kd,
            Double killsMedia,
            Double muertesMedia,
            Double asistenciasMedia,
            Map<String, Double> datosMedios) {}

    /** Rendimiento por mapa (CS2) o por dios (SMITE 2). */
    public record FilaDesglose(String clave, int partidas, int victorias, Double winrate, Double kd) {}

    /** Un jugador del equipo en una partida. Mismo resultado que otro del equipo = mismo bando. */
    public record Presencia(String slug, String nombre, Boolean gano) {}

    /**
     * Cómo le va a un jugador con un compañero del equipo, o solo (entonces {@code slug} y {@code nombre} son null).
     *
     * @param partidasSin partidas sin ese compañero (en la fila de solo: con alguien del equipo), para comparar
     * @param winrateSin winrate en esas otras partidas, o null si no hay ninguna con resultado
     */
    public record FilaSinergia(
            String slug, String nombre, int partidas, int victorias, Double winrate, Double kd, int partidasSin,
            Double winrateSin) {}

    /**
     * Con quién juega mejor.
     *
     * @param solo partidas sin nadie del equipo, o null si hay menos de las mínimas
     * @param companeros uno por compañero con partidas suficientes juntos, el que más primero
     */
    public record Sinergias(FilaSinergia solo, List<FilaSinergia> companeros) {}

    public record Miembro(String slug, String nombre) {}

    /** Dos o tres del equipo y cómo les va cuando juegan juntos en el mismo bando (aunque haya alguien más). */
    public record Grupo(List<Miembro> jugadores, int partidas, int victorias, Double winrate) {}

    /** Un punto de la gráfica de partidas. */
    public record PuntoSerie(
            long partidaId, Instant fecha, Boolean gano, Integer kills, Integer muertes, Integer asistencias,
            String clave) {}

    /**
     * Una métrica comparada entre dos jugadores.
     *
     * @param mejor "alto", "bajo" o "neutral": hacia dónde gana la métrica
     * @param ventaja "a", "b" o null si no hay datos de ambos o empatan
     */
    public record FilaComparacion(String metrica, Double a, Double b, String mejor, String ventaja) {}
}
