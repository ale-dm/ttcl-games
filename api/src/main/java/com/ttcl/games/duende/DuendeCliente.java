package com.ttcl.games.duende;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.ItemLote;
import com.ttcl.games.duende.DuendeModelos.PeticionChat;
import com.ttcl.games.duende.DuendeModelos.PeticionInsights;
import com.ttcl.games.duende.DuendeModelos.PeticionLote;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.duende.DuendeModelos.RespuestaInsights;
import com.ttcl.games.duende.DuendeModelos.RespuestaLote;
import com.ttcl.games.duende.DuendeModelos.Salud;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Cliente HTTP del servicio Python del Duende. Si no responde, lanza {@link DuendeNoDisponibleException}. */
@Component
public class DuendeCliente {

    private final RestClient http;
    private final RestClient httpRapido;

    public DuendeCliente(TtclProperties props) {
        this.http = cliente(props.duende().url(), Duration.ofMillis(props.duende().timeoutMs()));
        this.httpRapido = cliente(props.duende().url(), Duration.ofSeconds(2));
    }

    private static RestClient cliente(String url, Duration lectura) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(lectura);
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public List<Insight> insights(PeticionInsights peticion) {
        RespuestaInsights r = post("/v1/insights", peticion, RespuestaInsights.class);
        return r.insights() == null ? List.of() : r.insights();
    }

    public List<ItemLote> lote(List<PeticionInsights> peticiones) {
        if (peticiones.isEmpty()) {
            return List.of();
        }
        RespuestaLote r = post("/v1/insights/lote", new PeticionLote(peticiones), RespuestaLote.class);
        return r.items() == null ? List.of() : r.items();
    }

    public RespuestaChat chat(PeticionChat peticion) {
        return post("/v1/chat", peticion, RespuestaChat.class);
    }

    /** Estado del servicio, o vacío si no responde en 2 s. */
    public Optional<Salud> salud() {
        try {
            return Optional.ofNullable(httpRapido.get().uri("/health").retrieve().body(Salud.class));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    private <T> T post(String ruta, Object cuerpo, Class<T> tipo) {
        try {
            T respuesta = http.post()
                    .uri(ruta)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(tipo);
            if (respuesta == null) {
                throw new DuendeNoDisponibleException("El Duende devolvió una respuesta vacía", null);
            }
            return respuesta;
        } catch (RestClientException e) {
            throw new DuendeNoDisponibleException("El Duende no responde: " + e.getMessage(), e);
        }
    }
}
