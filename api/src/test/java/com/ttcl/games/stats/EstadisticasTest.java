package com.ttcl.games.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.ComparativaNivel;
import com.ttcl.games.stats.Modelos.ConsejoAnterior;
import com.ttcl.games.stats.Modelos.FilaComparacion;
import com.ttcl.games.stats.Modelos.FilaDesglose;
import com.ttcl.games.stats.Modelos.FilaMomento;
import com.ttcl.games.stats.Modelos.FilaParticipacion;
import com.ttcl.games.stats.Modelos.FilaSinergia;
import com.ttcl.games.stats.Modelos.Grupo;
import com.ttcl.games.stats.Modelos.MediasEquipo;
import com.ttcl.games.stats.Modelos.MetricaNivel;
import com.ttcl.games.stats.Modelos.Miembro;
import com.ttcl.games.stats.Modelos.Presencia;
import com.ttcl.games.stats.Modelos.ResumenJuego;
import com.ttcl.games.stats.Modelos.SeguimientoConsejo;
import com.ttcl.games.stats.Modelos.Sesiones;
import com.ttcl.games.stats.Modelos.Sinergias;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EstadisticasTest {

    private static final Instant BASE = Instant.parse("2026-10-01T18:00:00Z");

    private static FilaParticipacion cs2(int dia, Boolean gano, int k, int d, int a, Map<String, Object> datos) {
        return new FilaParticipacion(dia, Juego.CS2, BASE.plusSeconds(dia * 86400L), null, "de_mirage", gano, k, d, a, datos);
    }

    @Test
    void resumeWinrateKdMediasYForma() {
        List<FilaParticipacion> filas = List.of(
                cs2(1, true, 20, 10, 5, Map.of("mapa", "de_mirage", "adr", 90.0, "hs_pct", 50)),
                cs2(2, false, 10, 20, 3, Map.of("mapa", "de_nuke", "adr", 70.0, "hs_pct", 40)),
                cs2(3, null, 15, 15, 4, Map.of("mapa", "de_nuke")));

        ResumenJuego r = Estadisticas.resumir(Juego.CS2, filas);

        assertThat(r.partidas()).isEqualTo(3);
        assertThat(r.victorias()).isEqualTo(1);
        assertThat(r.derrotas()).isEqualTo(1);
        assertThat(r.winrate()).isEqualTo(50.0); // la partida sin resultado no cuenta
        assertThat(r.kd()).isEqualTo(1.0); // 45 / 45
        assertThat(r.killsMedia()).isEqualTo(15.0);
        assertThat(r.datosMedios()).containsEntry("adr", 80.0).containsEntry("hs_pct", 45.0);
        assertThat(r.forma()).isEqualTo("?DV"); // la más reciente primero
        assertThat(r.ultimaPartida()).isEqualTo(BASE.plusSeconds(3 * 86400L));
    }

    @Test
    void entryYClutchSalenDeLosTotalesNoDeLaMediaDePorcentajes() {
        List<FilaParticipacion> filas = List.of(
                cs2(1, true, 1, 1, 0, Map.of("entry_intentos", 4, "entry_ganados", 3, "clutch_intentos", 1, "clutch_ganados", 0)),
                cs2(2, true, 1, 1, 0, Map.of("entry_intentos", 1, "entry_ganados", 0, "clutch_intentos", 1, "clutch_ganados", 1)));

        ResumenJuego r = Estadisticas.resumir(Juego.CS2, filas);

        assertThat(r.datosMedios()).containsEntry("entry_pct", 60.0); // 3/5, no la media de 75 % y 0 %
        assertThat(r.datosMedios()).containsEntry("clutch_pct", 50.0);
        assertThat(r.datosMedios()).doesNotContainKeys("entry_intentos", "entry_ganados");
    }

    @Test
    void smiteCalculaKdaYPorMinuto() {
        List<FilaParticipacion> filas = List.of(
                new FilaParticipacion(1, Juego.SMITE2, BASE, null, "Conquest", true, 6, 3, 9,
                        Map.of("dios", "Zeus", "dano", 30000, "oro", 15000, "minutos", 30.0)),
                new FilaParticipacion(2, Juego.SMITE2, BASE.plusSeconds(60), null, "Arena", false, 4, 5, 2,
                        Map.of("dios", "Ra", "dano", 10000, "oro", 5000, "minutos", 10.0)));

        ResumenJuego r = Estadisticas.resumir(Juego.SMITE2, filas);

        assertThat(r.datosMedios()).containsEntry("kda", 2.63); // (10 + 11) / 8
        assertThat(r.datosMedios()).containsEntry("dano_min", 1000.0); // 40000 / 40
        assertThat(r.datosMedios()).containsEntry("oro_min", 500.0);
        assertThat(r.datosMedios()).doesNotContainKey("minutos");
    }

    @Test
    void mediasDelEquipoPesanIgualCadaJugador() {
        ResumenJuego a = new ResumenJuego(Juego.CS2, 50, 25, 25, 50.0, 1.2, 20.0, 16.0, 4.0, Map.of("adr", 90.0), "", null);
        ResumenJuego b = new ResumenJuego(Juego.CS2, 2, 0, 2, 0.0, 0.8, 10.0, 12.0, null, Map.of("adr", 70.0), "", null);

        MediasEquipo m = Estadisticas.mediasEquipo(List.of(a, b));

        assertThat(m.jugadores()).isEqualTo(2);
        assertThat(m.winrate()).isEqualTo(25.0);
        assertThat(m.kd()).isEqualTo(1.0);
        assertThat(m.asistenciasMedia()).isEqualTo(4.0); // solo cuenta quien tiene dato
        assertThat(m.datosMedios()).containsEntry("adr", 80.0);
        assertThat(Estadisticas.mediasEquipo(List.of())).isNull();
    }

    @Test
    void desglosePorMapaOrdenadoPorPartidas() {
        List<FilaParticipacion> filas = new ArrayList<>();
        filas.add(cs2(1, true, 10, 5, 0, Map.of("mapa", "de_mirage")));
        filas.add(cs2(2, false, 5, 10, 0, Map.of("mapa", "de_nuke")));
        filas.add(cs2(3, false, 5, 10, 0, Map.of("mapa", "de_nuke")));

        List<FilaDesglose> d = Estadisticas.desglose(Juego.CS2, filas);

        assertThat(d).extracting(FilaDesglose::clave).containsExactly("de_nuke", "de_mirage");
        assertThat(d.getFirst().winrate()).isEqualTo(0.0);
        assertThat(d.getFirst().kd()).isEqualTo(0.5);
    }

    @Test
    void desdeUnInicioSoloCuentanLasPartidasDeEseMomentoEnAdelante() {
        List<FilaParticipacion> filas = List.of(
                cs2(1, true, 1, 1, 0, Map.of()), cs2(2, false, 1, 1, 0, Map.of()), cs2(3, true, 1, 1, 0, Map.of()));

        assertThat(Estadisticas.desde(filas, BASE.plusSeconds(2 * 86400L)))
                .extracting(FilaParticipacion::partidaId)
                .containsExactly(2L, 3L); // la del mismo instante cuenta
        assertThat(Estadisticas.desde(filas, null)).isSameAs(filas);
        assertThat(Estadisticas.desde(filas, BASE.plusSeconds(10 * 86400L))).isEmpty();
        assertThat(Estadisticas.resumir(Juego.CS2, List.of()).partidas()).isZero(); // y sin partidas, resumen vacío
    }

    @Test
    void serieCronologicaConLasUltimas() {
        List<FilaParticipacion> filas = List.of(
                cs2(3, true, 1, 1, 0, Map.of()), cs2(1, true, 1, 1, 0, Map.of()), cs2(2, false, 1, 1, 0, Map.of()));

        assertThat(Estadisticas.serie(Juego.CS2, filas, 2))
                .extracting(Modelos.PuntoSerie::partidaId)
                .containsExactly(2L, 3L);
    }

    @Test
    void comparaConVentajaSegunHaciaDondeEsMejor() {
        ResumenJuego a = new ResumenJuego(Juego.CS2, 10, 6, 4, 60.0, 1.1, 20.0, 18.0, 4.0, Map.of("adr", 80.0), "", null);
        ResumenJuego b = new ResumenJuego(Juego.CS2, 12, 5, 7, 41.7, 1.3, 22.0, 15.0, 4.0, Map.of("adr", 80.0), "", null);

        List<FilaComparacion> filas = Estadisticas.comparar(a, b);

        assertThat(filas.getFirst().metrica()).isEqualTo("partidas");
        assertThat(filas.getFirst().ventaja()).isNull();
        assertThat(fila(filas, "winrate").ventaja()).isEqualTo("a");
        assertThat(fila(filas, "kd").ventaja()).isEqualTo("b");
        assertThat(fila(filas, "muertes_media").ventaja()).isEqualTo("b"); // menos muertes es mejor
        assertThat(fila(filas, "adr").ventaja()).isNull(); // empate
        assertThat(filas).extracting(FilaComparacion::metrica).doesNotContain("hs_pct"); // sin datos de nadie
    }

    private static Presencia p(String slug, Boolean gano) {
        return new Presencia(slug, slug.toUpperCase(), gano);
    }

    @Test
    void sinergiasConCadaCompaneroDelMismoBandoYSolo() {
        // Partidas de "a": con b (3), solas (3), con c (1, no llega al mínimo) y una contra b, en bandos contrarios.
        Map<Long, List<Presencia>> presencias = Map.of(
                1L, List.of(p("a", true), p("b", true)),
                2L, List.of(p("a", false), p("b", false)),
                3L, List.of(p("a", true), p("b", true)),
                4L, List.of(p("a", true)),
                5L, List.of(p("a", false)),
                6L, List.of(p("a", false)),
                7L, List.of(p("a", true), p("c", true)),
                8L, List.of(p("a", true), p("b", false)));
        List<FilaParticipacion> filas = new ArrayList<>();
        presencias.forEach((id, ps) -> {
            Boolean gano = ps.getFirst().gano();
            filas.add(new FilaParticipacion(id, Juego.CS2, BASE.plusSeconds(id), null, "x", gano, 10, 10, 2, Map.of()));
        });

        Sinergias s = Estadisticas.sinergias(Juego.CS2, "a", filas, presencias);

        assertThat(s.companeros()).singleElement().satisfies(b -> {
            assertThat(b.slug()).isEqualTo("b");
            assertThat(b.nombre()).isEqualTo("B");
            assertThat(b.partidas()).isEqualTo(3); // la 8 no: b estaba en el otro bando
            assertThat(b.victorias()).isEqualTo(2);
            assertThat(b.winrate()).isEqualTo(66.7);
            assertThat(b.kd()).isEqualTo(1.0);
            assertThat(b.partidasSin()).isEqualTo(5);
            assertThat(b.winrateSin()).isEqualTo(60.0); // 4, 7 y 8 ganadas; 5 y 6 perdidas
        });
        FilaSinergia solo = s.solo();
        assertThat(solo.slug()).isNull();
        assertThat(solo.partidas()).isEqualTo(4); // 4, 5, 6 y la 8, sin nadie del equipo en su bando
        assertThat(solo.winrate()).isEqualTo(50.0);
        assertThat(solo.partidasSin()).isEqualTo(4);
        assertThat(solo.winrateSin()).isEqualTo(75.0);
    }

    @Test
    void sinergiasSinPartidasSuficientesNoDanFilas() {
        // Una partida con b y otra sola: ninguna llega a las 3 que hacen falta.
        Map<Long, List<Presencia>> presencias = Map.of(1L, List.of(p("a", true), p("b", true)), 2L, List.of(p("a", true)));
        List<FilaParticipacion> filas = List.of(cs2(1, true, 1, 1, 0, Map.of()), cs2(2, true, 1, 1, 0, Map.of()));

        Sinergias s = Estadisticas.sinergias(Juego.CS2, "a", filas, presencias);

        assertThat(s.companeros()).isEmpty();
        assertThat(s.solo()).isNull();
    }

    @Test
    void duosYTriosDelMismoBandoConElMejorPrimero() {
        Map<Long, List<Presencia>> presencias = Map.of(
                1L, List.of(p("a", true), p("b", true), p("c", true)),
                2L, List.of(p("a", true), p("b", true), p("c", false)), // c en el otro bando
                3L, List.of(p("b", false), p("a", false)),
                4L, List.of(p("a", true), p("c", true)),
                5L, List.of(p("c", true), p("a", true)),
                6L, List.of(p("b", true), p("c", true)),
                7L, List.of(p("a", false), p("b", false), p("c", false)),
                8L, List.of(p("a", true), p("b", true), p("c", true)));

        List<Grupo> duos = Estadisticas.grupos(presencias, 2);
        List<Grupo> trios = Estadisticas.grupos(presencias, 3);

        assertThat(duos).extracting(g -> g.jugadores().stream().map(Miembro::slug).toList())
                .containsExactly(List.of("a", "c"), List.of("b", "c"), List.of("a", "b"));
        assertThat(duos).extracting(Grupo::partidas).containsExactly(5, 4, 5);
        assertThat(duos).extracting(Grupo::winrate).containsExactly(80.0, 75.0, 60.0);
        assertThat(duos.getFirst().jugadores()).extracting(Miembro::nombre).containsExactly("A", "C");
        assertThat(trios).singleElement().satisfies(t -> {
            assertThat(t.partidas()).isEqualTo(3); // 1, 7 y 8: en la 2, c estaba enfrente
            assertThat(t.victorias()).isEqualTo(2);
            assertThat(t.winrate()).isEqualTo(66.7);
        });
    }

    @Test
    void aIgualdadDeWinrateVaPrimeroElGrupoQueMasHaJugado() {
        Map<Long, List<Presencia>> presencias = new HashMap<>();
        for (long i = 1; i <= 3; i++) {
            presencias.put(i, List.of(p("a", true), p("b", true)));
        }
        for (long i = 4; i <= 9; i++) {
            presencias.put(i, List.of(p("c", true), p("d", true)));
        }

        assertThat(Estadisticas.grupos(presencias, 2)).extracting(Grupo::partidas).containsExactly(6, 3);
    }

    private static FilaParticipacion jugada(long id, String hora, Integer duracionSeg, Boolean gano) {
        return new FilaParticipacion(
                id, Juego.CS2, Instant.parse(hora), duracionSeg, "de_mirage", gano, 10, 10, 2, Map.of());
    }

    @Test
    void sesionesPorOrdenTrasResultadoYFranja() {
        List<FilaParticipacion> filas = List.of(
                // Sesión 1, por la mañana: 10 minutos entre una y otra.
                jugada(1, "2026-10-01T09:00:00Z", 1800, true),
                jugada(2, "2026-10-01T09:40:00Z", 1800, true),
                jugada(3, "2026-10-01T10:20:00Z", 1800, false),
                jugada(4, "2026-10-01T11:00:00Z", 1800, false),
                // Sesión 2, por la noche. Sin duración, la pausa se cuenta desde el principio: 44 minutos.
                jugada(5, "2026-10-01T21:00:00Z", null, false),
                jugada(6, "2026-10-01T21:44:00Z", 1800, null),
                // Sesión 3: la 6 acabó a las 22:14 y han pasado más de 45 minutos. Pasa de la medianoche.
                jugada(7, "2026-10-01T23:15:00Z", 1800, true),
                jugada(8, "2026-10-02T00:00:00Z", 1800, true),
                // Sesión 4, por la tarde, de una sola partida.
                jugada(9, "2026-10-02T15:00:00Z", 1800, true));

        Sesiones s = Estadisticas.sesiones(Juego.CS2, filas, ZoneOffset.UTC);

        assertThat(s.sesiones()).isEqualTo(4);
        assertThat(s.partidasPorSesion()).isEqualTo(2.3); // 9 / 4
        assertThat(s.porOrden()).containsExactly(
                new FilaMomento("1", 4, 3, 75.0, 1.0, 5, 50.0), // 1, 5, 7 y 9
                new FilaMomento("2", 3, 2, 100.0, 1.0, 6, 50.0), // 2, 6 (sin resultado) y 8
                new FilaMomento("3+", 2, 0, 0.0, 1.0, 7, 83.3)); // 3 y 4
        assertThat(s.trasResultado()).containsExactly(
                new FilaMomento("victoria", 3, 2, 66.7, 1.0, 6, 60.0), // 2, 3 y 8
                new FilaMomento("derrota", 2, 0, 0.0, 1.0, 7, 71.4)); // 4 y 6; la 9 empieza sesión
        assertThat(s.porFranja()).extracting(FilaMomento::clave, FilaMomento::partidas)
                .containsExactly(tuple("manana", 4), tuple("tarde", 1), tuple("noche", 3), tuple("madrugada", 1));
    }

    @Test
    void laHoraDelDiaEsLaDeLaZonaDelEquipo() {
        // 22:30 en UTC son las 00:30 en Madrid (horario de verano).
        List<FilaParticipacion> filas = List.of(jugada(1, "2026-10-01T22:30:00Z", 1800, true));

        assertThat(Estadisticas.sesiones(Juego.CS2, filas, ZoneOffset.UTC).porFranja())
                .extracting(FilaMomento::clave).containsExactly("noche");
        assertThat(Estadisticas.sesiones(Juego.CS2, filas, ZoneId.of("Europe/Madrid")).porFranja())
                .extracting(FilaMomento::clave).containsExactly("madrugada");
        assertThat(List.of(0, 5, 6, 13, 14, 19, 20, 23)).extracting(Estadisticas::franja).containsExactly(
                "madrugada", "madrugada", "manana", "manana", "tarde", "tarde", "noche", "noche");
    }

    @Test
    void conUnaSolaPartidaNoHayRestoNiPartidasTrasOtra() {
        Sesiones s = Estadisticas.sesiones(
                Juego.CS2, List.of(jugada(1, "2026-10-01T18:00:00Z", 1800, true)), ZoneOffset.UTC);

        assertThat(s.sesiones()).isEqualTo(1);
        assertThat(s.porOrden()).containsExactly(new FilaMomento("1", 1, 1, 100.0, 1.0, 0, null));
        assertThat(s.trasResultado()).isEmpty();
        assertThat(Estadisticas.sesiones(Juego.CS2, List.of(), ZoneOffset.UTC))
                .isEqualTo(new Sesiones(0, null, List.of(), List.of(), List.of()));
    }

    @Test
    void seguimientoDeCadaConsejoDesdeLaPrimeraVezQueSeDio() {
        Instant dado = BASE.plusSeconds(2 * 86400L);
        List<FilaParticipacion> filas = List.of(
                cs2(1, true, 10, 10, 2, Map.of("adr", 60.0)), // antes del consejo: no cuenta
                cs2(3, true, 10, 10, 2, Map.of("adr", 90.0)),
                cs2(4, false, 10, 10, 2, Map.of("adr", 100.0)));
        List<ConsejoAnterior> consejos = List.of(
                new ConsejoAnterior("debil_adr", "adr", 75.0, dado.plusSeconds(3 * 86400L)), // repetido: vale el primero
                new ConsejoAnterior("debil_adr", "adr", 70.0, dado),
                new ConsejoAnterior("tilt_sesion", null, null, dado), // sin métrica: no hay nada que seguir
                new ConsejoAnterior("debil_kd", "kd", 0.8, BASE.minusSeconds(70 * 86400L)), // hace más de 60 días
                new ConsejoAnterior("debil_hs_pct", "hs_pct", 30.0, BASE.plusSeconds(9 * 86400L)));
        Instant ahora = BASE.plusSeconds(10 * 86400L);

        assertThat(Estadisticas.seguimiento(Juego.CS2, filas, consejos, ahora)).containsExactly(
                new SeguimientoConsejo("debil_adr", "adr", 70.0, dado, 8, 2, 95.0),
                // Sin partidas desde entonces, aún no se sabe.
                new SeguimientoConsejo("debil_hs_pct", "hs_pct", 30.0, BASE.plusSeconds(9 * 86400L), 1, 0, null));
    }

    @Test
    void comparativaConLasPartidasDeSuNivel() {
        // 60 partidas de nivel 6: ADR de 61 a 120, 10 kills y 0, 1 o 2 muertes, y una entrada ganada de cada dos.
        List<FilaParticipacion> muestras = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            muestras.add(cs2(i, null, 10, i % 3, 1, Map.of("adr", 61.0 + i, "entry_intentos", 2, "entry_ganados", 1)));
        }
        ResumenJuego tu = Estadisticas.resumir(Juego.CS2, List.of(cs2(1, true, 20, 10, 5, Map.of("adr", 75.0))));

        ComparativaNivel c = Estadisticas.comparativaNivel(Juego.CS2, 6, 1290, tu, muestras);

        assertThat(c.nivel()).isEqualTo(6);
        assertThat(c.elo()).isEqualTo(1290);
        assertThat(c.partidas()).isEqualTo(60);
        // La mediana de sus partidas, y en cuántas lo hacen peor que él: 14 por debajo y 1 igual (cuenta la mitad).
        assertThat(nivel(c, "adr")).isEqualTo(new MetricaNivel("adr", 90.5, 24.2, 60));
        // K/D de cada partida: sin muertes cuenta como una (10, 10 y 5): mediana 10 y él, con 2, el peor.
        assertThat(nivel(c, "kd")).isEqualTo(new MetricaNivel("kd", 10.0, 0.0, 60));
        // En muertes no se gira: el percentil dice cuántas partidas tienen menos que él.
        assertThat(nivel(c, "muertes_media")).isEqualTo(new MetricaNivel("muertes_media", 1.0, 100.0, 60));
        // Las entradas salen del total del nivel, sin percentil.
        assertThat(nivel(c, "entry_pct")).isEqualTo(new MetricaNivel("entry_pct", 50.0, null, 60));
        // El winrate no se compara (en tu nivel es un 50 %); lo que no tiene dato, tampoco.
        assertThat(c.metricas()).extracting(MetricaNivel::metrica).doesNotContain("winrate", "hs_pct", "clutch_pct");
    }

    @Test
    void sinNivelNoHayComparativaYConPocasPartidasSoloElNivel() {
        ResumenJuego tu = Estadisticas.resumir(Juego.CS2, List.of(cs2(1, true, 20, 10, 5, Map.of("adr", 75.0))));
        assertThat(Estadisticas.comparativaNivel(Juego.CS2, null, null, tu, List.of())).isNull();

        List<FilaParticipacion> pocas = new ArrayList<>();
        for (int i = 0; i < Estadisticas.MIN_MUESTRAS_NIVEL - 1; i++) {
            pocas.add(cs2(i, true, 10, 10, 1, Map.of("adr", 80.0, "entry_intentos", 2, "entry_ganados", 1)));
        }
        assertThat(Estadisticas.comparativaNivel(Juego.CS2, 3, null, tu, pocas))
                .isEqualTo(new ComparativaNivel(3, null, 49, List.of()));
    }

    private static MetricaNivel nivel(ComparativaNivel c, String metrica) {
        return c.metricas().stream().filter(m -> m.metrica().equals(metrica)).findFirst().orElseThrow();
    }

    private static FilaComparacion fila(List<FilaComparacion> filas, String metrica) {
        return filas.stream().filter(f -> f.metrica().equals(metrica)).findFirst().orElseThrow();
    }
}
