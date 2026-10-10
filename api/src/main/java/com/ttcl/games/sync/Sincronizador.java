package com.ttcl.games.sync;

import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Demo;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Muestra;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Partida;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.DemoRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.MuestraRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.PartidaRepo;
import com.ttcl.games.dominio.Repositorios.SyncRepo;
import com.ttcl.games.dominio.Sync;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.sync.FuenteJuego.ParticipacionExterna;
import com.ttcl.games.sync.FuenteJuego.PartidaExterna;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Para cada cuenta del equipo, pide las partidas recientes a su fuente y guarda las que tienen a algún miembro del
 * equipo. Una partida jugada por dos del equipo se guarda una vez, con dos participaciones. De los demás jugadores de
 * cada partida nueva se guarda lo que hicieron y su nivel, sin identificarlos (P8: muestras de lo normal en cada
 * nivel), y de cada cuenta, su nivel y ELO actuales. De cada partida nueva de CS2 se apunta su demo, pendiente de
 * analizar (P12).
 */
@Service
public class Sincronizador {

    private static final Logger log = LoggerFactory.getLogger(Sincronizador.class);

    private final CuentaRepo cuentas;
    private final JugadorRepo jugadores;
    private final PartidaRepo partidas;
    private final ParticipacionRepo participaciones;
    private final MuestraRepo muestras;
    private final DemoRepo demos;
    private final SyncRepo syncs;
    private final TransactionTemplate tx;

    public Sincronizador(
            CuentaRepo cuentas,
            JugadorRepo jugadores,
            PartidaRepo partidas,
            ParticipacionRepo participaciones,
            MuestraRepo muestras,
            DemoRepo demos,
            SyncRepo syncs,
            TransactionTemplate tx) {
        this.cuentas = cuentas;
        this.jugadores = jugadores;
        this.partidas = partidas;
        this.participaciones = participaciones;
        this.muestras = muestras;
        this.demos = demos;
        this.syncs = syncs;
        this.tx = tx;
    }

    public record ResultadoCuenta(String jugador, Juego juego, boolean ok, int partidasNuevas, String error) {}

    /** Sincroniza todas las cuentas cuyo juego tiene fuente. Un fallo en una cuenta no para las demás. */
    public List<ResultadoCuenta> sincronizarTodo(Map<Juego, FuenteJuego> fuentes, int limite) {
        List<Cuenta> todas = cuentas.findAllConJugador();
        List<ResultadoCuenta> resultados = new ArrayList<>();

        for (var entrada : fuentes.entrySet()) {
            Juego juego = entrada.getKey();
            FuenteJuego fuente = entrada.getValue();
            List<Cuenta> delJuego = todas.stream().filter(c -> c.getJuego() == juego).toList();
            if (delJuego.isEmpty()) {
                continue;
            }

            // Pasada 1: resolver los IDs que falten y saber quién es quién (ID de la fuente -> jugador del equipo).
            // Va antes de mirar partidas: si no, una partida con dos del equipo se guardaría solo con uno, quedaría
            // marcada como conocida y el otro nunca tendría su participación.
            Map<String, Long> jugadorPorExternalId = new HashMap<>();
            List<Cuenta> pendientes = new ArrayList<>();
            for (Cuenta cuenta : delJuego) {
                Instant inicio = Instant.now();
                String nombre = cuenta.getJugador().getNombre();
                try {
                    if (cuenta.getExternalId() == null) {
                        var resuelta = fuente.resolverCuenta(cuenta.getNombreExterno())
                                .orElseThrow(() -> new IllegalStateException(
                                        "no existe ninguna cuenta con el nick \"" + cuenta.getNombreExterno() + "\""));
                        cuenta.setExternalId(resuelta.externalId());
                        if (resuelta.nombre() != null) {
                            cuenta.setNombreExterno(resuelta.nombre());
                        }
                        cuentas.save(cuenta);
                        log.info("{} ({}): cuenta resuelta ({})", nombre, juego.codigo(), cuenta.getNombreExterno());
                    }
                    jugadorPorExternalId.put(cuenta.getExternalId(), cuenta.getJugador().getId());
                    pendientes.add(cuenta);
                } catch (RuntimeException e) {
                    resultados.add(fallo(cuenta, juego, inicio, e));
                }
            }

            // Pasada 2: partidas recientes de cada cuenta, sin pedir otra vez las que ya están guardadas.
            Set<String> conocidas = new HashSet<>(partidas.externalIdsDe(juego));
            for (Cuenta cuenta : pendientes) {
                Instant inicio = Instant.now();
                String nombre = cuenta.getJugador().getNombre();
                try {
                    List<PartidaExterna> lote = fuente.partidasRecientes(cuenta.getExternalId(), limite, conocidas);
                    int nuevas = guardar(juego, lote, jugadorPorExternalId);
                    lote.forEach(p -> conocidas.add(p.externalId()));
                    actualizarNivel(fuente, cuenta);
                    cuenta.setUltimaSync(Instant.now());
                    cuentas.save(cuenta);
                    syncs.save(Sync.ok(cuenta, inicio, nuevas));
                    log.info("{} ({}): {} partidas revisadas, {} nuevas", nombre, juego.codigo(), lote.size(), nuevas);
                    resultados.add(new ResultadoCuenta(nombre, juego, true, nuevas, null));
                } catch (RuntimeException e) {
                    resultados.add(fallo(cuenta, juego, inicio, e));
                }
            }
        }
        return resultados;
    }

