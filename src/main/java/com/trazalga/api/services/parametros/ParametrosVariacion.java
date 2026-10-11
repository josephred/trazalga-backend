package com.trazalga.api.services.parametros;

/**
 * Registro inmutable con los parámetros normativos del dominio de Variación de Peso y Merma Biológica (TA.2).
 */
public record ParametrosVariacion(
        boolean bioPerdidaActivo,
        int bioHumedoDiasMinimosTransito,
        double bioHumedoMermaMinimaPct,
        double bioSecoMermaMaximaPct,
        boolean variacionPesoAlertaEquivalente
) {}
