package com.ttcl.games.analisis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Contrato con el trabajador de análisis de demos (analisis/app/modelos.py). Mismos nombres de campo, en camelCase. */
public final class AnalisisModelos {

    private AnalisisModelos() {}

    /**
     * Un jugador del equipo que puede estar en la partida: se le busca por steamid y, si no, por su nick de FACEIT.
     *
     * @param id el slug: así viene en la respuesta
     */
    public record JugadorBuscado(String id, String steamId, String nick) {}

    /** La URL de la demo o el nombre de un fichero de la carpeta de demos: una de las dos. */
    public record PeticionAnalisis(String url, String archivo, List<JugadorBuscado> jugadores) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RondaJugador(
            int ronda,
            String lado,
            Boolean gano,
            int kills,
            int asistencias,
            int asistenciasFlash,
            int dano,
            int danoUtilidad,
            boolean murio,
            Double muerteX,
            Double muerteY,
            String muerteZona,
            boolean tradeado,
            int trades,
            String apertura,
            Integer equipamiento,
            String compra,
            boolean kast) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JugadorAnalizado(String id, String steamId, List<RondaJugador> rondas) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RondaPartida(int numero, String ganador, String motivo) {}

    /** Dónde murió alguien de la partida, sin decir quién. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MuerteAnonima(double x, double y, String zona) {}

    /** @param jugadores solo los buscados que estaban en la partida */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaAnalisis(
            String mapa, List<RondaPartida> rondas, List<JugadorAnalizado> jugadores, List<MuerteAnonima> muertes) {}
}
