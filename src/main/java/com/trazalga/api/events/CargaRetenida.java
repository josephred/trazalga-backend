package com.trazalga.api.events;

/**
 * Evento de dominio emitido cuando una carga es retenida/bloqueada (TA.5 / TR.3).
 */
public record CargaRetenida(
        Long marcaId,
        Long holderId
) {
}
