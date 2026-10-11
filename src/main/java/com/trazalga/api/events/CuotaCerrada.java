package com.trazalga.api.events;

/**
 * Evento de dominio emitido cuando se cierra una cuota de extracción (TA.5 / TC.1).
 */
public record CuotaCerrada(
        Long cuotaId,
        String motivo
) {
}
