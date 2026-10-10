package com.ttcl.games.servicio;

import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.JugadorRef;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaComparacion;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.Grupo;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.PuntoSerie;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Lo que devuelve la API a la web. */
public final class Vistas {

    private Vistas() {}

    /**
     * @param rol rol declarado en ese juego ("entry", "soporte", "guardian"...) o null si no lo ha dicho
     * @param nivel nivel de FACEIT (1 a 10) en la última sincronización, o null (SMITE 2, o aún sin sincronizar)
     * @param elo ELO de FACEIT en la última sincronización, o null
     */
    public record CuentaVista(Juego juego, String nick, String rol, Integer nivel, Integer elo, Instant ultimaSync) {}

    public record JugadorVista(
            String slug, String nombre, boolean demo, List<CuentaVista> cuentas, List<ResumenJuego> resumenes) {}

    /** Lo primero que diría el Duende de un jugador, para la tarjeta del equipo. */
    public record ConsejoBreve(Juego juego, String nivel, String titulo) {}

    public record TarjetaJugador(
            String slug,
            String nombre,
            boolean demo,
            List<CuentaVista> cuentas,
            List<ResumenJuego> resumenes,
            ConsejoBreve consejo) {}

    public record BusquedaVista(String slug, String nombre, List<CuentaVista> cuentas) {}

    /**
     * Detalle de un jugador en un juego.
     *
     * @param reciente resumen de las últimas {@value com.ttcl.games.stats.Estadisticas#PARTIDAS_RECIENTES} partidas
     * @param equipo media del resto del equipo en ese juego (null si nadie más lo juega)
     */
    public record DetalleJuego(
            Juego juego,
            ResumenJuego resumen,
            ResumenJuego reciente,
            MediasEquipo equipo,
            List<FilaDesglose> desglose,
            List<PuntoSerie> serie) {}

    public record PartidaVista(
            long partidaId,
            Juego juego,
            Instant jugadaEn,
            String modo,
            Boolean gano,
            Integer kills,
            Integer muertes,
            Integer asistencias,
            Map<String, Object> datos,
            List<String> companeros) {}

    public record PaginaPartidas(List<PartidaVista> items, int total) {}

    /**
     * Partidas que pasan un filtro (P9: lo que consulta el chat cuando pregunta algo que no está en los resúmenes).
     *
     * @param resumen de todas las que pasan el filtro (0 partidas si no hay ninguna)
     * @param equipo media del resto del equipo con el mismo filtro (null si nadie más tiene partidas así)
     * @param partidas las más recientes, hasta el límite pedido
     */
    public record ConsultaPartidas(
            Juego juego, ResumenJuego resumen, MediasEquipo equipo, List<PartidaVista> partidas) {}

    /** Si alguno no tiene partidas del juego, su resumen es null y {@code filas} está vacía. */
    public record Comparacion(
            JugadorRef a, JugadorRef b, Juego juego, ResumenJuego resumenA, ResumenJuego resumenB,
            List<FilaComparacion> filas) {}

    public record FilaRanking(String slug, String nombre, ResumenJuego resumen) {}

    public record Ranking(Juego juego, List<String> metricas, List<FilaRanking> filas) {}

    /** Dúos y tríos de un juego, el mejor winrate primero (con un mínimo de partidas juntos). */
    public record GruposJuego(Juego juego, List<Grupo> duos, List<Grupo> trios) {}

    public record EstadoDuende(boolean disponible, boolean gemini, String modelo) {}

    public record Estado(Instant ultimaSync, boolean demo, Map<String, Boolean> fuentes, EstadoDuende duende) {}

    public record ConsejosVista(boolean disponible, List<Insight> insights) {}

    /**
     * Votos de una recomendación (por su id, de todos los jugadores) o de las respuestas del chat a un tipo de pregunta.
     *
     * @param clave id de la recomendación, o intención de la pregunta según las reglas (null si no se sabe)
     */
    public record GrupoValoraciones(String origen, String clave, int positivos, int negativos) {}

    /** Una valoración con lo que se vio, para leerla al revisar. */
    public record ValoracionVista(
            String tipo, String clave, String origen, String modelo, String intencion, String nivel, String jugador,
            Juego juego, String lang, String pregunta, String texto, Instant votadaEn) {}

    /**
     * Para revisar las valoraciones (P7): totales, las recomendaciones y los tipos de pregunta peor valorados primero y
     * las últimas negativas con lo que se vio.
     */
    public record ResumenValoraciones(
            int positivos, int negativos, List<GrupoValoraciones> consejos, List<GrupoValoraciones> respuestas,
            List<ValoracionVista> negativas) {}
}
