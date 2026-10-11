package com.trazalga.api.services.hallazgos;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Value Object para criterio estructurado de hallazgos (TA.3).
 * Modela el parámetro evaluado, el umbral normativo o configurado,
 * el valor observado y la unidad de medida, con formateo textual en español de Chile (es-CL).
 */
public record CriterioHallazgo(
        String parametro,
        String umbral,
        String valorObservado,
        String unidad
) {
    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");

    /**
     * Redacta el criterio estructurado en lenguaje natural (es-CL).
     * Ejemplo: "6.200 kg supera el umbral de 5.000 kg"
     */
    public String texto() {
        if (parametro == null && umbral == null && valorObservado == null) {
            return null;
        }

        String u = (unidad != null && !unidad.isBlank()) ? unidad.trim().toLowerCase() : "";

        // Formato para kg: «6.200 kg supera el umbral de 5.000 kg»
        if ("kg".equals(u)) {
            String valFormateado = formatearKilos(valorObservado);
            String umbralFormateado = formatearKilos(umbral);
            return String.format("%s kg supera el umbral de %s kg", valFormateado, umbralFormateado);
        }

        // Formato para horas: «126,4 h supera el umbral de 120,0 h»
        if ("h".equals(u) || "horas".equals(u)) {
            String valFormateado = formatearDecimal(valorObservado, 1);
            String umbralFormateado = formatearDecimal(umbral, 1);
            return String.format("%s h supera el umbral de %s h", valFormateado, umbralFormateado);
        }

        // Formato para porcentaje: «Diferencia 25,4% supera el umbral de 15,0%»
        if ("%".equals(u) || "pct".equals(u)) {
            String valFormateado = formatearDecimal(valorObservado, 1);
            String umbralFormateado = formatearDecimal(umbral, 1);
            return String.format("%s%% supera el umbral de %s%%", valFormateado, umbralFormateado);
        }

        // Formato para fechas: «Fecha observada 2026-04-02 es posterior al cierre del 2026-03-31»
        if ("fecha".equals(u)) {
            return String.format("Fecha observada %s es posterior al cierre del %s", valorObservado, umbral);
        }

        // Formato para sigma / z-score
        if ("sigma".equals(u)) {
            return String.format("z = %s supera el umbral de %s desviaciones estándar", valorObservado, umbral);
        }

        // Formato genérico
        if (!u.isEmpty()) {
            return String.format("%s %s supera el umbral de %s %s", valorObservado, u, umbral, u);
        }
        return String.format("Valor observado %s supera el umbral de %s", valorObservado, umbral);
    }

    public static CriterioHallazgo deKilos(String parametro, double umbral, double valor) {
        return new CriterioHallazgo(parametro, String.valueOf(umbral), String.valueOf(valor), "kg");
    }

    public static CriterioHallazgo deHoras(String parametro, double umbral, double valor) {
        return new CriterioHallazgo(parametro, String.valueOf(umbral), String.valueOf(valor), "h");
    }

    public static CriterioHallazgo dePorcentaje(String parametro, double umbral, double valor) {
        return new CriterioHallazgo(parametro, String.valueOf(umbral), String.valueOf(valor), "%");
    }

    public static CriterioHallazgo deFecha(String parametro, String umbralFecha, String valorFecha) {
        return new CriterioHallazgo(parametro, umbralFecha, valorFecha, "fecha");
    }

    private static String formatearKilos(String str) {
        if (str == null) return "";
        try {
            double d = Double.parseDouble(str.replace(",", "."));
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_CL);
            symbols.setGroupingSeparator('.');
            symbols.setDecimalSeparator(',');
            if (d == (long) d) {
                DecimalFormat df = new DecimalFormat("#,##0", symbols);
                return df.format((long) d);
            } else {
                DecimalFormat df = new DecimalFormat("#,##0.##", symbols);
                return df.format(d);
            }
        } catch (NumberFormatException e) {
            return str;
        }
    }

    private static String formatearDecimal(String str, int decimales) {
        if (str == null) return "";
        try {
            double d = Double.parseDouble(str.replace(",", "."));
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_CL);
            symbols.setGroupingSeparator('.');
            symbols.setDecimalSeparator(',');
            String pattern = "#,##0." + "0".repeat(decimales);
            DecimalFormat df = new DecimalFormat(pattern, symbols);
            return df.format(d);
        } catch (NumberFormatException e) {
            return str;
        }
    }
}
