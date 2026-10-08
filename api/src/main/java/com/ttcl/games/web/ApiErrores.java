package com.ttcl.games.web;

import com.ttcl.games.duende.DuendeNoDisponibleException;
import com.ttcl.games.servicio.NoEncontradoException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores de la API como {@code {"error": "mensaje"}} con el código HTTP que toca. */
@RestControllerAdvice
public class ApiErrores {

    private static final Logger log = LoggerFactory.getLogger(ApiErrores.class);

    private static ResponseEntity<Map<String, String>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }

    @ExceptionHandler(NoEncontradoException.class)
    ResponseEntity<Map<String, String>> noEncontrado(NoEncontradoException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentTypeMismatchException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<Map<String, String>> peticionMala(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Petición no válida.");
    }

    @ExceptionHandler(DuendeNoDisponibleException.class)
    ResponseEntity<Map<String, String>> duende(DuendeNoDisponibleException e) {
        log.warn("Duende no disponible: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "El Duende no está disponible ahora mismo.");
    }
}
