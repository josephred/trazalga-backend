package com.trazalga.api.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando una declaración de planta no cumple las validaciones normativas
 * obligatorias (peso en romana, voucher, humedad verificada o versión mínima de app) (TP.2 / TP.3).
 * Mapea a HTTP 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class ValidacionPlantaException extends IllegalArgumentException {
    public ValidacionPlantaException(String message) {
        super(message);
    }
}
