package com.trazalga.api.services.parametros;

/**
 * Registro inmutable con los parámetros normativos del dominio de Desembarques e Indicador 1 (TA.2).
 */
public record ParametrosDesembarque(
        double umbralAtipicoKg,
        boolean estadisticoActivo,
        double sigma,
        int ventanaDias,
        int minMuestras
) {}
