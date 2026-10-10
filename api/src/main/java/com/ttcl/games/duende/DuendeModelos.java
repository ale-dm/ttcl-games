package com.ttcl.games.duende;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Modelos.ResumenPeriodo;
import com.ttcl.games.stats.Modelos.SeguimientoConsejo;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import com.ttcl.games.stats.Periodo;
import java.util.List;

/** Contrato con el servicio Python del Duende (duende/app/modelos.py). Mismos nombres de campo, en camelCase. */
public final class DuendeModelos {

    private DuendeModelos() {}

    public record JugadorRef(String slug, String nombre) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Barra(String etiqueta, double valor, boolean tuyo) {}

    /**
     * Una recomendación.
     *
     * @param nivel "alto" (mejorar ya), "medio", "bien" (fortaleza) o "info"
     * @param formato cómo se muestran los valores de las barras: "pct", "dec" o "int"
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Insight(
            String id,
            String nivel,
            String metrica,
            String titulo,
            String texto,
            String consejo,
            List<Barra> barras,
            String formato) {}

    /**
     * @param rol rol declarado en ese juego, o null: el Duende juzga cada métrica según lo que pide el rol
     * @param sinergias con quién del equipo le va mejor o peor (y solo)
     * @param sesiones cómo le va según el orden en la sesión, lo que pasó en la anterior y la hora del día
     * @param seguimiento cómo han ido los consejos que se le dieron (vacío si se piden por periodo)
     */
    public record PeticionInsights(
            String lang,
            JugadorRef jugador,
            Juego juego,
            String rol,
            ResumenJuego resumen,
            ResumenJuego reciente,
            MediasEquipo equipo,
            List<FilaDesglose> desglose,
            Sinergias sinergias,
            Sesiones sesiones,
            List<SeguimientoConsejo> seguimiento) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaInsights(List<Insight> insights) {}

    public record PeticionLote(List<PeticionInsights> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemLote(String slug, Juego juego, List<Insight> insights) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaLote(List<ItemLote> items) {}

    /** @param rol "usuario" o "duende" */
    public record Mensaje(String rol, String texto) {}

    /**
     * Datos de un jugador en un juego para el chat, con todas sus partidas.
     *
     * @param periodos resumen de los últimos 7 y 30 días, los que tengan partidas ("¿cómo voy esta semana?")
     * @param seguimiento cómo han ido los consejos que se le dieron ("¿ha funcionado lo que me dijiste?")
     */
    public record JuegoContexto(
            Juego juego, String rol, ResumenJuego resumen, ResumenJuego reciente, MediasEquipo equipo,
            List<FilaDesglose> desglose, Sinergias sinergias, Sesiones sesiones, List<ResumenPeriodo> periodos,
            List<SeguimientoConsejo> seguimiento) {}

    public record JugadorContexto(String slug, String nombre, List<JuegoContexto> juegos) {}

    /** @param periodo el que se está viendo en la página (null o "todo": todas las partidas) */
    public record PeticionChat(
            String lang, List<Mensaje> mensajes, List<String> foco, List<JugadorContexto> equipo, Juego juego,
            Periodo periodo) {}

    /**
     * @param origen "gemini" o "reglas" (sin clave, sin cuota o si Gemini falla)
     * @param intencion de qué iba la pregunta según las reglas, conteste quien conteste (la web la devuelve al valorar)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaChat(
            String respuesta, String origen, String modelo, String intencion, List<String> sugerencias) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Salud(boolean ok, boolean gemini, String modelo) {}
}
