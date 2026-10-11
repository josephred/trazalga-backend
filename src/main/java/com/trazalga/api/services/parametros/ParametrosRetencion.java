package com.trazalga.api.services.parametros;

/**
 * Registro inmutable con los parámetros normativos del dominio de Retención y Bodega Virtual (TA.2).
 */
public record ParametrosRetencion(
        boolean retencionActivo,
        int humedoMaxHoras,
        int semihumedoMaxHoras,
        int semisecoMaxHoras,
        double semaforoPreavisoPct,
        boolean bloqueoActivo,
        int bloqueoHumedoHoras,
        int bloqueoSemihumedoHoras,
        int bloqueoSemisecoHoras,
        String bloqueoEspecies,
        double bloqueoPreavisoPct
) {
    /**
     * Constructor de retrocompatibilidad con los campos originales (TA.2).
     */
    public ParametrosRetencion(
            boolean bloqueoActivo,
            int bloqueoHumedoHoras,
            int bloqueoSemihumedoHoras,
            int bloqueoSemisecoHoras,
            String bloqueoEspecies,
            double bloqueoPreavisoPct,
            int humedoMaxHoras
    ) {
        this(true, humedoMaxHoras, 72, 216, 80.0,
                bloqueoActivo, bloqueoHumedoHoras, bloqueoSemihumedoHoras, bloqueoSemisecoHoras,
                bloqueoEspecies, bloqueoPreavisoPct);
    }

    /**
     * Alias retrocompatible para preavisoPct
     */
    public double preavisoPct() {
        return bloqueoPreavisoPct;
    }
}
