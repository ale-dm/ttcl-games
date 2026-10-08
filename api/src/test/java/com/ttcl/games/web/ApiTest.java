package com.ttcl.games.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcl.games.config.TtclProperties;
import com.ttcl.games.duende.DuendeCliente;
import com.ttcl.games.duende.DuendeModelos.Insight;
import com.ttcl.games.duende.DuendeModelos.ItemLote;
import com.ttcl.games.duende.DuendeModelos.JuegoContexto;
import com.ttcl.games.duende.DuendeModelos.PeticionChat;
import com.ttcl.games.duende.DuendeModelos.PeticionInsights;
import com.ttcl.games.duende.DuendeModelos.RespuestaChat;
import com.ttcl.games.duende.DuendeModelos.Salud;
import com.ttcl.games.duende.DuendeNoDisponibleException;
import com.ttcl.games.juego.Juego;
import java.util.List;
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

/** La API entera contra H2 con los datos de ejemplo. El servicio Python del Duende se simula. */
@SpringBootTest(properties = {"ttcl.demo=true", "ttcl.equipo-json=", "ttcl.faceit.api-key=", "ttcl.smite2.base="})
class ApiTest {

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    DuendeFalso duende;

    MockMvc mvc;

    /**
     * Sustituye al cliente HTTP del Duende. Escrito a mano en vez de con Mockito: el agente de Mockito no siempre
     * puede engancharse a la JVM (en Windows falla con rutas de usuario con tildes o eñes).
     */
    static class DuendeFalso extends DuendeCliente {
        List<ItemLote> lote = List.of();
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
            return List.of();
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
