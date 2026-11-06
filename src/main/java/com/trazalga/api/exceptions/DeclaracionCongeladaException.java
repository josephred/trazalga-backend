package com.trazalga.api.exceptions;

import java.util.List;

import com.trazalga.api.dto.AlertaDeclaracionDTO;

/**
 * La declaración tiene una alerta de fiscalización que la congela: su dueño no puede modificarla ni
 * eliminarla sin autorización del fiscalizador (refinamiento 30-10, punto 3). Se responde 423 Locked.
 */
public class DeclaracionCongeladaException extends RuntimeException {

    private final String declaracionTipo;
    private final Long declaracionId;
    private final List<AlertaDeclaracionDTO> marcas;

    public DeclaracionCongeladaException(String declaracionTipo, Long declaracionId, String message,
                                         List<AlertaDeclaracionDTO> marcas) {
        super(message);
        this.declaracionTipo = declaracionTipo;
        this.declaracionId = declaracionId;
        this.marcas = marcas != null ? List.copyOf(marcas) : List.of();
    }

    public String getDeclaracionTipo() {
        return declaracionTipo;
    }

    public Long getDeclaracionId() {
        return declaracionId;
    }

    public List<AlertaDeclaracionDTO> getMarcas() {
        return marcas;
    }
}
