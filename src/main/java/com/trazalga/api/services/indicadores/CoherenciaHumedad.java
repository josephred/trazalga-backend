package com.trazalga.api.services.indicadores;

import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * TH.1 · Evaluación canónica y pura de la coherencia entre la fecha de extracción
 * y el estado de humedad declarado según la Resolución Exenta N° 3602/2017 (art. 1).
 *
 * <p>Orden de sequedad: {@code HÚMEDO (0) < SEMI HÚMEDO (1) < SEMI SECO (2) < SECO (3)}.</p>
 * <ul>
 *   <li>Edad mínima: {@code h_min = M - (F 23:59:59)}, con piso en 0.</li>
 *   <li>Edad máxima: {@code h_max = M - (F 00:00:00)}.</li>
 * </ul>
 */
public final class CoherenciaHumedad {

    public static final String ESTADO_HUMEDO = "HÚMEDO";
    public static final String ESTADO_SEMI_HUMEDO = "SEMI HÚMEDO";
    public static final String ESTADO_SEMI_SECO = "SEMI SECO";
    public static final String ESTADO_SECO = "SECO";

    public static final String MODO_DIRECCIONAL = "DIRECCIONAL";
    public static final String MODO_ESTRICTO = "ESTRICTO";

    public static final String RESULTADO_COHERENTE = "COHERENTE";
    public static final String RESULTADO_INCOHERENTE = "INCOHERENTE";
    public static final String RESULTADO_SIN_DATO = "SIN_DATO";

