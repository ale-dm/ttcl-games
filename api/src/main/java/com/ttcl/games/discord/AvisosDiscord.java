package com.ttcl.games.discord;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.servicio.Vistas.Novedad;
import com.ttcl.games.servicio.Vistas.Novedades;
import com.ttcl.games.servicio.Vistas.PartidaVista;
import com.ttcl.games.servicio.Vistas.ResumenSemanal;
import com.ttcl.games.stats.Modelos.FilaSemana;
import com.ttcl.games.stats.Modelos.SemanaJuego;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Lleva al Duende a Discord con el webhook (P11): las partidas nuevas tras cada sincronización, con lo que dice de
 * cada una, y el resumen de la semana. Sin webhook no hace nada.
 */
@Component
public class AvisosDiscord {

    private static final Logger log = LoggerFactory.getLogger(AvisosDiscord.class);
    /** Algo de margen bajo el máximo de Discord. */
    static final int MAX_TROZO = 1900;

    private static final Map<String, Map<String, String>> TEXTOS = Map.of(
            "es", Map.of(
                    "nuevas", "🎮 **Partidas nuevas**",
                    "semana", "📅 **Resumen de la semana**",
                    "victoria", "✅ Victoria",
                    "derrota", "❌ Derrota",
                    "mejor", "mejor",
                    "peor", "peor",
                    "partidas", "partidas"),
            "en", Map.of(
                    "nuevas", "🎮 **New matches**",
                    "semana", "📅 **Weekly recap**",
                    "victoria", "✅ Win",
                    "derrota", "❌ Loss",
                    "mejor", "best",
                    "peor", "worst",
                    "partidas", "matches"));

    private final DiscordWebhook webhook;
    private final DuendeServicio duende;
    private final String lang;

    public AvisosDiscord(DiscordWebhook webhook, DuendeServicio duende, TtclProperties props) {
        this.webhook = webhook;
        this.duende = duende;
        this.lang = DuendeServicio.idioma(props.discord() == null ? null : props.discord().lang());
    }

    /** Publica las partidas guardadas desde {@code desde}. Devuelve cuántas ha publicado. */
    public int publicarNovedades(Instant desde) {
        if (!webhook.configurado()) {
            return 0;
        }
        Novedades novedades = duende.novedades(desde, lang);
        if (novedades.partidas().isEmpty()) {
            return 0;
        }
        List<String> bloques = new ArrayList<>(List.of(texto("nuevas")));
        novedades.partidas().forEach(n -> bloques.add(linea(n)));
        enviar(bloques);
        return novedades.partidas().size();
    }

    /** El resumen de los últimos 7 días, los lunes (o cuando diga {@code DISCORD_RESUMEN_SEMANAL}). */
    @Scheduled(cron = "${ttcl.discord.resumen-semanal:0 0 10 * * MON}", zone = "${ttcl.zona-horaria:Europe/Madrid}")
    public void publicarSemana() {
        if (!webhook.configurado()) {
            return;
        }
        ResumenSemanal resumen = duende.semana(lang);
        if (resumen.juegos().isEmpty()) {
            return;
        }
        List<String> bloques = new ArrayList<>(List.of(texto("semana")));
        if (resumen.texto() != null) {
            bloques.add(resumen.texto());
        } else {
            // Sin Duende, al menos los números.
            resumen.juegos().forEach(j -> bloques.add(lineaSemana(j)));
        }
        enviar(bloques);
    }

    /** "**Jugador 1** · Counter-Strike 2 · de_nuke · ❌ Derrota · 18/20/3", y debajo lo que dice el Duende. */
    String linea(Novedad n) {
        PartidaVista p = n.partida();
        Object clave = p.datos().getOrDefault(p.juego().claveDesglose(), p.modo());
        StringBuilder linea = new StringBuilder("**").append(n.jugador().nombre()).append("** · ").append(p.juego().nombre());
        if (clave != null) {
            linea.append(" · ").append(clave);
        }
        if (p.gano() != null) {
            linea.append(" · ").append(texto(p.gano() ? "victoria" : "derrota"));
        }
        linea.append(" · ").append(cifra(p.kills())).append('/').append(cifra(p.muertes())).append('/')
                .append(cifra(p.asistencias()));
        if (p.comentario() != null) {
            linea.append("\n> ").append(p.comentario());
        }
        return linea.toString();
    }

    private String lineaSemana(SemanaJuego j) {
        StringBuilder linea = new StringBuilder("**").append(j.juego().nombre()).append("**: ").append(j.partidas())
                .append(' ').append(texto("partidas"));
        if (j.mejor() != null && j.peor() == null) {
            // Si solo llega uno, no es "el mejor" de nadie.
            linea.append(" · ").append(fila(j.mejor()));
        } else if (j.mejor() != null) {
            linea.append(" · ").append(texto("mejor")).append(": ").append(fila(j.mejor()))
                    .append(" · ").append(texto("peor")).append(": ").append(fila(j.peor()));
        }
        return linea.toString();
    }

    private static String fila(FilaSemana f) {
        return String.format(Locale.ROOT, "%s (%d/%d)", f.nombre(), f.victorias(), f.partidas());
    }

    private static String cifra(Integer n) {
        return n == null ? "—" : String.valueOf(n);
    }

    private String texto(String clave) {
        return TEXTOS.get(lang).get(clave);
    }

    private void enviar(List<String> bloques) {
        try {
            for (String trozo : trocear(bloques, MAX_TROZO)) {
                webhook.enviar(trozo);
            }
        } catch (RestClientException e) {
            log.warn("No se pudo publicar en Discord: {}", e.getMessage());
        }
    }

    /** Junta los bloques en mensajes de hasta {@code max} caracteres, sin partir un bloque si cabe entero en uno. */
    static List<String> trocear(List<String> bloques, int max) {
        List<String> trozos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String bloque : bloques) {
            String cabe = bloque.length() > max ? bloque.substring(0, max - 1) + "…" : bloque;
            if (!actual.isEmpty() && actual.length() + 1 + cabe.length() > max) {
                trozos.add(actual.toString());
                actual.setLength(0);
            }
            if (!actual.isEmpty()) {
                actual.append('\n');
            }
            actual.append(cabe);
        }
        if (!actual.isEmpty()) {
            trozos.add(actual.toString());
        }
        return trozos;
    }
}
