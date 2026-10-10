package com.ttcl.games.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.dominio.ConsejoDado;
import com.ttcl.games.dominio.Repositorios.ConsejoDadoRepo;
import com.ttcl.games.duende.DuendeCliente;
import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.ItemLote;
import com.ttcl.games.duende.DuendeModelos.JuegoContexto;
import com.ttcl.games.duende.DuendeModelos.JugadorContexto;
import com.ttcl.games.duende.DuendeModelos.PeticionChat;
import com.ttcl.games.duende.DuendeModelos.PeticionInsights;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.duende.DuendeModelos.Salud;
import com.ttcl.games.duende.DuendeNoDisponibleException;
import com.ttcl.games.juego.Juego;
import com.ttcl.games.stats.Modelos.FilaMomento;
import com.ttcl.games.stats.Modelos.FilaSinergia;
import com.ttcl.games.stats.Modelos.ResumenPeriodo;
import com.ttcl.games.stats.Periodo;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** La API entera contra H2 con los datos de ejemplo. El servicio Python del Duende se simula. */
@SpringBootTest(properties = {"ttcl.demo=true", "ttcl.equipo-json=", "ttcl.faceit.api-key=", "ttcl.smite2.base="})
class ApiTest {

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    DuendeFalso duende;

    @Autowired
    ConsejoDadoRepo consejosDados;

    MockMvc mvc;

    /**
     * Sustituye al cliente HTTP del Duende. Escrito a mano en vez de con Mockito: el agente de Mockito no siempre
     * puede engancharse a la JVM (en Windows falla con rutas de usuario con tildes o eñes).
     */
    static class DuendeFalso extends DuendeCliente {
        List<ItemLote> lote = List.of();
        List<Insight> insights = List.of();
        RespuestaChat respuesta;
        RuntimeException fallo;
        PeticionChat ultimaChat;
        PeticionInsights ultimaInsights;
        List<PeticionInsights> ultimoLote;

        DuendeFalso(TtclProperties props) {
            super(props);
        }

        @Override
        public List<ItemLote> lote(List<PeticionInsights> peticiones) {
            ultimoLote = peticiones;
            if (fallo != null) {
                throw fallo;
            }
            return lote;
        }

        @Override
        public List<Insight> insights(PeticionInsights peticion) {
            ultimaInsights = peticion;
            if (fallo != null) {
                throw fallo;
            }
            return insights;
        }

        @Override
        public RespuestaChat chat(PeticionChat peticion) {
            ultimaChat = peticion;
            if (fallo != null) {
                throw fallo;
            }
            return respuesta;
        }

        @Override
        public Optional<Salud> salud() {
            return Optional.empty();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        DuendeFalso duendeFalso(TtclProperties props) {
            return new DuendeFalso(props);
        }
    }

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        duende.lote = List.of();
        duende.insights = List.of();
        duende.respuesta = null;
        duende.fallo = null;
        duende.ultimaChat = null;
        duende.ultimaInsights = null;
        duende.ultimoLote = null;
    }

    private static Insight insight(String nivel, String titulo) {
        return new Insight("x", nivel, "winrate", titulo, "texto", "consejo", List.of(), "pct");
    }