    public static final String PARAM_EDAD_MAX_HUMEDO = "humedad_edad_max_humedo_horas";
    public static final String PARAM_EDAD_MAX_SEMIHUMEDO = "humedad_edad_max_semihumedo_horas";
    public static final String PARAM_EDAD_MAX_SEMISECO = "humedad_edad_max_semiseco_horas";

    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private CoherenciaHumedad() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParametrosCoherencia {
        @Builder.Default
        private int edadMaxHumedoHoras = 24;
        @Builder.Default
        private int edadMaxSemihumedoHoras = 72;
        @Builder.Default
        private int edadMaxSemisecoHoras = 216;
        @Builder.Default
        private String modo = MODO_DIRECCIONAL;
        @Builder.Default
        private boolean activo = false;

        public static ParametrosCoherencia porDefecto() {
            return ParametrosCoherencia.builder().build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultadoCoherencia {
        private String resultado;        // "COHERENTE", "INCOHERENTE", "SIN_DATO"
        private boolean incoherente;
        private boolean masSecoQueSuEdad;
        private String estadoDeclarado;
        private String tramoMinimo;      // tramo(h_min)
        private String tramoMaximo;      // tramo(h_max)
        private Double hMin;
        private Double hMax;
        private CriterioHallazgo criterio;
        private String explicacion;
        private String aviso;
    }

    /**
     * Normaliza el estado de humedad SIN fallback a HÚMEDO cuando es nulo o irreconocible (TH.1).
     * «Seco Planta» cuenta como SECO.
     */
    public static String normalizarEstadoDeclarado(String hum) {
        if (hum == null || hum.isBlank()) {
            return null;
        }
        String h = hum.toUpperCase(LOCALE_CL).trim();
        if (h.contains("SEMI") && (h.contains("HUM") || h.contains("HÚM"))) {
            return ESTADO_SEMI_HUMEDO;
        }
        if (h.contains("SEMI") && h.contains("SEC")) {
            return ESTADO_SEMI_SECO;
        }
        if (h.contains("HUM") || h.contains("HÚM")) {
            return ESTADO_HUMEDO;
        }
        if (h.contains("SEC")) {
            return ESTADO_SECO;
        }
        return null;
    }

    /**
     * Ordena los estados por sequedad: HÚMEDO (0) < SEMI HÚMEDO (1) < SEMI SECO (2) < SECO (3).
     */
    public static int ordenSequedad(String estadoNormalizado) {
        if (estadoNormalizado == null) return -1;
        return switch (estadoNormalizado) {
            case ESTADO_HUMEDO -> 0;
            case ESTADO_SEMI_HUMEDO -> 1;
            case ESTADO_SEMI_SECO -> 2;
            case ESTADO_SECO -> 3;
            default -> -1;
        };
    }

    /**
     * Determina el tramo de humedad correspondiente a una edad en horas según Res. 3602, art. 1:
     * menos de 24 h -> HÚMEDO; hasta 72 h -> SEMI HÚMEDO; hasta 216 h -> SEMI SECO; más de 216 h -> SECO.
     */
    public static String tramoPorEdad(double edadHoras, ParametrosCoherencia params) {
        ParametrosCoherencia p = params != null ? params : ParametrosCoherencia.porDefecto();
        if (edadHoras < p.getEdadMaxHumedoHoras()) {
            return ESTADO_HUMEDO;
        }
        if (edadHoras <= p.getEdadMaxSemihumedoHoras()) {
            return ESTADO_SEMI_HUMEDO;
        }
        if (edadHoras <= p.getEdadMaxSemisecoHoras()) {
            return ESTADO_SEMI_SECO;
        }
        return ESTADO_SECO;
    }

    public static LocalDate toLocalDate(Date fecha) {
        if (fecha == null) return null;
        if (fecha instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static ResultadoCoherencia evaluar(
            String estadoRaw,
            Date fechaExtraccionDate,
            LocalDateTime momentoEvento,
            ParametrosCoherencia params) {
        return evaluar(estadoRaw, toLocalDate(fechaExtraccionDate), momentoEvento, params);
    }

    /**
     * Evalúa la coherencia entre la fecha de extracción {@code F}, el momento del evento {@code M}
     * y el estado de humedad declarado {@code E}.
     */
    public static ResultadoCoherencia evaluar(
            String estadoRaw,
            LocalDate fechaExtraccion,
            LocalDateTime momentoEvento,
            ParametrosCoherencia params) {

        ParametrosCoherencia p = params != null ? params : ParametrosCoherencia.porDefecto();
        String estadoDeclarado = normalizarEstadoDeclarado(estadoRaw);

        // Caso SIN_DATO: E o F o M son nulos, o F es posterior a la fecha de M
        if (estadoDeclarado == null || fechaExtraccion == null || momentoEvento == null
                || fechaExtraccion.isAfter(momentoEvento.toLocalDate())) {
            return ResultadoCoherencia.builder()
                    .resultado(RESULTADO_SIN_DATO)
                    .incoherente(false)
                    .masSecoQueSuEdad(false)
                    .estadoDeclarado(estadoDeclarado)
                    .build();
        }

        LocalDateTime finDiaExtraccion = fechaExtraccion.atTime(23, 59, 59);
        LocalDateTime inicioDiaExtraccion = fechaExtraccion.atStartOfDay();

        double rawMinHoras = Duration.between(finDiaExtraccion, momentoEvento).toSeconds() / 3600.0;
        double hMin = Math.max(0.0, Math.round(rawMinHoras * 10.0) / 10.0);

        double rawMaxHoras = Duration.between(inicioDiaExtraccion, momentoEvento).toSeconds() / 3600.0;
        double hMax = Math.max(0.0, Math.round(rawMaxHoras * 10.0) / 10.0);

        String tramoMin = tramoPorEdad(hMin, p);
        String tramoMax = tramoPorEdad(hMax, p);

        int seqDeclarado = ordenSequedad(estadoDeclarado);
        int seqMin = ordenSequedad(tramoMin);
        int seqMax = ordenSequedad(tramoMax);

        boolean modoEstricto = MODO_ESTRICTO.equalsIgnoreCase(p.getModo());

        boolean masHumedoQueMinimo = seqDeclarado < seqMin;
        boolean masSecoQueMaximo = seqDeclarado > seqMax;

        boolean incoherente;
        String aviso = null;
        if (modoEstricto) {
            incoherente = masHumedoQueMinimo || masSecoQueMaximo;
            if (masSecoQueMaximo) {
                aviso = "más seco que su edad";
            }
        } else {
            // DIRECCIONAL (por defecto): sólo incoherente si E es más húmedo que tramo(h_min)
            incoherente = masHumedoQueMinimo;
            if (masSecoQueMaximo) {
                aviso = "más seco que su edad";
            }
        }

        String resultado = incoherente ? RESULTADO_INCOHERENTE : RESULTADO_COHERENTE;
        CriterioHallazgo criterio = null;
        String explicacion = null;

        if (incoherente) {
            String paramNombre;
            int umbralHoras;
            double valorObservado;
            String descRegla;

            if (masHumedoQueMinimo) {
                valorObservado = hMin;
                switch (estadoDeclarado) {
                    case ESTADO_HUMEDO -> {
                        paramNombre = PARAM_EDAD_MAX_HUMEDO;
                        umbralHoras = p.getEdadMaxHumedoHoras();
                        descRegla = "el estado húmedo corresponde a menos de " + umbralHoras + " h";
                    }
                    case ESTADO_SEMI_HUMEDO -> {
                        paramNombre = PARAM_EDAD_MAX_SEMIHUMEDO;
                        umbralHoras = p.getEdadMaxSemihumedoHoras();
                        descRegla = "el estado semi húmedo corresponde a hasta " + umbralHoras + " h";
                    }
                    default -> {
                        paramNombre = PARAM_EDAD_MAX_SEMISECO;
                        umbralHoras = p.getEdadMaxSemisecoHoras();
                        descRegla = "el estado semi seco corresponde a hasta " + umbralHoras + " h";
                    }
                }
                criterio = CriterioHallazgo.deHoras(paramNombre, umbralHoras, valorObservado);
                explicacion = String.format(
                        "Declarado %s con al menos %s h desde la extracción (%s). Según la Res. 3602, art. 1, %s. Estado más húmedo admisible: %s.",
                        estadoDeclarado,
                        formatearDecimalEsCl(hMin),
                        fechaExtraccion.format(FECHA_FMT),
                        descRegla,
                        tramoMin
                );
            } else {
                // Caso ESTRICTO cuando el estado declarado es más seco que tramo(h_max)
                valorObservado = hMax;
                switch (tramoMax) {
                    case ESTADO_HUMEDO -> {
                        paramNombre = PARAM_EDAD_MAX_HUMEDO;
                        umbralHoras = p.getEdadMaxHumedoHoras();
                    }
                    case ESTADO_SEMI_HUMEDO -> {
                        paramNombre = PARAM_EDAD_MAX_SEMIHUMEDO;
                        umbralHoras = p.getEdadMaxSemihumedoHoras();
                    }
                    default -> {
                        paramNombre = PARAM_EDAD_MAX_SEMISECO;
                        umbralHoras = p.getEdadMaxSemisecoHoras();
                    }
                }
                criterio = CriterioHallazgo.deHoras(paramNombre, umbralHoras, valorObservado);
                explicacion = String.format(
                        "Declarado %s con a lo sumo %s h desde la extracción (%s). En modo estricto, el tramo esperado por edad es %s.",
                        estadoDeclarado,
                        formatearDecimalEsCl(hMax),
                        fechaExtraccion.format(FECHA_FMT),
                        tramoMax
                );
            }
        } else if (masSecoQueMaximo) {
            explicacion = String.format(
                    "Declarado %s con a lo sumo %s h desde la extracción (%s): más seco que su edad (%s).",
                    estadoDeclarado,
                    formatearDecimalEsCl(hMax),
                    fechaExtraccion.format(FECHA_FMT),
                    tramoMax
            );
        }

        return ResultadoCoherencia.builder()
                .resultado(resultado)
                .incoherente(incoherente)
                .masSecoQueSuEdad(masSecoQueMaximo)
                .estadoDeclarado(estadoDeclarado)
                .tramoMinimo(tramoMin)
                .tramoMaximo(tramoMax)
                .hMin(hMin)
                .hMax(hMax)
                .criterio(criterio)
                .explicacion(explicacion)
                .aviso(aviso)
                .build();
    }

    public static String formatearDecimalEsCl(double valor) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_CL);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        if (Math.abs(valor - Math.rint(valor)) < 1e-9) {
            return String.valueOf(Math.round(valor));
        }
        DecimalFormat df = new DecimalFormat("#0.0", symbols);
        return df.format(valor);
    }
}
