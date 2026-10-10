package com.ttcl.games.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.FilaSemana;
import com.ttcl.games.stats.Modelos.Hecho;
import com.ttcl.games.stats.Modelos.SemanaJuego;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InformesTest {

    private static final Instant HOY = Instant.parse("2026-10-10T20:00:00Z");

    /** Partida de CS2 de hace {@code dias} días, en ese mapa, con ese resultado y ese ADR (10 kills y 10 muertes). */
    private static FilaParticipacion cs2(long id, double dias, String mapa, Boolean gano, double adr) {
        return new FilaParticipacion(id, Juego.CS2, HOY.minusSeconds((long) (dias * 86400)), null, mapa, gano, 10, 10,
                2, Map.of("mapa", mapa, "adr", adr));
    }

    private static List<String> tipos(List<Hecho> hechos) {
        return hechos.stream().map(Hecho::tipo).toList();
    }

    @Test
    void rachasYRachasQueSeAcaban() {
        FilaParticipacion ultima = cs2(4, 0, "de_mirage", false, 80);
        List<FilaParticipacion> historial = List.of(
                cs2(1, 3, "de_inferno", true, 80), cs2(2, 2, "de_nuke", false, 80), cs2(3, 1, "de_mirage", false, 80),
                ultima,
                cs2(5, -1, "de_mirage", false, 80)); // las de después no cuentan
        assertThat(Informes.hechos(Juego.CS2, ultima, historial))
                .containsExactly(new Hecho("racha_derrotas", null, null, null, 3, null));

        // Ganar después de tres derrotas acaba la racha.
        FilaParticipacion gana = cs2(6, -0.5, "de_dust2", true, 80);
        List<FilaParticipacion> conLaVictoria = new ArrayList<>(historial);
        conLaVictoria.add(gana);
        assertThat(Informes.hechos(Juego.CS2, gana, conLaVictoria))
                .containsExactly(new Hecho("fin_racha_derrotas", null, null, null, 3, null));
    }

    @Test
    void rachaEnUnMapaYEstreno() {
        List<FilaParticipacion> historial = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            historial.add(cs2(i, 60 + i, i % 2 == 0 ? "de_nuke" : "de_mirage", i % 2 == 0 ? false : true, 80));
        }
        // Tercera derrota seguida en Nuke (entre medias ha ganado en Mirage: no es una racha general).
        FilaParticipacion nuke = cs2(20, 50, "de_nuke", false, 80);
        historial.add(nuke);
        assertThat(Informes.hechos(Juego.CS2, nuke, historial))
                .containsExactly(new Hecho("racha_derrotas_clave", null, null, null, 6, "de_nuke"));

        // Con 10 partidas o más de antes, la primera en un mapa es un estreno.
        FilaParticipacion anubis = cs2(21, 49, "de_anubis", null, 80);
        historial.add(anubis);
        assertThat(Informes.hechos(Juego.CS2, anubis, historial))
                .containsExactly(new Hecho("estreno_clave", null, null, null, null, "de_anubis"));
    }

    @Test
    void mejorYPeorDelMesConPartidasSuficientes() {
        List<FilaParticipacion> historial = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            historial.add(cs2(i, 5 + i, "de_mirage", null, 70 + i * 2)); // de 70 a 88 en el último mes
        }
        historial.add(cs2(19, 40, "de_mirage", null, 150)); // hace más de un mes: no cuenta
        FilaParticipacion buena = cs2(20, 0, "de_mirage", null, 95.25);
        historial.add(buena);
        // (La media de las 11 de antes es 83,6: 95 no se separa tanto como para decirlo también.)
        assertThat(Informes.hechos(Juego.CS2, buena, historial))
                .containsExactly(new Hecho("mejor_mes", "adr", 95.25, 88.0, 10, null));

        FilaParticipacion mala = cs2(21, 0, "de_mirage", null, 60);
        assertThat(Informes.hechos(Juego.CS2, mala, historial.subList(0, 11)))
                .containsExactly(new Hecho("peor_mes", "adr", 60.0, 70.0, 10, null));

        // Con nueve en el último mes, nada.
        assertThat(Informes.hechos(Juego.CS2, buena, historial.subList(1, 11))).isEmpty();
    }

    @Test
    void muyPorEncimaOPorDebajoDeSuMedia() {
        List<FilaParticipacion> historial = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            historial.add(cs2(i, 40 + i, "de_mirage", null, 80)); // más de un mes atrás: sin récords del mes
        }
        assertThat(tipos(Informes.hechos(Juego.CS2, cs2(20, 0, "de_mirage", null, 105), historial)))
                .containsExactly("sobre_media");
        assertThat(Informes.hechos(Juego.CS2, cs2(21, 0, "de_mirage", null, 50), historial))
                .containsExactly(new Hecho("bajo_media", "adr", 50.0, 80.0, 10, null));
        assertThat(Informes.hechos(Juego.CS2, cs2(22, 0, "de_mirage", null, 95), historial)).isEmpty();
    }

    @Test
    void enSmiteLaMetricaPrincipalEsElKda() {
        List<FilaParticipacion> historial = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            historial.add(new FilaParticipacion(i, Juego.SMITE2, HOY.minusSeconds((40L + i) * 86400), null, "Conquest",
                    null, 4, 4, 4, Map.of("dios", "Zeus")));
        }
        FilaParticipacion partida = new FilaParticipacion(
                99, Juego.SMITE2, HOY, null, "Conquest", null, 10, 2, 6, Map.of("dios", "Zeus"));
        assertThat(Informes.hechos(Juego.SMITE2, partida, historial))
                .containsExactly(new Hecho("sobre_media", "kda", 8.0, 2.0, 10, null));
    }

    @Test
    void semanaConElMejorYElPeor() {
        List<FilaSemana> filas = List.of(
                new FilaSemana("j1", "Ana", 7, 3, 42.9, 1.2),
                new FilaSemana("j2", "Bea", 5, 5, 100.0, 0.9),
                new FilaSemana("j3", "Carla", 2, 2, 100.0, 2.0)); // menos de 3 partidas: no cuenta
        SemanaJuego s = Informes.semana(Juego.CS2, 9, filas);
        assertThat(s.partidas()).isEqualTo(9);
        assertThat(s.jugadores()).extracting(FilaSemana::slug).containsExactly("j1", "j2", "j3");
        assertThat(s.mejor().slug()).isEqualTo("j2");
        assertThat(s.peor().slug()).isEqualTo("j1");
        // Con uno solo que llegue, no hay peor.
        assertThat(Informes.semana(Juego.CS2, 7, filas.subList(0, 1)).peor()).isNull();
    }
}
