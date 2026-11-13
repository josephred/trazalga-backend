package com.trazalga.api.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando una recepción en planta intenta reutilizar un número de lote interno
 * ya activo en la misma planta (NA.3). Mapea a HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class LoteInternoDuplicadoException extends RuntimeException {
    public LoteInternoDuplicadoException(String message) {
        super(message);
    }
}
