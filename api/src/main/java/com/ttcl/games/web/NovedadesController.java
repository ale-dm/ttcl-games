package com.ttcl.games.web;

import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.servicio.Vistas.Novedades;
import com.ttcl.games.servicio.Vistas.ResumenSemanal;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que pasa en el equipo, para llevarlo fuera de la web (P11): el bot de Discord del grupo lo puede consultar, y la
 * API también lo publica sola en un canal si hay webhook ({@code DISCORD_WEBHOOK_URL}).
 */
@RestController
@RequestMapping("/api/novedades")
public class NovedadesController {

    private final DuendeServicio duende;

    public NovedadesController(DuendeServicio duende) {
        this.duende = duende;
    }

    /**
     * Partidas guardadas desde {@code desde} (un instante ISO, sin incluirlo; por defecto, el último día), con lo que
     * dice el Duende de cada una. Para la siguiente vez, pedir desde el {@code hasta} de la respuesta.
     */
    @GetMapping
    public Novedades novedades(
            @RequestParam(required = false) Instant desde, @RequestParam(defaultValue = "es") String lang) {
        return duende.novedades(desde, lang);
    }

    /** Los últimos 7 días de cada juego: cada jugador, el mejor y el peor, y lo que dice el Duende. */
    @GetMapping("/semana")
    public ResumenSemanal semana(@RequestParam(defaultValue = "es") String lang) {
        return duende.semana(lang);
    }
}
