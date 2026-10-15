package com.trazalga.api.services.parametros;

/**
 * Registro inmutable con los parámetros normativos del dominio de Variación de Peso y Merma Biológica (TA.2, TV.1, TV.2).
 */
public record ParametrosVariacion(
        boolean bioPerdidaActivo,
        int bioHumedoDiasMinimosTransito,
        double bioHumedoMermaMinimaPct,
        double bioSecoMermaMaximaPct,
        boolean variacionPesoAlertaEquivalente,
        double variacionPesoUmbralGeneralPct,
        double riesgoVariacionRojoPct,
        double riesgoVariacionAmarilloPct
) {
    public ParametrosVariacion(
            boolean bioPerdidaActivo,
            int bioHumedoDiasMinimosTransito,
            double bioHumedoMermaMinimaPct,
            double bioSecoMermaMaximaPct,
            boolean variacionPesoAlertaEquivalente,
            double variacionPesoUmbralGeneralPct,
            double riesgoVariacionRojoPct
    ) {
        this(bioPerdidaActivo, bioHumedoDiasMinimosTransito, bioHumedoMermaMinimaPct,
                bioSecoMermaMaximaPct, variacionPesoAlertaEquivalente,
                variacionPesoUmbralGeneralPct, riesgoVariacionRojoPct, 5.0);
    }

    public double riesgoRojoPct() {
        return riesgoVariacionRojoPct;
    }
}