    /**
     * Nivel, ELO y steamid actuales de la cuenta. Si no se pueden pedir, se quedan los de antes: las partidas ya se
     * guardaron.
     */
    private void actualizarNivel(FuenteJuego fuente, Cuenta cuenta) {
        try {
            fuente.nivel(cuenta.getExternalId()).ifPresent(n -> {
                cuenta.setNivel(n.nivel(), n.elo());
                if (n.steamId() != null) {
                    cuenta.setSteamId(n.steamId());
                }
            });
        } catch (RuntimeException e) {
            log.warn("{} ({}): no se pudo leer el nivel: {}",
                    cuenta.getJugador().getNombre(), cuenta.getJuego().codigo(), e.getMessage());
        }
    }

    private ResultadoCuenta fallo(Cuenta cuenta, Juego juego, Instant inicio, RuntimeException e) {
        String mensaje = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        syncs.save(Sync.error(cuenta, inicio, mensaje));
        log.warn("{} ({}): error: {}", cuenta.getJugador().getNombre(), juego.codigo(), mensaje);
        return new ResultadoCuenta(cuenta.getJugador().getNombre(), juego, false, 0, mensaje);
    }

    /**
     * Guarda las partidas que tengan a algún miembro del equipo y, de las nuevas, una muestra por cada jugador que no
     * es del equipo y del que se sabe el nivel. Devuelve cuántas partidas eran nuevas.
     */
    int guardar(Juego juego, List<PartidaExterna> lote, Map<String, Long> jugadorPorExternalId) {
        int nuevas = 0;
        for (PartidaExterna externa : lote) {
            List<ParticipacionExterna> delEquipo = externa.participaciones().stream()
                    .filter(p -> p.externalPlayerId() != null && jugadorPorExternalId.containsKey(p.externalPlayerId()))
                    .toList();
            if (delEquipo.isEmpty()) {
                continue;
            }
            Boolean nueva = tx.execute(estado -> {
                // Puede que ya esté: la guardó otra cuenta del equipo en esta pasada o en una anterior.
                var existente = partidas.findByJuegoAndExternalId(juego, externa.externalId());
                Partida partida = existente.orElseGet(() -> partidas.save(new Partida(
                        juego, externa.externalId(), externa.jugadaEn(), externa.duracionSeg(), externa.modo())));
                for (ParticipacionExterna p : delEquipo) {
                    Jugador jugador = jugadores.getReferenceById(jugadorPorExternalId.get(p.externalPlayerId()));
                    Participacion participacion = participaciones.findByPartidaAndJugador(partida, jugador)
                            .orElseGet(() -> new Participacion(partida, jugador));
                    participacion.actualizar(p.gano(), p.kills(), p.muertes(), p.asistencias(), p.datos());
                    participaciones.save(participacion);
                }
                if (existente.isEmpty()) {
                    guardarMuestras(juego, externa, jugadorPorExternalId);
                    if (juego == Juego.CS2) {
                        demos.save(new Demo(partida, externa.demoUrl()));
                    }
                }
                return existente.isEmpty();
            });
            if (Boolean.TRUE.equals(nueva)) {
                nuevas++;
            }
        }
        return nuevas;
    }

    /** Los que no son del equipo, sin nick ni id (ni suyo ni de la partida) y sin el marcador: solo números y nivel. */
    private void guardarMuestras(Juego juego, PartidaExterna externa, Map<String, Long> jugadorPorExternalId) {
        LocalDate fecha = LocalDate.ofInstant(externa.jugadaEn(), ZoneOffset.UTC);
        for (ParticipacionExterna p : externa.participaciones()) {
            if (p.nivel() == null || jugadorPorExternalId.containsKey(p.externalPlayerId())) {
                continue;
            }
            Map<String, Object> datos = new LinkedHashMap<>(p.datos() == null ? Map.of() : p.datos());
            datos.keySet().removeAll(Set.of("mapa", "marcador"));
            muestras.save(new Muestra(
                    juego, p.nivel(), externa.modo(), fecha, p.gano(), p.kills(), p.muertes(), p.asistencias(), datos));
        }
    }
}
