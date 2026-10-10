package com.ttcl.games.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FiltroPartidasTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static FilaParticipacion cs2(long id, String cuando, String mapa, Boolean gano) {
        return new FilaParticipacion(
                id, Juego.CS2, Instant.parse(cuando), null, mapa, gano, 10, 10, 2, Map.of("mapa", mapa));
    }

    private static final List<FilaParticipacion> FILAS = List.of(
            cs2(1, "2026-10-01T18:00:00Z", "de_mirage", true),
            // A las 23:30 UTC del 1 ya es día 2 en Madrid.
            cs2(2, "2026-10-01T23:30:00Z", "de_mirage", false),
            cs2(3, "2026-10-03T19:00:00Z", "de_nuke", false),
            cs2(4, "2026-10-05T20:00:00Z", "de_mirage", false),
            cs2(5, "2026-10-06T20:00:00Z", "de_inferno", null));

    private static List<Long> ids(FiltroPartidas filtro) {
        return filtro.aplicar(Juego.CS2, FILAS, MADRID).stream().map(FilaParticipacion::partidaId).toList();
    }

    @Test
    void sinNadaTodasYLaMasRecientePrimero() {
        assertThat(ids(new FiltroPartidas(null, null, null, null, null))).containsExactly(5L, 4L, 3L, 2L, 1L);
    }

    @Test
    void elMapaSinDistinguirMayusculasNiElDe() {
        assertThat(ids(new FiltroPartidas("Mirage", null, null, null, null))).containsExactly(4L, 2L, 1L);
        assertThat(ids(new FiltroPartidas("DE_NUKE", null, null, null, null))).containsExactly(3L);
        assertThat(ids(new FiltroPartidas("Dust 2", null, null, null, null))).isEmpty();
    }

    @Test
    void resultadoDiasEnLaZonaDelEquipoYLasUltimas() {
        assertThat(ids(new FiltroPartidas(null, false, null, null, null))).containsExactly(4L, 3L, 2L);
        assertThat(ids(new FiltroPartidas(null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), null)))
                .containsExactly(3L, 2L);
        // Las dos últimas derrotas en Mirage: primero se filtra y luego se cuentan.
        assertThat(ids(new FiltroPartidas("mirage", false, null, null, 2))).containsExactly(4L, 2L);
    }

    @Test
    void validaLaEntrada() {
        assertThat(FiltroPartidas.resultado("Victoria")).isTrue();
        assertThat(FiltroPartidas.resultado("derrota")).isFalse();
        assertThat(FiltroPartidas.resultado(" ")).isNull();
        assertThatThrownBy(() -> FiltroPartidas.resultado("empate")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroPartidas(null, null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroPartidas(null, null, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
