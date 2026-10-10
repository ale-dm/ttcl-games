package com.ttcl.games.web;

import com.ttcl.games.duende.DuendeModelos.Mensaje;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.Valoraciones;
import com.ttcl.games.servicio.Valoraciones.VotoConsejo;
import com.ttcl.games.servicio.Valoraciones.VotoRespuesta;
import com.ttcl.games.servicio.Vistas.ResumenValoraciones;
import com.ttcl.games.stats.Periodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/duende")
public class DuendeController {

    private final DuendeServicio duende;
    private final Valoraciones valoraciones;

    public DuendeController(DuendeServicio duende, Valoraciones valoraciones) {
        this.duende = duende;
        this.valoraciones = valoraciones;
    }

    public record MensajeWeb(
            @NotBlank @Pattern(regexp = "usuario|duende") String rol, @NotBlank @Size(max = 2000) String texto) {}

    /**
     * Pregunta al Duende.
     *
     * @param foco slugs de los jugadores de los que va la conversación (0, 1 o 2)
     * @param juego juego seleccionado en la página, si hay
     * @param periodo periodo seleccionado en la página (7d, 30d o todo), si hay
     */
    public record PeticionChatWeb(
            String lang, @NotEmpty @Size(max = 30) List<@Valid MensajeWeb> mensajes, @Size(max = 2) List<String> foco,
            Juego juego, Periodo periodo) {}

    @PostMapping("/chat")
    public RespuestaChat chat(@Valid @RequestBody PeticionChatWeb peticion) {
        List<Mensaje> mensajes = peticion.mensajes().stream().map(m -> new Mensaje(m.rol(), m.texto())).toList();
        return duende.chat(peticion.lang(), mensajes, peticion.foco(), peticion.juego(), peticion.periodo());
    }

    /** 👍 (1), 👎 (-1) o quitar el voto (0) a una recomendación del panel de un jugador. */
    @PutMapping("/valoraciones/consejo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void valorarConsejo(@Valid @RequestBody VotoConsejo voto) {
        valoraciones.votarConsejo(voto);
    }

    /** 👍 (1), 👎 (-1) o quitar el voto (0) a una respuesta del chat. */
    @PutMapping("/valoraciones/respuesta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void valorarRespuesta(@Valid @RequestBody VotoRespuesta voto) {
        valoraciones.votarRespuesta(voto);
    }

    /** Para revisar: las recomendaciones y los tipos de pregunta peor valorados y las últimas valoraciones negativas. */
    @GetMapping("/valoraciones")
    public ResumenValoraciones valoraciones() {
        return valoraciones.resumen();
    }
}
