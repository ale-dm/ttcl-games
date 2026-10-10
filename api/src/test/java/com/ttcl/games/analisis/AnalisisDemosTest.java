package com.ttcl.games.analisis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ttcl.games.analisis.AnalisisCliente.AnalisisNoDisponibleException;
import com.ttcl.games.analisis.AnalisisCliente.DemoNoValidaException;
import com.ttcl.games.analisis.AnalisisModelos.JugadorAnalizado;
import com.ttcl.games.analisis.AnalisisModelos.JugadorBuscado;
import com.ttcl.games.analisis.AnalisisModelos.MuerteAnonima;
import com.ttcl.games.analisis.AnalisisModelos.PeticionAnalisis;
import com.ttcl.games.analisis.AnalisisModelos.RespuestaAnalisis;
import com.ttcl.games.analisis.AnalisisModelos.RondaJugador;
import com.ttcl.games.analisis.AnalisisModelos.RondaPartida;
import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Demo;
import com.ttcl.games.dominio.Participacion;
import com.ttcl.games.dominio.Partida;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.DemoRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.dominio.Repositorios.MuerteMapaRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.PartidaRepo;
import com.ttcl.games.dominio.Repositorios.RondaRepo;
import com.ttcl.games.dominio.Ronda;
import com.ttcl.games.juego.Juego;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Análisis de las demos pendientes (P12) con un trabajador de análisis de mentira, sobre los
 * datos de ejemplo. Usa su propia base H2: añade partidas y rondas, y los demás tests cuentan con los datos de ejemplo.
 */
