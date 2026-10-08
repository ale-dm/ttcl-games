package com.ttcl.games.duende;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.ResumenJuego;
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

    /** @param rol rol declarado en ese juego, o null: el Duende juzga cada métrica según lo que pide el rol */
    public record PeticionInsights(
            String lang,
            JugadorRef jugador,
            Juego juego,
            String rol,
            ResumenJuego resumen,
            ResumenJuego reciente,
            MediasEquipo equipo,
            List<FilaDesglose> desglose) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaInsights(List<Insight> insights) {}

    public record PeticionLote(List<PeticionInsights> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemLote(String slug, Juego juego, List<Insight> insights) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaLote(List<ItemLote> items) {}

    /** @param rol "usuario" o "duende" */
    public record Mensaje(String rol, String texto) {}

    public record JuegoContexto(
            Juego juego, String rol, ResumenJuego resumen, ResumenJuego reciente, MediasEquipo equipo,
            List<FilaDesglose> desglose) {}

    public record JugadorContexto(String slug, String nombre, List<JuegoContexto> juegos) {}

    public record PeticionChat(
            String lang, List<Mensaje> mensajes, List<String> foco, List<JugadorContexto> equipo, Juego juego) {}

    /** @param origen "gemini" o "reglas" (sin clave, sin cuota o si Gemini falla) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaChat(String respuesta, String origen, String modelo, List<String> sugerencias) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Salud(boolean ok, boolean gemini, String modelo) {}
}
