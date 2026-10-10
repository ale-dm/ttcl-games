package com.ttcl.games.discord;

import com.ttcl.games.config.TtclProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Publica mensajes en un canal de Discord con su webhook (P11), firmados como "El Duende". */
@Component
public class DiscordWebhook {

    /** Discord no admite mensajes más largos. */
    public static final int MAX_MENSAJE = 2000;

    private final TtclProperties.Discord config;
    private final RestClient http;

    public DiscordWebhook(TtclProperties props) {
        this.config = props.discord();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configurado() {
        return config != null && config.configurado();
    }

    /** Un mensaje (de hasta {@value #MAX_MENSAJE} caracteres). Sin menciones: un nick raro no avisa a nadie. */
    public void enviar(String contenido) {
        http.post()
                .uri(config.webhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", "El Duende",
                        "content", contenido,
                        "allowed_mentions", Map.of("parse", List.of())))
                .retrieve()
                .toBodilessEntity();
    }
}
