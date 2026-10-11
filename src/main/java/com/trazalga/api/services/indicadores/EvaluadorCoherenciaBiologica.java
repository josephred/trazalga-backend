package com.trazalga.api.services.indicadores;

import com.trazalga.api.services.parametros.ParametrosVariacion;
import org.springframework.stereotype.Component;

/**
 * TV.2 · Evaluador de Coherencia Biológica para Variación de Peso Comercializador → Planta.
 * <p>
 * Implementa la matriz normativa acordada el 09-10-2026 como una función pura
 * con estrategias por estado de hidratación (Húmedo/Semihúmedo, Seco/Semiseco y Equivalente).
 */
@Component
public class EvaluadorCoherenciaBiologica {

    public enum SemaforoVariacion {
        VERDE,
        AMARILLO,
        ROJO;

        public String toSeveridadIndicador8() {
            return switch (this) {
                case VERDE -> "NEUTRA";
                case AMARILLO -> "ATENCION";
                case ROJO -> "CRITICA";
            };
        }

        public static SemaforoVariacion fromSeveridadIndicador8(String severidad) {
            if (severidad == null) return VERDE;
            return switch (severidad.toUpperCase()) {
                case "CRITICA", "CRÍTICA", "ROJA", "ROJO" -> ROJO;
                case "ATENCION", "ATENCIÓN", "AMARILLA", "AMARILLO" -> AMARILLO;
                default -> VERDE;
            };
        }
    }

    public record ResultadoEvaluacion(
            SemaforoVariacion semaforo,
            String criterio,
            String descripcion,
            boolean alerta
    ) {}

