package com.ttcl.games.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AvisosDiscordTest {

    @Test
    void troceaSinPartirBloquesQueCabenYCortaLosQueNo() {
        String a = "a".repeat(1500);
        String b = "b".repeat(600);
        assertThat(AvisosDiscord.trocear(List.of(a, b, "c"), 1900)).containsExactly(a, b + "\nc");
        assertThat(AvisosDiscord.trocear(List.of("x".repeat(2500)), 1900))
                .singleElement()
                .satisfies(t -> assertThat(t).hasSize(1900).endsWith("…"));
        assertThat(AvisosDiscord.trocear(List.of(), 1900)).isEmpty();
    }
}
