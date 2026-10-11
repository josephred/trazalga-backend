package com.trazalga.api.services.parametros;

/**
 * Registro inmutable con los parámetros normativos del dominio de Cuotas (TA.2).
 */
public record ParametrosCuota(
        boolean cierreAutomaticoVencimiento,
        int diasGraciaDeclaracion,
        String accionExtemporanea,
        String imputacionArmador,
        double umbralRestantePct
) {}