    /**
     * Evalúa la coherencia biológica de una variación física (o equivalente) de peso.
     *
     * @param humedadDespacho   Estado de humedad predominante del despacho (e.g. HÚMEDO, SECO)
     * @param humedadRecepcion  Estado de humedad registrado en la recepción en planta
     * @param deltaPct          Variación física porcentual: ((kgRomana - kgDespacho) / kgDespacho) * 100
     * @param deltaEqPct        Variación equivalente porcentual (opcional si hay cambio de humedad)
     * @param diasTranscurridos Días transcurridos entre salida y recepción
     * @param params            Parámetros normativos de variación y merma
     * @return Resultado estructurado con semáforo, criterio y descripción
     */
    public ResultadoEvaluacion evaluar(
            String humedadDespacho,
            String humedadRecepcion,
            double deltaPct,
            Double deltaEqPct,
            double diasTranscurridos,
            ParametrosVariacion params
    ) {
        if (params == null) {
            params = new ParametrosVariacion(true, 3, 5.0, 3.0, false, 5.0, 10.0);
        }

        double tol = params.variacionPesoUmbralGeneralPct();
        double mermaPct = -deltaPct;

        // 1. Estrategia Equivalente (D8): cuando cambia la humedad y la alerta equivalente está activa
        if (params.variacionPesoAlertaEquivalente()
                && deltaEqPct != null
                && esCambioDeHumedad(humedadDespacho, humedadRecepcion)) {
            return evaluarEstrategiaEquivalente(deltaEqPct, tol, params.riesgoVariacionRojoPct());
        }

        // Si el control biológico está desactivado por parámetro, evaluar únicamente tolerancia general
        if (!params.bioPerdidaActivo()) {
            return evaluarToleranciaGeneral(deltaPct, tol, params.riesgoVariacionRojoPct());
        }

        // 2. Condición Universal: Ganancia de peso sobre tolerancia no explicable biológicamente
        if (deltaPct > tol) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.ROJO,
                    "GANANCIA_NO_EXPLICADA",
                    String.format("Ganancia de peso no explicada (%.1f%% > tolerancia %.1f%%)", deltaPct, tol),
                    true
            );
        }

        // 3. Estrategias por Estado de Humedad del Despacho
        if (esHumedoOSemihumedo(humedadDespacho)) {
            return evaluarEstrategiaHumedo(deltaPct, mermaPct, diasTranscurridos, tol, params);
        } else if (esSecoOSemiseco(humedadDespacho)) {
            return evaluarEstrategiaSeco(deltaPct, mermaPct, diasTranscurridos, params);
        } else {
            // Estado desconocido o neutro: evaluar tolerancia general
            return evaluarToleranciaGeneral(deltaPct, tol, params.riesgoVariacionRojoPct());
        }
    }

    private ResultadoEvaluacion evaluarEstrategiaEquivalente(double deltaEqPct, double tol, double rojoPct) {
        double absDeltaEq = Math.abs(deltaEqPct);
        if (absDeltaEq <= tol) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.VERDE,
                    "CAMBIO_HUMEDAD_COHERENTE",
                    String.format("Variación equivalente coherente (%.1f%% dentro de tolerancia ±%.1f%%)", deltaEqPct, tol),
                    false
            );
        } else if (absDeltaEq <= rojoPct) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.AMARILLO,
                    "CAMBIO_HUMEDAD_ATENCION",
                    String.format("Variación equivalente moderada fuera de tolerancia (%.1f%%)", deltaEqPct),
                    true
            );
        } else {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.ROJO,
                    "CAMBIO_HUMEDAD_CRITICA",
                    String.format("Variación equivalente crítica sobre umbral de riesgo (%.1f%%)", deltaEqPct),
                    true
            );
        }
    }

    private ResultadoEvaluacion evaluarEstrategiaHumedo(
            double deltaPct,
            double mermaPct,
            double dias,
            double tol,
            ParametrosVariacion params
    ) {
        int diasMin = params.bioHumedoDiasMinimosTransito();
        double mermaMin = params.bioHumedoMermaMinimaPct();

        if (dias < diasMin) {
            if (deltaPct >= -tol && deltaPct <= tol) {
                return new ResultadoEvaluacion(
                        SemaforoVariacion.VERDE,
                        "MASA_ESTABLE",
                        String.format("Variación física en tránsito corto (%.1f d) dentro de tolerancia (%.1f%%)", dias, deltaPct),
                        false
                );
            } else { // mermaPct > tol (es decir, deltaPct < -tol)
                return new ResultadoEvaluacion(
                        SemaforoVariacion.AMARILLO,
                        "MERMA_ALTA_TRANSITO_CORTO",
                        String.format("Merma alta en tránsito corto de %.1f días (merma %.1f%% > tol %.1f%%)", dias, mermaPct, tol),
                        true
                );
            }
        } else { // dias >= diasMin
            if (mermaPct >= mermaMin) {
                return new ResultadoEvaluacion(
                        SemaforoVariacion.VERDE,
                        "DESHIDRATACION_COHERENTE",
                        String.format("Deshidratación biológicamente coherente tras %.1f días (merma %.1f%% ≥ mínima %.1f%%)", dias, mermaPct, mermaMin),
                        false
                );
            } else if (mermaPct > 0 && mermaPct < mermaMin) {
                return new ResultadoEvaluacion(
                        SemaforoVariacion.AMARILLO,
                        "HUMEDO_MERMA_INSUFICIENTE",
                        String.format("Merma insuficiente tras %.1f días de tránsito (merma %.1f%% < mínima esperada %.1f%%)", dias, mermaPct, mermaMin),
                        true
                );
            } else { // mermaPct <= 0 (variación nula o ganancia dentro de tolerancia)
                return new ResultadoEvaluacion(
                        SemaforoVariacion.ROJO,
                        "HUMEDO_SIN_MERMA",
                        String.format("Inconsistencia biológica crítica tras %.1f días: alga húmeda sin merma (variación %.1f%%)", dias, deltaPct),
                        true
                );
            }
        }
    }

    private ResultadoEvaluacion evaluarEstrategiaSeco(
            double deltaPct,
            double mermaPct,
            double dias,
            ParametrosVariacion params
    ) {
        double mermaMax = params.bioSecoMermaMaximaPct();
        double rojoPct = params.riesgoVariacionRojoPct();

        if (mermaPct <= mermaMax) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.VERDE,
                    "MASA_ESTABLE",
                    String.format("Masa estable de alga seca tras %.1f días (merma %.1f%% ≤ máx %.1f%%)", dias, mermaPct, mermaMax),
                    false
            );
        } else if (mermaPct <= rojoPct) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.AMARILLO,
                    "SECO_MERMA_EXCESIVA",
                    String.format("Merma seca excesiva tras %.1f días (merma %.1f%% sobre máx %.1f%%)", dias, mermaPct, mermaMax),
                    true
            );
        } else {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.ROJO,
                    "SECO_MERMA_EXCESIVA",
                    String.format("Merma seca crítica tras %.1f días (merma %.1f%% sobre umbral rojo %.1f%%)", dias, mermaPct, rojoPct),
                    true
            );
        }
    }

    private ResultadoEvaluacion evaluarToleranciaGeneral(double deltaPct, double tol, double rojoPct) {
        double absDelta = Math.abs(deltaPct);
        if (absDelta <= tol) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.VERDE,
                    "MASA_ESTABLE",
                    String.format("Variación de peso dentro de tolerancia general (%.1f%% ≤ ±%.1f%%)", deltaPct, tol),
                    false
            );
        } else if (absDelta <= rojoPct) {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.AMARILLO,
                    deltaPct > 0 ? "GANANCIA_NO_EXPLICADA" : "MERMA_MODERADA",
                    String.format("Variación de peso moderada fuera de tolerancia (%.1f%%)", deltaPct),
                    true
            );
        } else {
            return new ResultadoEvaluacion(
                    SemaforoVariacion.ROJO,
                    deltaPct > 0 ? "GANANCIA_NO_EXPLICADA" : "MERMA_CRITICA",
                    String.format("Variación de peso crítica (%.1f%%)", deltaPct),
                    true
            );
        }
    }

    public static boolean esHumedoOSemihumedo(String estado) {
        if (estado == null) return false;
        String u = estado.toUpperCase();
        return u.contains("HUMED") || u.contains("HÚMED");
    }

    public static boolean esSecoOSemiseco(String estado) {
        if (estado == null) return false;
        String u = estado.toUpperCase();
        return u.contains("SEC");
    }

    public static boolean esCambioDeHumedad(String estadoDespacho, String estadoRecepcion) {
        if (estadoDespacho == null || estadoRecepcion == null) return false;
        String d = normalizarHumedad(estadoDespacho);
        String r = normalizarHumedad(estadoRecepcion);
        return !d.equalsIgnoreCase(r);
    }

    public static String normalizarHumedad(String estado) {
        if (estado == null) return "DESCONOCIDO";
        String u = estado.toUpperCase().trim();
        if (u.contains("SEMI") && (u.contains("HUMED") || u.contains("HÚMED"))) return "SEMIHÚMEDO";
        if (u.contains("HUMED") || u.contains("HÚMED")) return "HÚMEDO";
        if (u.contains("SEMI") && u.contains("SEC")) return "SEMISECO";
        if (u.contains("SEC")) return "SECO";
        return u;
    }
}
