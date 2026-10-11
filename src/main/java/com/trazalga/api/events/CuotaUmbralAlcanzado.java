package com.trazalga.api.events;

/**
 * Evento de dominio emitido cuando el consumo de una cuota alcanza o supera el umbral configurado (TA.5 / TC.6).
 */
public record CuotaUmbralAlcanzado(
        Long cuotaId,
        Long personaId,
        Double pct
) {
}
