package com.ttcl.games.juego;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * Juegos soportados. Añadir uno = un valor aquí, su adaptador en {@code sync} y sus métricas en
 * {@link com.ttcl.games.stats.Estadisticas}. En JSON y en la base se guarda el código ("cs2", "smite2").
 */
public enum Juego {
    CS2("cs2", "Counter-Strike 2", "mapa", List.of("adr", "hs_pct", "kr", "entry_pct", "clutch_pct", "mvps",
            "multikills", "dano_utilidad")),
    SMITE2("smite2", "SMITE 2", "dios", List.of("kda", "dano_min", "oro_min", "dano", "mitigado", "curacion"));

    private final String codigo;
    private final String nombre;
    private final String claveDesglose;
    private final List<String> metricasPropias;

    Juego(String codigo, String nombre, String claveDesglose, List<String> metricasPropias) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.claveDesglose = claveDesglose;
        this.metricasPropias = metricasPropias;
    }

    @JsonValue
    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    /** Campo de {@code datos} por el que se agrupa el desglose: mapa en CS2, dios en SMITE 2. */
    public String claveDesglose() {
        return claveDesglose;
    }

    /** Métricas específicas del juego, en el orden en que se muestran y se comparan. */
    public List<String> metricasPropias() {
        return metricasPropias;
    }

    @JsonCreator
    public static Juego desde(String codigo) {
        return Arrays.stream(values())
                .filter(j -> j.codigo.equalsIgnoreCase(codigo == null ? "" : codigo.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Juego desconocido: " + codigo));
    }
}
