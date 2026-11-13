package com.trazalga.api.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce validaciones de planta (422) y conflictos de lote interno duplicado (409)
 * a JSON legible por la aplicación móvil y la web.
 */
@RestControllerAdvice
public class PlantaValidacionHandler {

    @ExceptionHandler(ValidacionPlantaException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacionPlanta(ValidacionPlantaException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("error", "VALIDACION_PLANTA");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(LoteInternoDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> manejarLoteDuplicado(LoteInternoDuplicadoException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "LOTE_INTERNO_DUPLICADO");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
