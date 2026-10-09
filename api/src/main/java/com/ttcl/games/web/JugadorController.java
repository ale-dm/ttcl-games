package com.ttcl.games.web;

import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.EquipoServicio;
import com.ttcl.games.servicio.Vistas.ConsejosVista;
import com.ttcl.games.servicio.Vistas.DetalleJuego;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.PaginaPartidas;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jugadores/{slug}")
public class JugadorController {

    private final EquipoServicio equipo;
    private final DuendeServicio duende;

    public JugadorController(EquipoServicio equipo, DuendeServicio duende) {
        this.equipo = equipo;
        this.duende = duende;
    }

    /** Perfil: cuentas y resumen de cada juego. */
    @GetMapping
    public JugadorVista perfil(@PathVariable String slug) {
        return equipo.jugador(slug);
    }

    /** Resumen, últimas partidas, media del equipo, desglose por mapa/dios y serie para la gráfica. */
    @GetMapping("/juegos/{juego}")
    public DetalleJuego detalle(@PathVariable String slug, @PathVariable Juego juego) {
        return equipo.detalle(slug, juego);
    }

    @GetMapping("/partidas")
    public PaginaPartidas partidas(
            @PathVariable String slug,
            @RequestParam(required = false) Juego juego,
            @RequestParam(defaultValue = "20") int limite,
            @RequestParam(defaultValue = "0") int offset) {
        return equipo.partidas(slug, juego, limite, offset);
    }

    /** Con quién del equipo juega mejor (y cómo le va solo). */
    @GetMapping("/sinergias")
    public Sinergias sinergias(@PathVariable String slug, @RequestParam Juego juego) {
        return equipo.sinergias(slug, juego);
    }

    /** Cómo le va según cuándo juega: orden en la sesión, después de ganar o de perder y hora del día. */
    @GetMapping("/sesiones")
    public Sesiones sesiones(@PathVariable String slug, @RequestParam Juego juego) {
        return equipo.sesiones(slug, juego);
    }

    /** En qué mejorar y qué hace bien, según el Duende. */
    @GetMapping("/consejos")
    public ConsejosVista consejos(
            @PathVariable String slug, @RequestParam Juego juego, @RequestParam(defaultValue = "es") String lang) {
        return duende.consejos(slug, juego, lang);
    }
}
