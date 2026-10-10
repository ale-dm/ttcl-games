package com.ttcl.games.analisis;

import com.ttcl.games.analisis.AnalisisModelos.PeticionAnalisis;
import com.ttcl.games.analisis.AnalisisModelos.RespuestaAnalisis;
import com.ttcl.games.config.TtclProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP del trabajador de análisis de demos (P12). Si dice que la demo no vale (422), lanza
 * {@link DemoNoValidaException}; si no responde o no puede ahora, {@link AnalisisNoDisponibleException}.
 */
@Component
public class AnalisisCliente {

    private final RestClient http;
    private final boolean configurado;

    public AnalisisCliente(TtclProperties props) {
        TtclProperties.Analisis cfg = props.analisis();
        this.configurado = cfg != null && cfg.configurado();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(cfg == null ? 600_000 : cfg.timeoutMs()));
        this.http = RestClient.builder().baseUrl(configurado ? cfg.url() : "http://localhost").requestFactory(factory).build();
    }

    public boolean configurado() {
        return configurado;
    }

    public RespuestaAnalisis analizar(PeticionAnalisis peticion) {
        try {
            RespuestaAnalisis r = http.post()
                    .uri("/v1/analizar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(peticion)
                    .retrieve()
                    .body(RespuestaAnalisis.class);
            if (r == null) {
                throw new AnalisisNoDisponibleException("El trabajador de análisis devolvió una respuesta vacía", null);
            }
            return r;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                throw new DemoNoValidaException(mensaje(e));
            }
            throw new AnalisisNoDisponibleException("El trabajador de análisis respondió " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AnalisisNoDisponibleException("El trabajador de análisis no responde: " + e.getMessage(), e);
        }
    }

    private static String mensaje(HttpClientErrorException e) {
        try {
            Map<?, ?> cuerpo = e.getResponseBodyAs(Map.class);
            if (cuerpo != null && cuerpo.get("error") instanceof String texto) {
                return texto;
            }
        } catch (RuntimeException ignorada) {
            // Sin cuerpo legible: vale el genérico.
        }
        return "La demo no se puede analizar.";
    }

    /** La demo no vale (no existe, ha caducado, está rota...): no se vuelve a intentar. */
    public static class DemoNoValidaException extends RuntimeException {
        public DemoNoValidaException(String mensaje) {
            super(mensaje);
        }
    }

    /** El trabajador no responde o no ha podido descargar la demo ahora: se intenta más tarde. */
    public static class AnalisisNoDisponibleException extends RuntimeException {
        public AnalisisNoDisponibleException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
