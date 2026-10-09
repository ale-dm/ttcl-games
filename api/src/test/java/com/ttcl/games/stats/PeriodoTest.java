package com.ttcl.games.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PeriodoTest {

    @Test
    void seLeePorSuCodigo() {
        assertThat(Periodo.de("7d")).isEqualTo(Periodo.SIETE_DIAS);
        assertThat(Periodo.de(" 30D ")).isEqualTo(Periodo.TREINTA_DIAS);
        assertThat(Periodo.de("todo")).isEqualTo(Periodo.TODO);
        assertThat(Periodo.SIETE_DIAS.codigo()).isEqualTo("7d");
        assertThatThrownBy(() -> Periodo.de("1a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Periodo.de(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cuentaHaciaAtrasDesdeAhora() {
        Instant ahora = Instant.parse("2026-10-09T12:00:00Z");
        assertThat(Periodo.SIETE_DIAS.inicio(ahora)).isEqualTo(Instant.parse("2026-10-02T12:00:00Z"));
        assertThat(Periodo.TREINTA_DIAS.inicio(ahora)).isEqualTo(Instant.parse("2026-09-09T12:00:00Z"));
        assertThat(Periodo.TODO.inicio(ahora)).isNull();
        assertThat(Periodo.RECORTADOS).containsExactly(Periodo.SIETE_DIAS, Periodo.TREINTA_DIAS);
    }
}
