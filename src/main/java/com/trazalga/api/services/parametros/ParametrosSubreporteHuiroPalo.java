package com.trazalga.api.services.parametros;

import java.util.Locale;

/**
 * Parámetros del Subreporte Huiro Palo (refinamiento 23-10-2026, punto 3).
 * <p>
 * Al declarar huiro palo barreteado "sin fronda" se pierde la parte de la biomasa que corresponde a la
 * fronda. Según IFOP esa parte es el 22 % de la captura biológica. El subreporte estima los kilos que no
 * quedan declarados.
 *
 * @param activo      habilita la tarjeta.
 * @param especie     nombre de la especie en el maestro (por defecto HUIRO PALO).
 * @param metodo      nombre del método de extracción declarado (por defecto BARRETEADO).
 * @param composicion nombre de la composición de fronda (por defecto SIN FRONDA).
 * @param factorPct   porcentaje de fronda informado por IFOP (por defecto 22).
 * @param formula     {@link #FORMULA_SOBRE_SIN_FRONDA}: subreporte = captura sin fronda × factor;
 *                    {@link #FORMULA_SOBRE_TOTAL}: el factor es la parte de la biomasa total, de modo que
 *                    subreporte = captura sin fronda × factor / (1 − factor).
 */
public record ParametrosSubreporteHuiroPalo(
        boolean activo,
        String especie,
        String metodo,
        String composicion,
        double factorPct,
        String formula
) {

    public static final String FORMULA_SOBRE_SIN_FRONDA = "SOBRE_SIN_FRONDA";
    public static final String FORMULA_SOBRE_TOTAL = "SOBRE_TOTAL";

    public static ParametrosSubreporteHuiroPalo porDefecto() {
        return new ParametrosSubreporteHuiroPalo(true, "HUIRO PALO", "BARRETEADO", "SIN FRONDA", 22.0, FORMULA_SOBRE_SIN_FRONDA);
    }

    /** Fórmula normalizada; cualquier valor desconocido se trata como {@link #FORMULA_SOBRE_SIN_FRONDA}. */
    public String formulaNormalizada() {
        String f = formula != null ? formula.trim().toUpperCase(Locale.ROOT) : "";
        return FORMULA_SOBRE_TOTAL.equals(f) ? FORMULA_SOBRE_TOTAL : FORMULA_SOBRE_SIN_FRONDA;
    }

    /** El porcentaje es utilizable: mayor que 0 y menor que 100. */
    public boolean factorValido() {
        return !Double.isNaN(factorPct) && factorPct > 0.0 && factorPct < 100.0;
    }

    /**
     * Multiplicador que se aplica a la captura sin fronda para obtener los kilos no declarados.
     * Con 22 %: 0,22 sobre lo sin fronda, o 0,22 / 0,78 ≈ 0,2821 sobre el total.
     */
    public double multiplicador() {
        if (!factorValido()) {
            return 0.0;
        }
        double f = factorPct / 100.0;
        return FORMULA_SOBRE_TOTAL.equals(formulaNormalizada()) ? f / (1.0 - f) : f;
    }
}
