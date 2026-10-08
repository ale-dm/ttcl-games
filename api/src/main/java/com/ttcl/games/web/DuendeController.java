package com.ttcl.games.web;

import com.ttcl.games.duende.DuendeModelos.Mensaje;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.juego.Juego;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/duende")
public class DuendeController {

    private final DuendeServicio duende;

    public DuendeController(DuendeServicio duende) {
        this.duende = duende;
    }

    public record MensajeWeb(
            @NotBlank @Pattern(regexp = "usuario|duende") String rol, @NotBlank @Size(max = 2000) String texto) {}

    /**
     * Pregunta al Duende.
     *
     * @param foco slugs de los jugadores de los que va la conversación (0, 1 o 2)
     * @param juego juego seleccionado en la página, si hay
     */
    public record PeticionChatWeb(
            String lang, @NotEmpty @Size(max = 30) List<@Valid MensajeWeb> mensajes, @Size(max = 2) List<String> foco,
            Juego juego) {}

    @PostMapping("/chat")
    public RespuestaChat chat(@Valid @RequestBody PeticionChatWeb peticion) {
        List<Mensaje> mensajes = peticion.mensajes().stream().map(m -> new Mensaje(m.rol(), m.texto())).toList();
        return duende.chat(peticion.lang(), mensajes, peticion.foco(), peticion.juego());
    }
}
