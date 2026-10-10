package com.ttcl.games.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.sync.FuenteJuego.ParticipacionExterna;
import com.ttcl.games.sync.FuenteJuego.PartidaExterna;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FuentesTest {

    private static final String STATS_FACEIT = """
            {"rounds": [{
              "round_stats": {"Map": "de_inferno", "Rounds": "22", "Score": "13 / 9"},
              "teams": [
                {"players": [{"player_id": "p1", "player_stats": {
                  "Kills": "21", "Deaths": "15", "Assists": "4", "Result": "1", "ADR": "88.4",
                  "Headshots %": "52", "K/R Ratio": "0.95", "MVPs": "4", "Triple Kills": "2", "Quadro Kills": "1",
                  "Penta Kills": "0", "Entry Count": "5", "Entry Wins": "3", "1v1Count": "1", "1v1Wins": "1",
                  "1v2Count": "2", "1v2Wins": "0", "Utility Damage": "140"}}]},
                {"players": [{"player_id": "p2", "player_stats": {"Kills": 10, "Deaths": 18, "Assists": 2, "Result": 0}}]}
              ]}]}
            """;

    @Test
    void mapeaLasEstadisticasDeFaceit() {
        Object json = JsonMapper.builder().build().readValue(STATS_FACEIT, Object.class);

        FaceitFuente.Mapeo m = FaceitFuente.mapearEstadisticas(json);

        assertThat(m.mapa()).isEqualTo("de_inferno");
        assertThat(m.participaciones()).hasSize(2);
        ParticipacionExterna p1 = m.participaciones().getFirst();
        assertThat(p1.externalPlayerId()).isEqualTo("p1");
        assertThat(p1.gano()).isTrue();
        assertThat(p1.kills()).isEqualTo(21);
        assertThat(p1.datos())
                .containsEntry("mapa", "de_inferno")
                .containsEntry("rondas", 22)
                .containsEntry("adr", 88.4)
                .containsEntry("hs_pct", 52.0)
                .containsEntry("multikills", 3)
                .containsEntry("entry_intentos", 5)
                .containsEntry("entry_ganados", 3)
                .containsEntry("clutch_intentos", 3)
                .containsEntry("clutch_ganados", 1)
                .containsEntry("dano_utilidad", 140);
        ParticipacionExterna p2 = m.participaciones().get(1);
        assertThat(p2.gano()).isFalse(); // Result numérico también vale
        assertThat(p2.datos()).doesNotContainKey("adr"); // sin dato, sin clave
    }

    @Test
    void nivelYEloDelJugadorYNivelDeCadaUnoEnLaPartida() {
        JsonMapper json = JsonMapper.builder().build();
        Object jugador = json.readValue("""
                {"player_id": "p1", "nickname": "uno", "games": {"cs2": {"region": "EU", "skill_level": 6,
                 "faceit_elo": 1287, "game_player_id": "76561198000000000"}}}
                """, Object.class);
        assertThat(FaceitFuente.mapearNivel(jugador)).contains(new FuenteJuego.NivelCuenta(6, 1287));
        assertThat(FaceitFuente.mapearNivel(Map.of("games", Map.of("csgo", Map.of("skill_level", 4))))).isEmpty();

        Object detalles = json.readValue("""
                {"match_id": "m1", "teams": {
                  "faction1": {"roster": [{"player_id": "p1", "nickname": "uno", "game_skill_level": 6},
                                          {"player_id": "p3", "game_skill_level": "7"}]},
                  "faction2": {"roster": [{"player_id": "p2", "game_skill_level": 5}, {"player_id": "p4"}]}}}
                """, Object.class);
        Map<String, Integer> niveles = FaceitFuente.nivelesDelRoster(detalles);
        assertThat(niveles).containsExactlyInAnyOrderEntriesOf(Map.of("p1", 6, "p2", 5, "p3", 7));

        // Cada participación de las estadísticas, con su nivel en esa partida.
        FaceitFuente.Mapeo m = FaceitFuente.conNiveles(
                FaceitFuente.mapearEstadisticas(json.readValue(STATS_FACEIT, Object.class)), niveles);
        assertThat(m.participaciones()).extracting(ParticipacionExterna::nivel).containsExactly(6, 5);
        assertThat(FaceitFuente.nivelesDelRoster(Map.of())).isEmpty();
    }

    @Test
    void mapeaSinRondasSinRomperse() {
        assertThat(FaceitFuente.mapearEstadisticas(Map.of()).participaciones()).isEmpty();
    }

    @Test
    void firmaYTimestampDeHirez() {
        String ts = HirezFuente.timestamp(Instant.parse("2026-01-02T03:04:05Z"));
        assertThat(ts).isEqualTo("20260102030405");
        // MD5("1234" + "createsession" + "CLAVE" + "20260102030405"), calculado con md5sum.
        assertThat(HirezFuente.firma("1234", "createsession", "CLAVE", ts))
                .isEqualTo("3740ea9ad15689676340fc9291de5d2b");
    }

    @Test
    void mapeaUnaFilaDelHistorialDeHirez() {
        Object fila = JsonMapper.builder().build().readValue("""
                {"Match": 987654, "Win_Status": "Winner", "Kills": "6", "Deaths": 3, "Assists": 9, "Damage": 30123,
                 "Damage_Mitigated": 12000, "Healing": 800, "Gold": 14500, "Minutes": 31, "God": "Zeus",
                 "Queue": "Conquest", "Entry_Datetime": "10/8/2026 7:05:12 PM"}
                """, Object.class);

        PartidaExterna p = HirezFuente.mapearFila(Json.mapa(fila), "42");

        assertThat(p.externalId()).isEqualTo("987654");
        assertThat(p.juego()).isEqualTo(Juego.SMITE2);
        assertThat(p.jugadaEn()).isEqualTo(Instant.parse("2026-10-08T19:05:12Z"));
        assertThat(p.duracionSeg()).isEqualTo(31 * 60);
        assertThat(p.modo()).isEqualTo("Conquest");
        ParticipacionExterna yo = p.participaciones().getFirst();
        assertThat(yo.externalPlayerId()).isEqualTo("42");
        assertThat(yo.gano()).isTrue();
        assertThat(yo.kills()).isEqualTo(6);
        assertThat(yo.datos()).containsEntry("dios", "Zeus").containsEntry("dano", 30123).containsEntry("minutos", 31.0);
    }

    @Test
    void fechaIsoTambienVale() {
        assertThat(HirezFuente.fecha("2026-10-08T19:05:12Z")).isEqualTo(Instant.parse("2026-10-08T19:05:12Z"));
    }
}