    @Test
    void equipoConElConsejoMasUrgenteDeCadaUno() throws Exception {
        duende.lote = List.of(
                new ItemLote("j1", Juego.SMITE2, List.of(insight("bien", "Buen farmeo"))),
                new ItemLote("j1", Juego.CS2, List.of(insight("alto", "Tus kills no se convierten en victorias"))));

        mvc.perform(get("/api/equipo?lang=es"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].slug").value("j1"))
                .andExpect(jsonPath("$[0].demo").value(true))
                .andExpect(jsonPath("$[0].resumenes", hasSize(2)))
                .andExpect(jsonPath("$[0].resumenes[0].juego").value("cs2"))
                .andExpect(jsonPath("$[0].consejo.juego").value("cs2"))
                .andExpect(jsonPath("$[0].consejo.titulo").value("Tus kills no se convierten en victorias"))
                .andExpect(jsonPath("$[1].consejo").doesNotExist());
    }

    @Test
    void equipoSinDuendeSigueFuncionando() throws Exception {
        duende.fallo = new DuendeNoDisponibleException("caído", null);

        mvc.perform(get("/api/equipo?juego=smite2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))) // Jugador 3 no juega a SMITE 2
                .andExpect(jsonPath("$[0].resumenes", hasSize(1)))
                .andExpect(jsonPath("$[0].resumenes[0].juego").value("smite2"));
    }

    @Test
    void perfilYDetallePorJuego() throws Exception {
        mvc.perform(get("/api/jugadores/j1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Jugador 1"))
                .andExpect(jsonPath("$.cuentas", hasSize(2)))
                .andExpect(jsonPath("$.resumenes[0].forma").value(org.hamcrest.Matchers.startsWith("DD")));

        mvc.perform(get("/api/jugadores/j1/juegos/cs2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.datosMedios.adr").isNumber())
                .andExpect(jsonPath("$.resumen.datosMedios.entry_pct").isNumber())
                .andExpect(jsonPath("$.reciente.partidas").value(10))
                .andExpect(jsonPath("$.equipo.jugadores").value(2))
                .andExpect(jsonPath("$.serie", hasSize(20)))
                .andExpect(jsonPath("$.desglose[0].clave").value(org.hamcrest.Matchers.startsWith("de_")));

        mvc.perform(get("/api/jugadores/j4/juegos/cs2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jugadores/nadie")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jugadores/j1/juegos/fortnite")).andExpect(status().isBadRequest());
    }

    @Test
    void rolDeCadaCuentaEnElPerfilYParaElDuende() throws Exception {
        mvc.perform(get("/api/jugadores/j2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentas[0].juego").value("cs2"))
                .andExpect(jsonPath("$.cuentas[0].rol").value("soporte"))
                .andExpect(jsonPath("$.cuentas[1].juego").value("smite2"))
                .andExpect(jsonPath("$.cuentas[1].rol").value("guardian"));

        mvc.perform(get("/api/jugadores/j2/consejos?juego=smite2")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.juego()).isEqualTo(Juego.SMITE2);
        assertThat(duende.ultimaInsights.rol()).isEqualTo("guardian");

        mvc.perform(get("/api/equipo?juego=cs2")).andExpect(status().isOk());
        assertThat(duende.ultimoLote)
                .extracting(p -> p.jugador().slug() + ":" + p.rol())
                .containsExactly("j1:rifler", "j2:soporte", "j3:entry");

        duende.respuesta = new RespuestaChat("Hola", "reglas", null, List.of());
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"¿Quién es el mejor?\"}]}"))
                .andExpect(status().isOk());
        assertThat(duende.ultimaChat.equipo())
                .filteredOn(j -> j.slug().equals("j4"))
                .singleElement()
                .satisfies(j -> assertThat(j.juegos()).extracting(JuegoContexto::rol).containsExactly("jungla"));
    }

    /** GET que tiene que ir bien, con la respuesta ya leída. */
    private JsonNode json(String url) throws Exception {
        String cuerpo = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonMapper.builder().build().readTree(cuerpo);
    }

    @Test
    void sinergiasDeUnJugadorConLosDatosDeEjemplo() throws Exception {
        JsonNode s = json("/api/jugadores/j1/sinergias?juego=cs2");
        int total = json("/api/jugadores/j1/juegos/cs2").get("resumen").get("partidas").asInt();

        // Compañeros de CS2 de Jugador 1, el que más partidas juntos primero.
        List<JsonNode> filas = new ArrayList<>();
        s.get("companeros").forEach(filas::add);
        assertThat(filas).extracting(f -> f.get("slug").asString()).containsExactly("j2", "j3");
        assertThat(filas.getFirst().get("nombre").asString()).isEqualTo("Jugador 2");
        filas.add(s.get("solo"));
        for (JsonNode f : filas) {
            assertThat(f.get("partidas").asInt()).isGreaterThanOrEqualTo(3);
            assertThat(f.get("partidas").asInt() + f.get("partidasSin").asInt()).isEqualTo(total); // con + sin = todas
            assertThat(f.get("winrate").isNumber()).isTrue();
        }

        mvc.perform(get("/api/jugadores/j4/sinergias?juego=cs2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jugadores/j1/sinergias")).andExpect(status().isBadRequest());
    }

    @Test
    void duosYTriosDelEquipo() throws Exception {
        mvc.perform(get("/api/equipo/grupos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].juego").value("cs2"))
                .andExpect(jsonPath("$[0].duos", hasSize(3))) // los tres de CS2, de dos en dos
                .andExpect(jsonPath("$[0].trios", hasSize(1)))
                .andExpect(jsonPath("$[0].trios[0].jugadores[*].slug", contains("j1", "j2", "j3")))
                .andExpect(jsonPath("$[0].duos[0].jugadores[0].nombre").isString());

        JsonNode smite = json("/api/equipo/grupos?juego=smite2");
        assertThat(smite).hasSize(1);
        assertThat(smite.get(0).get("juego").asString()).isEqualTo("smite2");
        List<Double> winrates = new ArrayList<>();
        smite.get(0).get("duos").forEach(d -> winrates.add(d.get("winrate").asDouble()));
        assertThat(winrates).hasSize(3).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void elDuendeRecibeLasSinergias() throws Exception {
        mvc.perform(get("/api/jugadores/j2/consejos?juego=cs2")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.sinergias().companeros())
                .extracting(FilaSinergia::slug)
                .containsExactlyInAnyOrder("j1", "j3");

        duende.respuesta = new RespuestaChat("Hola", "reglas", null, List.of());
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"¿Con quién juego mejor?\"}]}"))
                .andExpect(status().isOk());
        assertThat(duende.ultimaChat.equipo().getFirst().juegos())
                .allSatisfy(g -> assertThat(g.sinergias().companeros()).isNotEmpty());
    }

    /** Las filas de una lista de las sesiones, por clave. */
    private static Map<String, JsonNode> filas(JsonNode sesiones, String lista) {
        Map<String, JsonNode> filas = new LinkedHashMap<>();
        sesiones.get(lista).forEach(f -> filas.put(f.get("clave").asString(), f));
        return filas;
    }

    @Test
    void sesionesDeUnJugadorConLosDatosDeEjemplo() throws Exception {
        JsonNode s = json("/api/jugadores/j3/sesiones?juego=cs2");
        int total = json("/api/jugadores/j3/juegos/cs2").get("resumen").get("partidas").asInt();
        int sesiones = s.get("sesiones").asInt();

        assertThat(sesiones).isGreaterThanOrEqualTo(15);
        assertThat(s.get("partidasPorSesion").asDouble()).isEqualTo(Math.round(10.0 * total / sesiones) / 10.0);
        assertThat(filas(s, "porOrden")).containsOnlyKeys("1", "2", "3+");
        assertThat(filas(s, "porOrden").get("1").get("partidas").asInt()).isEqualTo(sesiones); // una 1ª por sesión
        assertThat(filas(s, "trasResultado")).containsOnlyKeys("victoria", "derrota");
        for (String lista : List.of("porOrden", "trasResultado", "porFranja")) {
            int suma = 0;
            for (JsonNode f : filas(s, lista).values()) {
                assertThat(f.get("partidas").asInt() + f.get("partidasResto").asInt()).isEqualTo(total);
                suma += f.get("partidas").asInt();
            }
            // Todas las partidas tienen orden y franja; tras otra van todas menos la 1ª de cada sesión.
            assertThat(suma).isEqualTo(lista.equals("trasResultado") ? total - sesiones : total);
        }

        mvc.perform(get("/api/jugadores/j4/sesiones?juego=cs2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jugadores/j3/sesiones")).andExpect(status().isBadRequest());
    }

    @Test
    void losDatosDeEjemploTienenTiltYUnaHoraBuena() throws Exception {
        // Jugador 3 gana mucho menos a partir de la 3ª seguida; Jugador 4, mucho más por la tarde.
        JsonNode tercera = filas(json("/api/jugadores/j3/sesiones?juego=cs2"), "porOrden").get("3+");
        assertThat(tercera.get("partidas").asInt()).isGreaterThanOrEqualTo(10);
        assertThat(tercera.get("winrateResto").asDouble() - tercera.get("winrate").asDouble()).isGreaterThanOrEqualTo(15);

        JsonNode tarde = filas(json("/api/jugadores/j4/sesiones?juego=smite2"), "porFranja").get("tarde");
        assertThat(tarde.get("partidas").asInt()).isGreaterThanOrEqualTo(10);
        assertThat(tarde.get("winrate").asDouble() - tarde.get("winrateResto").asDouble()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void elDuendeRecibeLasSesiones() throws Exception {
        mvc.perform(get("/api/jugadores/j3/consejos?juego=cs2")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.sesiones().sesiones()).isGreaterThanOrEqualTo(15);
        assertThat(duende.ultimaInsights.sesiones().porOrden()).extracting(FilaMomento::clave)
                .containsExactly("1", "2", "3+");

        duende.respuesta = new RespuestaChat("Hola", "reglas", null, List.of());
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"¿Cuándo juego mejor?\"}]}"))
                .andExpect(status().isOk());
        assertThat(duende.ultimaChat.equipo()).flatExtracting(JugadorContexto::juegos)
                .allSatisfy(g -> assertThat(g.sesiones().porFranja()).isNotEmpty());
    }

    @Test
    void elPeriodoRecortaLasPartidasQueCuentan() throws Exception {
        // Jugador 3 jugó 5 partidas de CS2 en los últimos 7 días: las de la racha con Jugador 2, todas ganadas.
        int todas = json("/api/jugadores/j3/juegos/cs2").get("resumen").get("partidas").asInt();
        int mes = json("/api/jugadores/j3/juegos/cs2?periodo=30d").get("resumen").get("partidas").asInt();
        JsonNode semana = json("/api/jugadores/j3/juegos/cs2?periodo=7d");
        assertThat(semana.get("resumen").get("partidas").asInt()).isEqualTo(5);
        assertThat(semana.get("resumen").get("winrate").asDouble()).isEqualTo(100.0);
        assertThat(semana.get("serie")).hasSize(5);
        assertThat(mes).isBetween(6, todas - 1);

        // El mismo recorte en el historial, el perfil, el ranking y el cara a cara.
        JsonNode historial = json("/api/jugadores/j3/partidas?juego=cs2&periodo=7d");
        assertThat(historial.get("total").asInt()).isEqualTo(5);
        Instant hace7Dias = Instant.now().minus(Duration.ofDays(7));
        historial.get("items").forEach(p -> assertThat(Instant.parse(p.get("jugadaEn").asString())).isAfter(hace7Dias));
        assertThat(json("/api/jugadores/j3?periodo=7d").get("resumenes").get(0).get("partidas").asInt()).isEqualTo(5);
        JsonNode ranking = json("/api/ranking?juego=smite2&periodo=7d");
        assertThat(ranking.get("filas")).hasSize(1); // a SMITE 2, esta semana, solo ha jugado Jugador 4
        assertThat(ranking.get("filas").get(0).get("slug").asString()).isEqualTo("j4");
        assertThat(json("/api/comparar?a=j3&b=j1&juego=cs2&periodo=7d").get("resumenA").get("partidas").asInt())
                .isEqualTo(5);

        mvc.perform(get("/api/ranking?juego=cs2&periodo=1a")).andExpect(status().isBadRequest());
    }

    @Test
    void unPeriodoSinPartidasDaDatosVaciosYNoUn404() throws Exception {
        // Jugador 1 lleva semanas sin jugar a SMITE 2.
        JsonNode d = json("/api/jugadores/j1/juegos/smite2?periodo=7d");
        assertThat(d.get("resumen").get("partidas").asInt()).isZero();
        assertThat(d.get("serie")).isEmpty();
        assertThat(d.get("desglose")).isEmpty();
        assertThat(json("/api/jugadores/j1/sinergias?juego=smite2&periodo=7d").get("companeros")).isEmpty();
        assertThat(json("/api/jugadores/j1/sesiones?juego=smite2&periodo=7d").get("sesiones").asInt()).isZero();
        assertThat(json("/api/jugadores/j1/partidas?juego=smite2&periodo=7d").get("total").asInt()).isZero();
        List<String> juegos = new ArrayList<>();
        json("/api/jugadores/j1?periodo=7d").get("resumenes").forEach(r -> juegos.add(r.get("juego").asString()));
        assertThat(juegos).containsExactly("cs2");
        mvc.perform(get("/api/comparar?a=j1&b=j4&juego=smite2&periodo=7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumenA").doesNotExist())
                .andExpect(jsonPath("$.filas", hasSize(0)));

        mvc.perform(get("/api/jugadores/j1/consejos?juego=smite2&periodo=7d")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.resumen().partidas()).isZero();

        // Quien no ha jugado nunca a ese juego sigue siendo un 404, pida el periodo que pida.
        mvc.perform(get("/api/jugadores/j3/juegos/smite2?periodo=7d")).andExpect(status().isNotFound());
    }

    @Test
    void elChatRecibeLosUltimosDiasYElPeriodoDeLaPagina() throws Exception {
        duende.respuesta = new RespuestaChat("Hola", "reglas", null, List.of());
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodo": "7d", "foco": ["j4"],
                                 "mensajes": [{"rol": "usuario", "texto": "¿Cómo voy esta semana?"}]}
                                """))
                .andExpect(status().isOk());
        assertThat(duende.ultimaChat.periodo()).isEqualTo(Periodo.SIETE_DIAS);

        JuegoContexto cuatro = juegoDe(duende.ultimaChat, "j4", Juego.SMITE2);
        assertThat(cuatro.periodos()).extracting(ResumenPeriodo::periodo)
                .containsExactly(Periodo.SIETE_DIAS, Periodo.TREINTA_DIAS);
        assertThat(cuatro.periodos().getFirst().resumen().partidas()).isEqualTo(5);
        assertThat(cuatro.periodos().getFirst().equipo()).isNull(); // nadie más jugó a SMITE 2 esta semana
        assertThat(cuatro.periodos().get(1).equipo().jugadores()).isEqualTo(2);
        // Jugador 1, sin SMITE 2 esta semana: solo el resumen del mes.
        assertThat(juegoDe(duende.ultimaChat, "j1", Juego.SMITE2).periodos()).extracting(ResumenPeriodo::periodo)
                .containsExactly(Periodo.TREINTA_DIAS);

        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"hola\"}]}"))
                .andExpect(status().isOk());
        assertThat(duende.ultimaChat.periodo()).isNull();
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodo\": \"siempre\", \"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"hola\"}]}"))
                .andExpect(status().isBadRequest());
    }

    private static JuegoContexto juegoDe(PeticionChat chat, String slug, Juego juego) {
        return chat.equipo().stream()
                .filter(j -> j.slug().equals(slug))
                .flatMap(j -> j.juegos().stream())
                .filter(g -> g.juego() == juego)
                .findFirst()
                .orElseThrow();
    }

    // ─── Memoria de consejos (P6) ───────────────────────────────────────────

    private List<ConsejoDado> consejosDe(String slug) {
        return consejosDados.findAllDesde(Instant.EPOCH).stream()
                .filter(c -> c.getJugador().getSlug().equals(slug))
                .toList();
    }

    @Test
    void losConsejosNuevosSeApuntanUnaVezYSoloConTodasLasPartidas() throws Exception {
        // Jugador 2 no tiene consejos en los datos de ejemplo.
        duende.insights = List.of(
                new Insight("debil_adr", "alto", "adr", "Poco daño", "t", "c", List.of(), "int"),
                new Insight("tilt_sesion", "medio", null, "Tilt", "t", "c", List.of(), "pct"),
                new Insight("fuerte_kd", "bien", "kd", "Buen K/D", "t", "c", List.of(), "dec"), // lo hace bien
                new Insight("consejo_no_funciona", "medio", "kd", "Sigue igual", "t", "c", List.of(), "dec"));
        try {
            mvc.perform(get("/api/jugadores/j2/consejos?juego=cs2")).andExpect(status().isOk());
            List<ConsejoDado> dados = consejosDe("j2");
            assertThat(dados).extracting(ConsejoDado::getInsight).containsExactlyInAnyOrder("debil_adr", "tilt_sesion");
            double adr = json("/api/jugadores/j2/juegos/cs2").get("resumen").get("datosMedios").get("adr").asDouble();
            ConsejoDado delAdr = dados.stream().filter(c -> c.getInsight().equals("debil_adr")).findFirst().orElseThrow();
            assertThat(delAdr.getValor()).isEqualTo(adr); // el valor de ese día, con todas las partidas
            assertThat(delAdr.getNivel()).isEqualTo("alto");
            assertThat(delAdr.getJuego()).isEqualTo(Juego.CS2);

            // Otra vez (o en inglés): no se repite. Con un periodo, no se apunta nada.
            mvc.perform(get("/api/jugadores/j2/consejos?juego=cs2&lang=en")).andExpect(status().isOk());
            duende.insights = List.of(new Insight("debil_hs_pct", "alto", "hs_pct", "HS", "t", "c", List.of(), "pct"));
            mvc.perform(get("/api/jugadores/j2/consejos?juego=cs2&periodo=7d")).andExpect(status().isOk());
            assertThat(consejosDe("j2")).hasSize(2);

            // A partir de ahora, el Duende recibe su seguimiento (aún sin partidas desde entonces).
            mvc.perform(get("/api/jugadores/j2/consejos?juego=cs2")).andExpect(status().isOk());
            assertThat(duende.ultimaInsights.seguimiento()).singleElement().satisfies(s -> {
                assertThat(s.insight()).isEqualTo("debil_adr");
                assertThat(s.dias()).isZero();
                assertThat(s.partidasDesde()).isZero();
                assertThat(s.valorDesde()).isNull();
            });
        } finally {
            consejosDados.deleteAll(consejosDe("j2"));
        }
    }

    @Test
    void elDuendeRecibeElSeguimientoDeLosConsejosDeEjemplo() throws Exception {
        // A Jugador 3 se le avisó del ADR hace 12 días y ha mejorado; a Jugador 4, de las muertes, y muere más.
        mvc.perform(get("/api/jugadores/j3/consejos?juego=cs2")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.seguimiento()).singleElement().satisfies(s -> {
            assertThat(s.insight()).isEqualTo("debil_adr");
            assertThat(s.metrica()).isEqualTo("adr");
            assertThat(s.dias()).isEqualTo(12);
            assertThat(s.partidasDesde()).isGreaterThanOrEqualTo(5);
            assertThat(s.valorDesde()).isGreaterThan(s.valor() * 1.1);
        });
        mvc.perform(get("/api/jugadores/j4/consejos?juego=smite2")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.seguimiento()).singleElement().satisfies(s -> {
            assertThat(s.metrica()).isEqualTo("muertes_media");
            assertThat(s.valorDesde()).isGreaterThan(s.valor());
        });

        // Con un periodo, sin seguimiento (es con todas las partidas); en el chat, sí.
        mvc.perform(get("/api/jugadores/j3/consejos?juego=cs2&periodo=30d")).andExpect(status().isOk());
        assertThat(duende.ultimaInsights.seguimiento()).isEmpty();
        duende.respuesta = new RespuestaChat("Hola", "reglas", null, List.of());
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"¿Ha funcionado?\"}]}"))
                .andExpect(status().isOk());
        assertThat(juegoDe(duende.ultimaChat, "j3", Juego.CS2).seguimiento()).hasSize(1);
        assertThat(juegoDe(duende.ultimaChat, "j1", Juego.CS2).seguimiento()).isEmpty();
    }

    @Test
    void historialPaginadoConCompaneros() throws Exception {
        mvc.perform(get("/api/jugadores/j2/partidas?juego=cs2&limite=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.total", greaterThan(5)))
                .andExpect(jsonPath("$.items[0].companeros[0]").value("Jugador 3"));
    }

    @Test
    void comparacionRankingYBusqueda() throws Exception {
        mvc.perform(get("/api/comparar?a=j1&b=j2&juego=cs2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.a.nombre").value("Jugador 1"))
                .andExpect(jsonPath("$.filas[0].metrica").value("partidas"))
                .andExpect(jsonPath("$.filas[1].metrica").value("winrate"));

        mvc.perform(get("/api/comparar?a=j3&b=j4&juego=cs2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumenB").doesNotExist())
                .andExpect(jsonPath("$.filas", hasSize(0)));

        mvc.perform(get("/api/ranking?juego=smite2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas", hasSize(3)))
                .andExpect(jsonPath("$.metricas[0]").value("winrate"));

        mvc.perform(get("/api/buscar?q=DEMO_tres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("j3"));
    }

    @Test
    void consejosSinDuendeDicenQueNoEstaDisponible() throws Exception {
        duende.fallo = new DuendeNoDisponibleException("caído", null);

        mvc.perform(get("/api/jugadores/j1/consejos?juego=cs2&lang=en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.insights", hasSize(0)));
    }

    @Test
    void chatMandaElContextoYFiltraLosSlugs() throws Exception {
        duende.respuesta = new RespuestaChat("Hola", "reglas", null, List.of("¿Qué hago bien?"));

        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lang": "en-GB", "juego": "cs2", "foco": ["j1", "nadie"],
                                 "mensajes": [{"rol": "usuario", "texto": "What should I improve?"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respuesta").value("Hola"))
                .andExpect(jsonPath("$.sugerencias[0]").value("¿Qué hago bien?"));

        PeticionChat enviada = duende.ultimaChat;
        assertThat(enviada.lang()).isEqualTo("en");
        assertThat(enviada.foco()).containsExactly("j1");
        assertThat(enviada.equipo()).hasSize(4);
        assertThat(enviada.equipo().getFirst().juegos()).hasSize(2);
    }

    @Test
    void chatValidaLaEntradaYAvisaSiElDuendeNoEsta() throws Exception {
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lang\": \"es\", \"mensajes\": []}"))
                .andExpect(status().isBadRequest());

        duende.fallo = new DuendeNoDisponibleException("caído", null);
        mvc.perform(post("/api/duende/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensajes\": [{\"rol\": \"usuario\", \"texto\": \"hola\"}]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void estado() throws Exception {
        mvc.perform(get("/api/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.ultimaSync").isString())
                .andExpect(jsonPath("$.fuentes.cs2").value(false))
                .andExpect(jsonPath("$.duende.disponible").value(false));
    }
}
