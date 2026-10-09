package com.ttcl.games.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Qué partidas cuentan: las de los últimos 7 días, las de los últimos 30 o todas. En la URL y en JSON va por su código
 * ({@code ?periodo=7d}); sin él, todas.
 */
public enum Periodo {
    SIETE_DIAS("7d", Duration.ofDays(7)),
    TREINTA_DIAS("30d", Duration.ofDays(30)),
    TODO("todo", null);

    /** Los que recortan: el chat recibe un resumen de cada uno para preguntas como "¿cómo voy esta semana?". */
    public static final List<Periodo> RECORTADOS = List.of(SIETE_DIAS, TREINTA_DIAS);

    private final String codigo;
    private final Duration duracion;

    Periodo(String codigo, Duration duracion) {
        this.codigo = codigo;
        this.duracion = duracion;
    }

    @JsonValue
    public String codigo() {
        return codigo;
    }

    /** Desde cuándo cuentan las partidas, o null si cuentan todas. */
    public Instant inicio(Instant ahora) {
        return duracion == null ? null : ahora.minus(duracion);
    }

    @JsonCreator
    public static Periodo de(String codigo) {
        return Arrays.stream(values())
                .filter(p -> p.codigo.equalsIgnoreCase(codigo == null ? "" : codigo.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Periodo desconocido: " + codigo));
    }
}
