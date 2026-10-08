package com.ttcl.games.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcl.games.dominio.Cuenta;
import com.ttcl.games.dominio.Jugador;
import com.ttcl.games.dominio.Repositorios.CuentaRepo;
import com.ttcl.games.dominio.Repositorios.JugadorRepo;
import com.ttcl.games.juego.Juego;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Carga del equipo real desde config/equipo.json. Usa su propia base H2: la de por defecto vive mientras dure la JVM
 * y la comparten todos los tests, y aquí no debe haber datos de ejemplo (ni dejar jugadores para los demás).
 */
@SpringBootTest(properties = {
    "ttcl.demo=false",
    "ttcl.faceit.api-key=",
    "ttcl.smite2.base=",
    "spring.datasource.url=jdbc:h2:mem:equipo_seeder;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
            + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
})
class EquipoSeederTest {

    static Path equipo;

    @DynamicPropertySource
    static void equipoJson(DynamicPropertyRegistry registro) throws IOException {
        equipo = Files.createTempFile("equipo", ".json");
        Files.writeString(equipo, "[]");
        registro.add("ttcl.equipo-json", equipo::toString);
    }

    @AfterAll
    static void borrar() throws IOException {
        Files.deleteIfExists(equipo);
    }

    @Autowired
    EquipoSeeder seeder;

    @Autowired
    JugadorRepo jugadores;

    @Autowired
    CuentaRepo cuentas;

    @Autowired
    WebApplicationContext contexto;

    /** Escribe el fichero y lo carga como en un arranque. */
    private void cargar(String json) throws Exception {
        Files.writeString(equipo, json);
        seeder.run(null);
    }

    private Cuenta cuenta(String slug, Juego juego) {
        Jugador j = jugadores.findBySlug(slug).orElseThrow();
        return cuentas.findByJugadorAndJuego(j, juego).orElseThrow();
    }

    @Test
    void leeElRolSinDistinguirMayusculasNiTildesYLoEnsenaEnElPerfil() throws Exception {
        cargar("""
                [{"slug": "ana", "nombre": "Ana", "cuentas": {
                    "cs2": {"nick": "ana_faceit", "rol": "Soporte"},
                    "smite2": {"nick": "AnaSmite", "rol": "Guardián"}}}]
                """);

        assertThat(cuenta("ana", Juego.CS2).getRol()).isEqualTo("soporte");
        assertThat(cuenta("ana", Juego.SMITE2).getRol()).isEqualTo("guardian");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        mvc.perform(get("/api/jugadores/ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentas[0].nick").value("ana_faceit"))
                .andExpect(jsonPath("$.cuentas[0].rol").value("soporte"))
                .andExpect(jsonPath("$.cuentas[1].rol").value("guardian"));
    }

    @Test
    void unRolQueNoEsDelJuegoOQueNoSeDiceDejaLaCuentaSinRol() throws Exception {
        cargar("""
                [{"slug": "bea", "nombre": "Bea", "cuentas": {
                    "cs2": {"nick": "bea_faceit", "rol": "francotiradora"},
                    "smite2": {"nick": "BeaSmite", "rol": "entry"}}},
                 {"slug": "carla", "nombre": "Carla", "cuentas": {"cs2": {"nick": "carla_faceit"}}}]
                """);

        assertThat(cuenta("bea", Juego.CS2).getRol()).isNull();
        assertThat(cuenta("bea", Juego.SMITE2).getRol()).isNull(); // entry es de CS2
        assertThat(cuenta("bea", Juego.CS2).getNombreExterno()).isEqualTo("bea_faceit"); // la cuenta sí se carga
        assertThat(cuenta("carla", Juego.CS2).getRol()).isNull();
    }

    @Test
    void cadaArranqueVuelveALeerElRolSinDuplicarNada() throws Exception {
        cargar("""
                [{"slug": "dani", "nombre": "Dani", "cuentas": {
                    "cs2": {"nick": "dani_faceit", "rol": "entry"},
                    "smite2": {"nick": "DaniSmite", "rol": "mid"}}}]
                """);
        Cuenta antes = cuenta("dani", Juego.CS2);
        antes.setExternalId("faceit-dani");
        cuentas.save(antes);
        long jugadoresAntes = jugadores.count();
        long cuentasAntes = cuentas.count();

        cargar("""
                [{"slug": "dani", "nombre": "Dani", "cuentas": {
                    "cs2": {"nick": "dani_faceit", "rol": "igl"},
                    "smite2": {"nick": "DaniSmite"}}}]
                """);

        assertThat(cuenta("dani", Juego.CS2).getRol()).isEqualTo("igl");
        assertThat(cuenta("dani", Juego.SMITE2).getRol()).isNull(); // quitarlo del fichero lo quita
        assertThat(cuenta("dani", Juego.CS2).getExternalId()).isEqualTo("faceit-dani"); // mismo nick: no se resuelve otra vez
        assertThat(jugadores.count()).isEqualTo(jugadoresAntes);
        assertThat(cuentas.count()).isEqualTo(cuentasAntes);
    }
}
