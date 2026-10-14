package com.ttcl.games.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ttcl.games.stats.Capturas.Evolucion;
import com.ttcl.games.stats.Capturas.FilaEvolucion;
import com.ttcl.games.stats.Capturas.Foto;
import com.ttcl.games.stats.Capturas.MapaLeido;
import com.ttcl.games.stats.Capturas.Tramo;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapturasTest {

    /** Lo de la captura de csstats de ejemplo (la de MartintheKing). */
    private static Map<String, Double> antes() {
        Map<String, Double> d = new LinkedHashMap<>();
        d.put("rating", 0.74);
        d.put("kd", 0.6);
        d.put("winrate", 43.0);
        d.put("hs_pct", 36.0);
        d.put("adr", 57.0);
        d.put("partidas", 344.0);
        d.put("victorias", 147.0);
        d.put("derrotas", 188.0);
        d.put("empates", 9.0);
        d.put("kills", 3264.0);
        d.put("muertes", 5419.0);
        d.put("asistencias", 1840.0);
        d.put("headshots", 1168.0);
        d.put("dano", 404607.0);
        d.put("rondas", 7041.0);
        d.put("clutch_1v1_ganados", 52.0);
        d.put("clutch_1v1_perdidos", 53.0);
        d.put("premier_actual", 11445.0);
        return d;
    }

    /** Diez partidas después: 6 ganadas y 4 perdidas, 200 kills y 160 muertes en 220 rondas. */
    private static Map<String, Double> despues() {
        Map<String, Double> d = new LinkedHashMap<>(antes());
        d.put("rating", 0.76);
        d.put("kd", 0.61);
        d.put("partidas", 354.0);
        d.put("victorias", 153.0);
        d.put("derrotas", 192.0);
        d.put("kills", 3464.0);
        d.put("muertes", 5579.0);
        d.put("asistencias", 1880.0);
        d.put("headshots", 1248.0);
        d.put("dano", 420007.0);
        d.put("rondas", 7261.0);
        d.put("clutch_1v1_ganados", 55.0);
        d.put("clutch_1v1_perdidos", 54.0);
        d.put("premier_actual", 11300.0);
        return d;
    }

    private static Foto foto(long id, String cuando, Map<String, Double> datos) {
        return new Foto(id, Instant.parse(cuando), datos);
    }

    @Test
    void soloSeGuardanLosNumerosQueValen() {
        Map<String, Object> leido = new LinkedHashMap<>();
        leido.put("kd", 0.604);
        leido.put("winrate", 143.0); // un porcentaje de más de 100: mal leído
        leido.put("partidas", 344.4);
        leido.put("kills", -3);
        leido.put("inventado", 9);
        leido.put("adr", "57");
        leido.put("rating", 0.74);
        Map<String, Double> limpios = Capturas.limpiar(leido);
        // En el orden de CAMPOS; los enteros, redondeados; lo que no es un número, fuera.
        assertThat(limpios).containsExactly(Map.entry("rating", 0.74), Map.entry("kd", 0.6),
                Map.entry("partidas", 344.0));
        assertThat(Capturas.limpiar(null)).isEmpty();
    }

    @Test
    void mapasSinRepetirYConAlgunNumero() {
        List<MapaLeido> mapas = Capturas.limpiarMapas(List.of(
                new MapaLeido("de_inferno", 109, null),
                new MapaLeido("De_Inferno ", null, 48.0),
                new MapaLeido("de_dust2", 98, 120.0),
                new MapaLeido(" ", 5, null),
                new MapaLeido("de_nuke", null, null)));
        assertThat(mapas).extracting(MapaLeido::mapa, MapaLeido::partidas, MapaLeido::winrate)
                .containsExactly(tuple("de_inferno", 109, 48.0), tuple("de_dust2", 98, null));
    }

    @Test
    void comparaCadaNumeroYSacaElTramo() {
        Evolucion e = Capturas.comparar(foto(1, "2026-10-01T20:00:00Z", antes()), foto(2, "2026-10-11T20:00:00Z",
                despues()));
        assertThat(e.dias()).isEqualTo(10);
        assertThat(e.filas()).extracting(FilaEvolucion::clave, FilaEvolucion::diferencia, FilaEvolucion::mejora)
                .contains(
                        tuple("rating", 0.02, 1),
                        tuple("premier_actual", -145.0, -1),
                        tuple("partidas", 10.0, 0), // los totales siempre suben: ni mejor ni peor
                        tuple("adr", 0.0, 0));
        Tramo t = e.tramo();
        assertThat(t.partidas()).isEqualTo(10);
        assertThat(t.victorias()).isEqualTo(6);
        assertThat(t.derrotas()).isEqualTo(4);
        assertThat(t.empates()).isZero();
        assertThat(t.winrate()).isEqualTo(60.0);
        assertThat(t.kd()).isEqualTo(1.25); // 200 / 160
        assertThat(t.hsPct()).isEqualTo(40.0); // 80 / 200
        assertThat(t.adr()).isEqualTo(70.0); // 15400 / 220
        assertThat(t.killsPartida()).isEqualTo(20.0);
        assertThat(t.asistenciasPartida()).isEqualTo(4.0);
        assertThat(t.clutch1v1()).isEqualTo(75.0); // 3 de 4
    }

    @Test
    void sinPartidasNuevasOConTotalesQueBajanNoHayTramo() {
        Foto a = foto(1, "2026-10-01T20:00:00Z", antes());
        assertThat(Capturas.comparar(a, foto(2, "2026-10-02T20:00:00Z", antes())).tramo()).isNull();
        Map<String, Double> otraCuenta = despues();
        otraCuenta.put("kills", 100.0);
        assertThat(Capturas.comparar(a, foto(2, "2026-10-02T20:00:00Z", otraCuenta)).tramo()).isNull();
        // Si falta un total en alguna, ese número del tramo va a null; lo demás, igual.
        Map<String, Double> sinDano = despues();
        sinDano.remove("dano");
        Tramo t = Capturas.comparar(a, foto(2, "2026-10-02T20:00:00Z", sinDano)).tramo();
        assertThat(t.adr()).isNull();
        assertThat(t.kd()).isEqualTo(1.25);
    }

    @Test
    void laReferenciaEsLaUltimaConUnaSemanaMasOLaPrimera() {
        Foto a = foto(1, "2026-09-01T20:00:00Z", antes());
        Foto b = foto(2, "2026-09-20T20:00:00Z", antes());
        Foto c = foto(3, "2026-10-08T20:00:00Z", antes());
        Foto d = foto(4, "2026-10-12T20:00:00Z", antes());
        assertThat(Capturas.referencia(List.of(a, b, c, d))).isEqualTo(b);
        assertThat(Capturas.referencia(List.of(c, d))).isEqualTo(c); // ninguna con una semana más: la primera
        assertThat(Capturas.referencia(List.of(d))).isNull();
    }
}
