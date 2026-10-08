package com.ttcl.games.servicio;

import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.JugadorRef;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaComparacion;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.PuntoSerie;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Lo que devuelve la API a la web. */
public final class Vistas {

    private Vistas() {}

    public record CuentaVista(Juego juego, String nick, Instant ultimaSync) {}

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

    /** Si alguno no tiene partidas del juego, su resumen es null y {@code filas} está vacía. */
    public record Comparacion(
            JugadorRef a, JugadorRef b, Juego juego, ResumenJuego resumenA, ResumenJuego resumenB,
            List<FilaComparacion> filas) {}

    public record FilaRanking(String slug, String nombre, ResumenJuego resumen) {}

    public record Ranking(Juego juego, List<String> metricas, List<FilaRanking> filas) {}

    public record EstadoDuende(boolean disponible, boolean gemini, String modelo) {}

    public record Estado(Instant ultimaSync, boolean demo, Map<String, Boolean> fuentes, EstadoDuende duende) {}

    public record ConsejosVista(boolean disponible, List<Insight> insights) {}
}