@SpringBootTest(properties = {
    "ttcl.demo=true",
    "ttcl.equipo-json=",
    "ttcl.faceit.api-key=",
    "ttcl.smite2.base=",
    "ttcl.analisis.lote=10",
    "spring.datasource.url=jdbc:h2:mem:analisis;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
            + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
class AnalisisDemosTest {

    static final Path CARPETA = Path.of("target", "demos-test").toAbsolutePath();

    @DynamicPropertySource
    static void carpeta(DynamicPropertyRegistry registro) {
        registro.add("ttcl.analisis.carpeta", CARPETA::toString);
    }

    /** Hace de trabajador de análisis: apunta las peticiones y responde lo que diga {@code responder}. */
    static class AnalisisFalso extends AnalisisCliente {
        final List<PeticionAnalisis> peticiones = new ArrayList<>();
        Function<PeticionAnalisis, RespuestaAnalisis> responder;

        AnalisisFalso(TtclProperties props) {
            super(props);
        }

        @Override
        public boolean configurado() {
            return true;
        }

        @Override
        public RespuestaAnalisis analizar(PeticionAnalisis peticion) {
            peticiones.add(peticion);
            return responder.apply(peticion);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        AnalisisFalso analisisFalso(TtclProperties props) {
            return new AnalisisFalso(props);
        }

    }

    @Autowired
    AnalisisDemos analisis;

    @Autowired
    AnalisisFalso trabajador;

    @Autowired
    DemoRepo demos;

    @Autowired
    RondaRepo rondas;

    @Autowired
    MuerteMapaRepo muertes;

    @Autowired
    PartidaRepo partidas;

    @Autowired
    ParticipacionRepo participaciones;

    @Autowired
    JugadorRepo jugadores;

    @Autowired
    CuentaRepo cuentas;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void preparar() throws IOException {
        Files.createDirectories(CARPETA);
        try (var ficheros = Files.list(CARPETA)) {
            for (Path f : ficheros.toList()) {
                Files.delete(f);
            }
        }
        trabajador.peticiones.clear();
        trabajador.responder = p -> respuesta();
        // Solo las pendientes de cada test: las de antes se dan por analizadas.
        tx.executeWithoutResult(t -> demos.findAll().stream()
                .filter(d -> d.getEstado().equals(Demo.PENDIENTE))
                .forEach(d -> {
                    d.fallida("de otro test");
                    demos.save(d);
                }));
    }

    /** Una partida nueva de CS2 de Jugador 1 y Jugador 3, con su demo pendiente. */
    private Partida partidaPendiente(String externalId, String url) {
        return tx.execute(t -> {
            Partida p = partidas.save(new Partida(Juego.CS2, externalId, Instant.parse("2026-10-09T20:00:00Z"), 2400,
                    "de_mirage"));
            for (String slug : List.of("j1", "j3")) {
                Participacion part = new Participacion(p, jugadores.findBySlug(slug).orElseThrow());
                part.actualizar(true, 20, 15, 4, java.util.Map.of("mapa", "de_mirage", "rondas", 2));
                participaciones.save(part);
            }
            demos.save(new Demo(p, url));
            return p;
        });
    }

    private static RondaJugador ronda(int n, String lado, int kills, boolean murio, String zona) {
        return new RondaJugador(n, lado, true, kills, 1, 0, 90, 10, murio, murio ? 10.0 : null, murio ? 20.0 : null,
                zona, false, 0, null, 4200, "completa", true);
    }

    private static RespuestaAnalisis respuesta() {
        return new RespuestaAnalisis(
                "de_mirage",
                List.of(new RondaPartida(1, "T", "t_win"), new RondaPartida(2, "T", "t_win")),
                List.of(
                        new JugadorAnalizado("j1", "76561198000000001",
                                List.of(ronda(1, "T", 2, false, null), ronda(2, "T", 1, true, "TRamp"))),
                        new JugadorAnalizado("j3", "76561198000000003",
                                List.of(ronda(1, "T", 0, true, "Palace"), ronda(2, "T", 3, false, null)))),
                List.of(new MuerteAnonima(10, 20, "TRamp"), new MuerteAnonima(-5, 3, "Palace"),
                        new MuerteAnonima(1, 1, null)));
    }

    private Demo demoDe(Partida p) {
        return demos.findAll().stream().filter(d -> d.getPartida().getId().equals(p.getId())).findFirst().orElseThrow();
    }

    private Cuenta cuentaCs2(String slug) {
        return cuentas.findAllConJugador().stream()
                .filter(c -> c.getJuego() == Juego.CS2 && c.getJugador().getSlug().equals(slug))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void conLaDemoEnLaCarpetaGuardaCadaRondaYDondeMuereLaGente() throws IOException {
        Partida p = partidaPendiente("1-carpeta", null);
        Files.writeString(CARPETA.resolve("1-carpeta-1-1.dem.zst"), "demo");
        Files.writeString(CARPETA.resolve("1-carpeta.txt"), "no es una demo");
        int muertesAntes = muertes.findByMapa("de_mirage").size();

        AnalisisDemos.Resultado r = analisis.analizarPendientes();

        assertThat(r.analizadas()).isEqualTo(1);
        PeticionAnalisis peticion = trabajador.peticiones.getFirst();
        assertThat(peticion.archivo()).isEqualTo("1-carpeta-1-1.dem.zst");
        assertThat(peticion.url()).isNull();
        // A quién buscar: su steamid (si se sabe) y su nick de FACEIT.
        assertThat(peticion.jugadores()).extracting(JugadorBuscado::id, JugadorBuscado::nick)
                .containsExactlyInAnyOrder(tuple("j1", "demo_uno"), tuple("j3", "demo_tres"));
        assertThat(demoDe(p).getEstado()).isEqualTo(Demo.ANALIZADA);
        List<Ronda> guardadas = rondas.findDePartida(p.getId(), jugadores.findBySlug("j1").orElseThrow());
        assertThat(guardadas).extracting(Ronda::getRonda, Ronda::getKills, Ronda::isMurio, Ronda::getMuerteZona)
                .containsExactly(tuple(1, 2, false, null), tuple(2, 1, true, "TRamp"));
        assertThat(muertes.findByMapa("de_mirage")).hasSize(muertesAntes + 3);
        // Se le encontró por el nick: ya se sabe su steamid.
        assertThat(cuentaCs2("j3").getSteamId()).isEqualTo("76561198000000003");
    }

    @Test
    void sinAccesoALaDemoSigueEsperandoSinGastarIntentos() {
        Partida p = partidaPendiente("1-sin-acceso", "https://demos.faceit.com/cs2/1-sin-acceso.dem.zst");

        AnalisisDemos.Resultado r = analisis.analizarPendientes();

        assertThat(r.pendientes()).isEqualTo(1);
        assertThat(trabajador.peticiones).isEmpty();
        Demo d = demoDe(p);
        assertThat(d.getEstado()).isEqualTo(Demo.PENDIENTE);
        assertThat(d.getIntentos()).isZero();
        assertThat(d.getError()).contains("carpeta de demos");
    }

    @Test
    void unaDemoQueNoValeNoSeVuelveAPedir() throws IOException {
        Partida p = partidaPendiente("1-rota", null);
        Files.writeString(CARPETA.resolve("1-rota.dem"), "demo");
        trabajador.responder = x -> {
            throw new DemoNoValidaException("El fichero no es una demo de CS2.");
        };

        assertThat(analisis.analizarPendientes().fallidas()).isEqualTo(1);
        assertThat(demoDe(p).getEstado()).isEqualTo(Demo.FALLIDA);
        assertThat(demoDe(p).getError()).isEqualTo("El fichero no es una demo de CS2.");
        analisis.analizarPendientes();
        assertThat(trabajador.peticiones).hasSize(1);
    }

    @Test
    void siElTrabajadorNoRespondeLoIntentaTresVeces() throws IOException {
        Partida p = partidaPendiente("1-caido", null);
        Files.writeString(CARPETA.resolve("1-caido.dem.gz"), "demo");
        trabajador.responder = x -> {
            throw new AnalisisNoDisponibleException("El trabajador de análisis no responde", null);
        };

        analisis.analizarPendientes();
        assertThat(demoDe(p).getEstado()).isEqualTo(Demo.PENDIENTE);
        assertThat(demoDe(p).getIntentos()).isEqualTo(1);
        analisis.analizarPendientes();
        analisis.analizarPendientes();
        assertThat(demoDe(p).getEstado()).isEqualTo(Demo.FALLIDA);
        assertThat(trabajador.peticiones).hasSize(AnalisisDemos.MAX_INTENTOS);
    }

    @Test
    void sinNadieDelEquipoEnLaDemoNoGuardaNada() throws IOException {
        Partida p = partidaPendiente("1-vacia", null);
        Files.writeString(CARPETA.resolve("1-vacia.dem"), "demo");
        trabajador.responder = x -> new RespuestaAnalisis("de_mirage", List.of(), List.of(), List.of());

        analisis.analizarPendientes();

        assertThat(demoDe(p).getEstado()).isEqualTo(Demo.FALLIDA);
        assertThat(demoDe(p).getError()).isEqualTo("No está nadie del equipo en la demo.");
        assertThat(rondas.findDePartida(p.getId(), jugadores.findBySlug("j1").orElseThrow())).isEmpty();
    }
}
