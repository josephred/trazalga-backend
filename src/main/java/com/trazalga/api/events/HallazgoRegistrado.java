package com.trazalga.api.events;

/**
 * Evento de dominio emitido cuando se registra un hallazgo/marca normativo (TA.5).
 */
public record HallazgoRegistrado(
        Long marcaId,
        String marca,
        String tipo,
        Long declaracionId,
        Long usuarioDeclaranteId,
        Long regionId
) {
}
