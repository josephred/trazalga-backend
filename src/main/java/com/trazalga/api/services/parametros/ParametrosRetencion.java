package com.trazalga.api.services.parametros;

/**
 * Registro inmutable con los parámetros normativos del dominio de Retención y Bodega Virtual (TA.2).
 */
public record ParametrosRetencion(
        boolean bloqueoActivo,
        int bloqueoHumedoHoras,
        int bloqueoSemihumedoHoras,
        int bloqueoSemisecoHoras,
        String bloqueoEspecies,
        double preavisoPct,
        int humedoMaxHoras
) {}
