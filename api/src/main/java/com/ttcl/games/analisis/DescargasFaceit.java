package com.ttcl.games.analisis;

import com.ttcl.games.config.TtclProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * API de descargas de FACEIT (P12): las demos están en un almacén privado y para bajarlas hace falta una URL firmada
 * ({@code POST /download/v2/demos/download} con la URL de la demo). Pide un token propio con permiso de descargas, que
 * FACEIT da tras rellenar su formulario (https://fce.gg/downloads-api-application); la clave de la Data API no vale.
 */
@Component
public class DescargasFaceit {

    private final RestClient http;
    private final boolean configurada;

    public DescargasFaceit(TtclProperties props) {
        TtclProperties.Faceit faceit = props.faceit();
        this.configurada = faceit != null && faceit.descargas();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        String base = faceit == null || faceit.downloadsBase() == null ? "" : faceit.downloadsBase().replaceAll("/$", "");
        this.http = RestClient.builder()
                .baseUrl(base.isBlank() ? "https://open.faceit.com/download/v2" : base)
                .defaultHeader("Authorization", "Bearer " + (configurada ? faceit.downloadsToken() : ""))
                .requestFactory(factory)
                .build();
    }

    public boolean configurada() {
        return configurada;
    }

    /** La URL firmada para descargar la demo, o vacío si no hay token o FACEIT no la da. */
    public Optional<String> urlFirmada(String urlDemo) {
        if (!configurada || urlDemo == null) {
            return Optional.empty();
        }
        try {
            Object r = http.post()
                    .uri("/demos/download")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("resource_url", urlDemo))
                    .retrieve()
                    .body(Object.class);
            return Optional.ofNullable(firmada(r));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    /** {@code payload.download_url} de la respuesta. Pública para los tests. */
    public static String firmada(Object respuesta) {
        if (respuesta instanceof Map<?, ?> m && m.get("payload") instanceof Map<?, ?> p
                && p.get("download_url") instanceof String url && url.startsWith("http")) {
            return url;
        }
        return null;
    }
}
