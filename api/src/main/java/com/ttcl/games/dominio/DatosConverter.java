package com.ttcl.games.dominio;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo específico de cada juego (ADR, HS%, daño, dios...) se guarda como JSON en una columna de texto: así el mismo
 * esquema vale para Postgres y para H2. Valores: números, texto o null; nunca objetos anidados.
 */
@Converter
public class DatosConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> TIPO = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> datos) {
        return datos == null ? "{}" : JSON.writeValueAsString(datos);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String texto) {
        if (texto == null || texto.isBlank()) {
            return new LinkedHashMap<>();
        }
        return JSON.readValue(texto, TIPO);
    }
}
