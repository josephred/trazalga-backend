package com.trazalga.api.services.parametros;

/**
 * Parámetros de la imputación cruzada por veda (refinamiento 23-10-2026, punto 1).
 *
 * @param activo        habilita la regla.
 * @param metodoEnVeda  nombre del método de extracción declarado que, estando en veda y sin cuota
 *                      propia, se imputa a otro método (por defecto BARRETEADO).
 * @param metodoDestino nombre del método cuya cuota recibe la imputación (por defecto VARADO).
 */
public record ParametrosImputacionVeda(
        boolean activo,
        String metodoEnVeda,
        String metodoDestino
) {}
