package com.ttcl.games.juego;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JuegoTest {

    @Test
    void reconoceLosRolesDeCadaJuegoSinMayusculasNiTildes() {
        assertThat(Juego.CS2.rol("Soporte")).contains("soporte");
        assertThat(Juego.CS2.rol(" AWP ")).contains("awp");
        assertThat(Juego.SMITE2.rol("Guardián")).contains("guardian");
        assertThat(Juego.SMITE2.rol("soporte")).isEmpty(); // es de CS2
        assertThat(Juego.CS2.rol("francotirador")).isEmpty();
        assertThat(Juego.CS2.rol("")).isEmpty();
        assertThat(Juego.CS2.rol(null)).isEmpty();
    }

    @Test
    void ningunRolSeLlamaIgualEnLosDosJuegos() {
        // La web traduce el rol sin mirar el juego.
        assertThat(Juego.CS2.roles()).doesNotContainAnyElementsOf(Juego.SMITE2.roles());
    }
}
