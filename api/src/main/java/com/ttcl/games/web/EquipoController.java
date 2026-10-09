package com.ttcl.games.web;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.duende.DuendeCliente;
import com.ttcl.games.duende.DuendeServicio;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.servicio.EquipoServicio;
import com.ttcl.games.servicio.Vistas.BusquedaVista;
import com.ttcl.games.servicio.Vistas.Comparacion;
import com.ttcl.games.servicio.Vistas.Estado;
import com.ttcl.games.servicio.Vistas.EstadoDuende;
import com.ttcl.games.servicio.Vistas.GruposJuego;
import com.ttcl.games.servicio.Vistas.Ranking;
import com.ttcl.games.servicio.Vistas.TarjetaJugador;
import com.ttcl.games.stats.Periodo;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EquipoController {

    private final EquipoServicio equipo;
    private final DuendeServicio duende;
    private final DuendeCliente duendeCliente;
    private final CuentaRepo cuentas;
    private final JugadorRepo jugadores;
    private final TtclProperties props;

    public EquipoController(
            EquipoServicio equipo,
            DuendeServicio duende,
            DuendeCliente duendeCliente,
            CuentaRepo cuentas,
            JugadorRepo jugadores,
            TtclProperties props) {
        this.equipo = equipo;
        this.duende = duende;
        this.duendeCliente = duendeCliente;
        this.cuentas = cuentas;
        this.jugadores = jugadores;
        this.props = props;
    }

    /** Última sincronización, fuentes configuradas y si el Duende responde. */
    @GetMapping("/estado")
    public Estado estado() {
        EstadoDuende estadoDuende = duendeCliente.salud()
                .map(s -> new EstadoDuende(true, s.gemini(), s.modelo()))
                .orElse(new EstadoDuende(false, false, null));
        return new Estado(
                cuentas.ultimaSync().orElse(null),
                jugadores.findAll().stream().anyMatch(j -> j.isDemo()),
                Map.of(
                        Juego.CS2.codigo(), props.faceit().configurada(),
                        Juego.SMITE2.codigo(), props.smite2().configurada()),
                estadoDuende);
    }

    /** Tarjetas del equipo. Con {@code juego}, solo ese juego y solo quien lo juega. */
    @GetMapping("/equipo")
    public List<TarjetaJugador> equipo(
            @RequestParam(required = false) Juego juego, @RequestParam(defaultValue = "es") String lang) {
        return duende.tarjetas(juego, lang);
    }

    /** Mejores dúos y tríos. Con {@code juego}, solo ese juego. */
    @GetMapping("/equipo/grupos")
    public List<GruposJuego> grupos(@RequestParam(required = false) Juego juego) {
        return equipo.grupos(juego);
    }

    @GetMapping("/buscar")
    public List<BusquedaVista> buscar(@RequestParam(defaultValue = "") String q) {
        return equipo.buscar(q);
    }

    /** Cara a cara. Con {@code periodo} (7d, 30d o todo), solo las partidas de esos días. */
    @GetMapping("/comparar")
    public Comparacion comparar(
            @RequestParam String a, @RequestParam String b, @RequestParam Juego juego,
            @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.comparar(a, b, juego, periodo);
    }

    /** Clasificación. Con {@code periodo} (7d, 30d o todo), solo las partidas de esos días. */
    @GetMapping("/ranking")
    public Ranking ranking(@RequestParam Juego juego, @RequestParam(defaultValue = "todo") Periodo periodo) {
        return equipo.ranking(juego, periodo);
    }
}
