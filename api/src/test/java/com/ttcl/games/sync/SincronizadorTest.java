package com.ttcl.games.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Muestra;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.MuestraRepo;
import com.ttcl.games.dominio.Repositorios.ParticipacionRepo;
import com.ttcl.games.dominio.Repositorios.PartidaRepo;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.sync.Sincronizador.ResultadoCuenta;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sincronización con una fuente de mentira, sobre los datos de ejemplo. Usa su propia base H2: guarda partidas y
 * muestras, y los demás tests cuentan con los datos de ejemplo tal cual.
 */
@SpringBootTest(properties = {
    "ttcl.demo=true",
    "ttcl.equipo-json=",
    "ttcl.faceit.api-key=",
    "ttcl.smite2.base=",
    "spring.datasource.url=jdbc:h2:mem:sincronizador;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
            + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
class SincronizadorTest {

    @Autowired
    Sincronizador sincronizador;

    @Autowired
    MuestraRepo muestras;

    @Autowired
    CuentaRepo cuentas;

    @Autowired
    PartidaRepo partidas;

    @Autowired
    ParticipacionRepo participaciones;

    /** Una partida de Jugador 1 (demo-j1-cs2) con tres jugadores de fuera; del último no se sabe el nivel. */
    static class FuenteFalsa implements FuenteJuego {
        @Override
        public Juego juego() {
            return Juego.CS2;
        }

        @Override
        public Optional<CuentaResuelta> resolverCuenta(String nick) {
            return Optional.empty();
        }

        @Override
        public List<PartidaExterna> partidasRecientes(String externalId, int limite, Set<String> conocidas) {
            if (!externalId.equals("demo-j1-cs2") || conocidas.contains("sync-1")) {
                return List.of();
            }
            Map<String, Object> datos = Map.of("mapa", "de_nuke", "marcador", "13 / 7", "rondas", 20, "adr", 91.0);
            return List.of(new PartidaExterna("sync-1", Juego.CS2, Instant.parse("2026-10-09T20:00:00Z"), 2100,
                    "de_nuke", List.of(
                            new ParticipacionExterna("demo-j1-cs2", 7, true, 20, 12, 3, datos),
                            new ParticipacionExterna("rival-1", 6, false, 15, 18, 2, datos),
                            new ParticipacionExterna("rival-2", 8, false, 18, 17, 4, datos),
                            new ParticipacionExterna("rival-3", null, true, 11, 13, 6, datos))));
        }

        @Override
        public Optional<NivelCuenta> nivel(String externalId) {
            return externalId.equals("demo-j1-cs2") ? Optional.of(new NivelCuenta(8, 1777)) : Optional.empty();
        }
    }

    private Set<Long> idsMuestras() {
        return muestras.findAll().stream().map(Muestra::getId).collect(Collectors.toSet());
    }

    private Cuenta cuentaCs2(String slug) {
        return cuentas.findAllConJugador().stream()
                .filter(c -> c.getJuego() == Juego.CS2 && c.getJugador().getSlug().equals(slug))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void guardaSinIdentificarALosDeFueraYElNivelDeCadaCuenta() {
        Set<Long> antes = idsMuestras();

        List<ResultadoCuenta> resultados = sincronizador.sincronizarTodo(Map.of(Juego.CS2, new FuenteFalsa()), 20);

        assertThat(resultados).hasSize(3).allMatch(ResultadoCuenta::ok);
        // La partida, solo con Jugador 1.
        long partida = partidas.findByJuegoAndExternalId(Juego.CS2, "sync-1").orElseThrow().getId();
        assertThat(participaciones.findAllCompletas().stream().filter(p -> p.getPartida().getId() == partida))
                .extracting(p -> p.getJugador().getSlug())
                .containsExactly("j1");
        // Dos muestras (del tercero no se sabe el nivel): su nivel, el mapa y el día; sin id, nick ni marcador.
        List<Muestra> nuevas = muestras.findAll().stream().filter(m -> !antes.contains(m.getId())).toList();
        assertThat(nuevas)
                .extracting(Muestra::getNivel, Muestra::getKills, Muestra::getMapa, Muestra::getFecha)
                .containsExactlyInAnyOrder(
                        tuple(6, 15, "de_nuke", LocalDate.of(2026, 10, 9)),
                        tuple(8, 18, "de_nuke", LocalDate.of(2026, 10, 9)));
        assertThat(nuevas.getFirst().getDatos())
                .containsEntry("adr", 91.0)
                .containsEntry("rondas", 20)
                .doesNotContainKeys("mapa", "marcador");
        // Nivel y ELO de la cuenta, al día; si la fuente no los da, se quedan los de antes.
        assertThat(cuentaCs2("j1").getNivel()).isEqualTo(8);
        assertThat(cuentaCs2("j1").getElo()).isEqualTo(1777);
        assertThat(cuentaCs2("j2").getNivel()).isEqualTo(6);

        // Otra vez: la partida ya es conocida y no se repiten las muestras.
        sincronizador.sincronizarTodo(Map.of(Juego.CS2, new FuenteFalsa()), 20);
        assertThat(idsMuestras()).hasSize(antes.size() + 2);
    }
}
