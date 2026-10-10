package com.ttcl.games.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ttcl.games.stats.Modelos.CalorMapa;
import com.ttcl.games.stats.Modelos.FilaCompra;
import com.ttcl.games.stats.Modelos.FilaLado;
import com.ttcl.games.stats.Modelos.FilaRonda;
import com.ttcl.games.stats.Modelos.MapaMuertes;
import com.ttcl.games.stats.Modelos.MetricasRondas;
import com.ttcl.games.stats.Modelos.ResumenDemos;
import com.ttcl.games.stats.Modelos.ZonaMuerte;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RondasTest {

    private static final Instant DIA = Instant.parse("2026-10-01T20:00:00Z");

    /** Una ronda con lo justo: kills, asistencias, si murió (y dónde, y si le tradearon), daño y apertura. */
    private static FilaRonda ronda(
            long partida, int numero, String lado, boolean gano, int kills, int asist, boolean murio, String zona,
            boolean tradeado, int dano, String apertura, String compra) {
        boolean kast = kills > 0 || asist > 0 || !murio || tradeado;
        return new FilaRonda(partida, DIA, "de_inferno", numero, lado, gano, kills, asist, asist > 0 ? 1 : 0, dano,
                dano / 4, murio, murio ? 100.0 + numero : null, murio ? 200.0 : null, zona, tradeado, kills > 1 ? 1 : 0,
                apertura, 4000, compra, kast);
    }

    /** Dos partidas, cinco rondas. */
    private static List<FilaRonda> filas() {
        return List.of(
                ronda(1, 1, "CT", true, 2, 0, false, null, false, 180, "ganada", "pistola"),
                ronda(1, 2, "CT", false, 0, 0, true, "Banana", false, 20, "perdida", "eco"),
                ronda(1, 3, "T", true, 1, 1, true, "Banana", true, 120, null, "completa"),
                ronda(2, 1, "T", false, 0, 1, true, "Bombsite B", false, 40, null, "pistola"),
                ronda(2, 2, "T", true, 1, 0, false, null, false, 90, null, "forzada"));
    }

    @Test
    void metricasDeLasRondas() {
        MetricasRondas m = Rondas.metricas(filas());
        assertThat(m.partidas()).isEqualTo(2);
        assertThat(m.rondas()).isEqualTo(5);
        // KAST: kill (1), nada (2), kill (3), asistencia (4), sobrevive (5): 4 de 5.
        assertThat(m.kast()).isEqualTo(80.0);
        assertThat(m.adr()).isEqualTo(90.0); // 450 / 5
        assertThat(m.kpr()).isEqualTo(0.8);
        assertThat(m.dpr()).isEqualTo(0.6);
        assertThat(m.aperturas()).isEqualTo(2);
        assertThat(m.aperturasGanadas()).isEqualTo(1);
        assertThat(m.aperturaPct()).isEqualTo(50.0);
        assertThat(m.trades()).isEqualTo(1); // la ronda de dos kills
        assertThat(m.tradesPartida()).isEqualTo(0.5);
        assertThat(m.muertes()).isEqualTo(3);
        assertThat(m.muertesTradeadas()).isEqualTo(1);
        assertThat(m.tradeadasPct()).isEqualTo(33.3);
        assertThat(m.flashPartida()).isEqualTo(1.0); // dos rondas con asistencia de flash, dos partidas
        assertThat(m.utilidadRonda()).isEqualTo(22.4); // (45 + 5 + 30 + 10 + 22) / 5
        assertThat(m.winrateRondas()).isEqualTo(60.0);
        assertThat(m.rating()).isEqualTo(Rondas.rating(80.0, 0.8, 0.6, 0.4, 90.0));
    }

    @Test
    void ratingAlEstiloDelDeHltv() {
        // Un jugador normal anda por 1,00; uno que mata mucho y muere poco, bastante más.
        assertThat(Rondas.rating(70.0, 0.68, 0.66, 0.13, 75.0)).isBetween(0.95, 1.1);
        assertThat(Rondas.rating(80.0, 0.9, 0.55, 0.15, 95.0)).isGreaterThan(1.3);
        assertThat(Rondas.rating(55.0, 0.45, 0.8, 0.1, 55.0)).isLessThan(0.7);
        assertThat(Rondas.rating(null, 0.7, 0.6, 0.1, 80.0)).isNull();
    }

    @Test
    void sinRondasTodoACero() {
        MetricasRondas m = Rondas.metricas(List.of());
        assertThat(m.partidas()).isZero();
        assertThat(m.rating()).isNull();
        assertThat(m.kast()).isNull();
        ResumenDemos r = Rondas.resumen(List.of(), List.of());
        assertThat(r.equipo()).isNull();
        assertThat(r.lados()).isEmpty();
        assertThat(r.mapas()).isEmpty();
    }

    @Test
    void ctYTEconomiaYMapas() {
        List<FilaLado> lados = Rondas.lados(filas());
        assertThat(lados).extracting(FilaLado::lado, FilaLado::rondas, FilaLado::ganadas, FilaLado::winrate)
                .containsExactly(tuple("CT", 2, 1, 50.0), tuple("T", 3, 2, 66.7));
        List<FilaCompra> economia = Rondas.economia(filas());
        assertThat(economia).extracting(FilaCompra::compra, FilaCompra::rondas, FilaCompra::winrate)
                .containsExactly(tuple("pistola", 2, 50.0), tuple("eco", 1, 0.0), tuple("forzada", 1, 100.0),
                        tuple("completa", 1, 100.0));
        List<MapaMuertes> mapas = Rondas.mapas(filas());
        assertThat(mapas).hasSize(1);
        MapaMuertes inferno = mapas.getFirst();
        assertThat(inferno.partidas()).isEqualTo(2);
        assertThat(inferno.muertes()).isEqualTo(3);
        assertThat(inferno.sinTrade()).isEqualTo(2);
        // Primero la zona con más muertes sin trade (a igualdad, con más muertes).
        assertThat(inferno.zonas()).extracting(ZonaMuerte::zona, ZonaMuerte::muertes, ZonaMuerte::sinTrade)
                .containsExactly(tuple("Banana", 2, 1), tuple("Bombsite B", 1, 1));
    }

    @Test
    void mediaDelEquipoCadaJugadorPesaIgual() {
        MetricasRondas a = Rondas.metricas(filas());
        MetricasRondas b = Rondas.metricas(filas().subList(0, 1));
        MetricasRondas media = Rondas.mediaEquipo(List.of(a, b));
        assertThat(media.partidas()).isEqualTo(3);
        assertThat(media.rondas()).isEqualTo(6);
        assertThat(media.kast()).isEqualTo(90.0); // (80 + 100) / 2
        assertThat(Rondas.mediaEquipo(List.of())).isNull();
        ResumenDemos r = Rondas.resumen(filas(), List.of(filas().subList(0, 1), List.of()));
        assertThat(r.equipo().kast()).isEqualTo(100.0); // el que no tiene rondas no cuenta
    }

    @Test
    void mapaDeCalorConElFondoAclaradoYLasZonasEnSuMediana() {
        List<Rondas.PuntoFondo> fondo = new ArrayList<>();
        for (int i = 0; i < 6000; i++) {
            fondo.add(new Rondas.PuntoFondo(i % 2 == 0 ? 10 : 20, 5, i < 3000 ? "Banana" : "Arch"));
        }
        CalorMapa calor = Rondas.calor("de_inferno", List.of("de_inferno"), filas(), fondo);
        assertThat(calor.muertes()).hasSize(3);
        assertThat(calor.muertes().getFirst().zona()).isEqualTo("Banana");
        assertThat(calor.fondo()).hasSizeLessThanOrEqualTo(Rondas.MAX_FONDO);
        assertThat(calor.zonas()).extracting(z -> z.zona()).containsExactly("Arch", "Banana");
        assertThat(calor.zonas().getFirst().x()).isEqualTo(15.0);
        // Sin fondo, las etiquetas salen de sus muertes (si hay bastantes en la zona).
        assertThat(Rondas.calor("de_inferno", List.of(), filas(), List.of()).zonas()).isEmpty();
    }

    @Test
    void nombreDeLasZonas() {
        assertThat(Rondas.nombreZona("TopofMid")).isEqualTo("Top of Mid");
        assertThat(Rondas.nombreZona("BombsiteA")).isEqualTo("Bombsite A");
        assertThat(Rondas.nombreZona("CTSpawn")).isEqualTo("CT Spawn");
        assertThat(Rondas.nombreZona("TRamp")).isEqualTo("T Ramp");
        assertThat(Rondas.nombreZona("LongDoors")).isEqualTo("Long Doors");
        assertThat(Rondas.nombreZona("Banana")).isEqualTo("Banana");
        assertThat(Rondas.nombreZona("Roof")).isEqualTo("Roof");
        assertThat(Rondas.nombreZona(" ")).isNull();
        assertThat(Rondas.nombreZona(null)).isNull();
    }

    @Test
    void mapasJugadosElDeMasRondasPrimero() {
        List<FilaRonda> varias = new ArrayList<>(filas());
        varias.add(new FilaRonda(3, DIA, "de_nuke", 1, "CT", true, 0, 0, 0, 0, 0, false, null, null, null, false, 0,
                null, null, null, true));
        assertThat(Rondas.mapasJugados(varias)).containsExactly("de_inferno", "de_nuke");
    }
}
