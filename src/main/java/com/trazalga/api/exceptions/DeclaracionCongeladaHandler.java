package com.trazalga.api.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce {@link DeclaracionCongeladaException} a 423 Locked con un cuerpo que la app puede mostrar.
 * Sólo maneja esa excepción; el resto sigue con el manejo por defecto.
 */
@RestControllerAdvice
public class DeclaracionCongeladaHandler {

    @ExceptionHandler(DeclaracionCongeladaException.class)
    public ResponseEntity<Map<String, Object>> manejar(DeclaracionCongeladaException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.LOCKED.value());
        body.put("error", "DECLARACION_CONGELADA");
        body.put("message", e.getMessage());
        body.put("declaracionTipo", e.getDeclaracionTipo());
        body.put("declaracionId", e.getDeclaracionId());
        body.put("marcas", e.getMarcas());
        return ResponseEntity.status(HttpStatus.LOCKED).body(body);
    }
}
