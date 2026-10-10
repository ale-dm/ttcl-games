package com.ttcl.games.web;

import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.EquipoServicio;
import com.ttcl.games.servicio.Vistas.ConsejosVista;
import com.ttcl.games.servicio.Vistas.ConsultaPartidas;
import com.ttcl.games.servicio.Vistas.DetalleJuego;
import com.ttcl.games.servicio.Vistas.JugadorVista;
import com.ttcl.games.servicio.Vistas.PaginaPartidas;
import com.ttcl.games.servicio.Vistas.RondasPartida;
import com.ttcl.games.stats.Modelos.CalorMapa;
import com.ttcl.games.stats.Modelos.ResumenDemos;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import com.ttcl.games.stats.FiltroPartidas;
import com.ttcl.games.stats.Periodo;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Todo lo de un jugador. {@code ?periodo=7d|30d|todo} (por defecto, todo) recorta las partidas que cuentan. */
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
    public JugadorVista perfil(@PathVariable String slug, @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.jugador(slug, periodo);
    }

    /** Resumen, últimas partidas, media del equipo, desglose por mapa/dios y serie para la gráfica. */
    @GetMapping("/juegos/{juego}")
    public DetalleJuego detalle(
            @PathVariable String slug, @PathVariable Juego juego,
            @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.detalle(slug, juego, periodo);
    }

    /** Historial, la más reciente primero, con lo que dice el Duende de cada partida (P10). */
    @GetMapping("/partidas")
    public PaginaPartidas partidas(
            @PathVariable String slug,
            @RequestParam(required = false) Juego juego,
            @RequestParam(defaultValue = "20") int limite,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "todo") Periodo periodo,
            @RequestParam(defaultValue = "es") String lang) {
        return duende.partidas(slug, juego, limite, offset, periodo, lang);
    }

    /**
     * Partidas filtradas por mapa o dios ({@code clave}), resultado ({@code victoria} o {@code derrota}), días
     * ({@code desde} y {@code hasta}, incluidos, en la zona del equipo) o las {@code ultimas} n, con su resumen y la
     * media del resto del equipo con el mismo filtro. Es lo que consulta el Duende cuando el chat pregunta algo que no
     * está en los resúmenes (P9).
     */
    @GetMapping("/consulta")
    public ConsultaPartidas consulta(
            @PathVariable String slug,
            @RequestParam Juego juego,
            @RequestParam(required = false) String clave,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer ultimas,
            @RequestParam(defaultValue = "10") int limite) {
        FiltroPartidas filtro = new FiltroPartidas(clave, FiltroPartidas.resultado(resultado), desde, hasta, ultimas);
        return equipo.consulta(slug, juego, filtro, limite);
    }

    /** Con quién del equipo juega mejor (y cómo le va solo). */
    @GetMapping("/sinergias")
    public Sinergias sinergias(
            @PathVariable String slug, @RequestParam Juego juego, @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.sinergias(slug, juego, periodo);
    }

    /** Cómo le va según cuándo juega: orden en la sesión, después de ganar o de perder y hora del día. */
    @GetMapping("/sesiones")
    public Sesiones sesiones(
            @PathVariable String slug, @RequestParam Juego juego, @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.sesiones(slug, juego, periodo);
    }

    /**
     * Lo que dicen las rondas de sus demos de CS2 analizadas (P12): rating, KAST, trades, duelos de apertura,
     * asistencias de flash, utilidad, CT y T, economía y dónde muere en cada mapa, con la media del resto del equipo.
     */
    @GetMapping("/demos")
    public ResumenDemos demos(@PathVariable String slug, @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.demos(slug, periodo);
    }

    /** Mapa de calor de dónde muere en un mapa (sin {@code mapa}, en el que más rondas tiene analizadas). */
    @GetMapping("/demos/calor")
    public CalorMapa calor(
            @PathVariable String slug,
            @RequestParam(required = false) String mapa,
            @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.calor(slug, mapa, periodo);
    }

    /** Lo que hizo en cada ronda de una partida con la demo analizada (P12). */
    @GetMapping("/partidas/{partidaId}/rondas")
    public RondasPartida rondas(@PathVariable String slug, @PathVariable long partidaId) {
        return equipo.rondasPartida(slug, partidaId);
    }

    /** En qué mejorar y qué hace bien, según el Duende. */
    @GetMapping("/consejos")
    public ConsejosVista consejos(
            @PathVariable String slug,
            @RequestParam Juego juego,
            @RequestParam(defaultValue = "es") String lang,
            @RequestParam(defaultValue = "todo") Periodo periodo) {
        return duende.consejos(slug, juego, lang, periodo);
    }
}
