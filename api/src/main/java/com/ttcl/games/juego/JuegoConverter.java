package com.ttcl.games.juego;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda el juego por su código ("cs2"), no por el nombre del enum. */
@Converter(autoApply = true)
public class JuegoConverter implements AttributeConverter<Juego, String> {

    @Override
    public String convertToDatabaseColumn(Juego juego) {
        return juego == null ? null : juego.codigo();
    }

    @Override
    public Juego convertToEntityAttribute(String codigo) {
        return codigo == null ? null : Juego.desde(codigo);
    }
}
