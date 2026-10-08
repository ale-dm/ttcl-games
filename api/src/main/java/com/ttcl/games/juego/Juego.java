package com.ttcl.games.juego;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Juegos soportados. Añadir uno = un valor aquí, su adaptador en {@code sync} y sus métricas en
 * {@link com.ttcl.games.stats.Estadisticas}. En JSON y en la base se guarda el código ("cs2", "smite2").
 */
public enum Juego {
    CS2("cs2", "Counter-Strike 2", "mapa", List.of("adr", "hs_pct", "kr", "entry_pct", "clutch_pct", "mvps",
            "multikills", "dano_utilidad"),
            List.of("entry", "awp", "soporte", "lurker", "igl", "rifler")),
    SMITE2("smite2", "SMITE 2", "dios", List.of("kda", "dano_min", "oro_min", "dano", "mitigado", "curacion"),
            List.of("solo", "jungla", "mid", "guardian", "carry"));

    private final String codigo;
    private final String nombre;
    private final String claveDesglose;
    private final List<String> metricasPropias;
    private final List<String> roles;

    Juego(String codigo, String nombre, String claveDesglose, List<String> metricasPropias, List<String> roles) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.claveDesglose = claveDesglose;
        this.metricasPropias = metricasPropias;
        this.roles = roles;
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

    /** Roles que se pueden declarar en este juego. Son los que entiende el Duende (metricas.py, AJUSTES_ROL). */
    public List<String> roles() {
        return roles;
    }

    /** El rol tal y como lo guarda la API ("Guardián" → "guardian"), o vacío si no es uno de este juego. */
    public Optional<String> rol(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpio = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return roles.contains(limpio) ? Optional.of(limpio) : Optional.empty();
    }

    @JsonCreator
    public static Juego desde(String codigo) {
        return Arrays.stream(values())
                .filter(j -> j.codigo.equalsIgnoreCase(codigo == null ? "" : codigo.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Juego desconocido: " + codigo));
    }
}
