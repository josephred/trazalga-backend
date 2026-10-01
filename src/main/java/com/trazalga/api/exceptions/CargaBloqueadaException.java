package com.trazalga.api.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CargaBloqueadaException extends RuntimeException {
    public CargaBloqueadaException(String message) {
        super(message);
    }
}
