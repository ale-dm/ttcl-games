package com.ttcl.games.carga;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.juego.Juego;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Carga el equipo real desde {@code config/equipo.json} (copia de equipo.example.json, que no va al repo). Es
 * idempotente: se ejecuta en cada arranque. Si cambia el nick de una cuenta, se borra su ID para volver a resolverlo.
 * El rol de cada cuenta se vuelve a leer en cada arranque; si no es uno de los del juego, se avisa y queda sin rol.
 */
@Component
@Order(1)
public class EquipoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquipoSeeder.class);

    record EntradaEquipo(String slug, String nombre, Map<String, Map<String, String>> cuentas) {}

    private final TtclProperties props;
    private final JugadorRepo jugadores;
    private final CuentaRepo cuentas;

    public EquipoSeeder(TtclProperties props, JugadorRepo jugadores, CuentaRepo cuentas) {
        this.props = props;
        this.jugadores = jugadores;
        this.cuentas = cuentas;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (props.equipoJson() == null || props.equipoJson().isBlank()) {
            return;
        }
        Path ruta = Path.of(props.equipoJson());
        if (!Files.exists(ruta)) {
            log.info("No existe {}: no se carga equipo real", ruta.toAbsolutePath());
            return;
        }
        List<EntradaEquipo> equipo = JsonMapper.builder().build()
                .readValue(Files.readString(ruta), new TypeReference<List<EntradaEquipo>>() {});
        for (EntradaEquipo miembro : equipo) {
            Jugador jugador = jugadores.findBySlug(miembro.slug())
                    .orElseGet(() -> new Jugador(miembro.slug(), miembro.nombre(), false));
            jugador.setNombre(miembro.nombre());
            jugadores.save(jugador);
            if (miembro.cuentas() == null) {
                continue;
            }
            for (var entrada : miembro.cuentas().entrySet()) {
                Juego juego = Juego.desde(entrada.getKey());
                String nick = entrada.getValue().get("nick");
                if (nick == null || nick.isBlank()) {
                    continue;
                }
                Cuenta cuenta = cuentas.findByJugadorAndJuego(jugador, juego)
                        .orElseGet(() -> new Cuenta(jugador, juego, nick));
                if (!nick.equals(cuenta.getNombreExterno())) {
                    cuenta.setNombreExterno(nick);
                    cuenta.setExternalId(null);
                }
                cuenta.setRol(rol(juego, entrada.getValue().get("rol"), miembro.slug()));
                cuentas.save(cuenta);
            }
        }
        log.info("Equipo cargado desde {}: {} miembros", ruta, equipo.size());
    }

    private static String rol(Juego juego, String texto, String slug) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return juego.rol(texto).orElseGet(() -> {
            log.warn("El rol \"{}\" de {} no es de {} (valen {}): se queda sin rol", texto, slug, juego.nombre(),
                    juego.roles());
            return null;
        });
    }
}
