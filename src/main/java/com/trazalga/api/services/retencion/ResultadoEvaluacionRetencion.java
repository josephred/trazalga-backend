package com.trazalga.api.services.retencion;

import lombok.Builder;
import lombok.Value;

/**
 * Resultado estructurado de la evaluación normativa y de bloqueo de una carga en bodega (TR.1, TR.3).
 */
@Value
@Builder
public class ResultadoEvaluacionRetencion {
    String estadoDeclarado;
    String claveMatriz;
    String tramoReal;
    int plazoMaxHoras;
    int preavisoSemaforoHoras;
    String semaforo; // "VERDE", "AMARILLO", "ROJO"
    boolean vencido;
    boolean enPreavisoSemaforo;
    boolean bloqueoAplicaEspecie;
    int umbralBloqueoHoras;
    boolean bloqueoRequerido;
    boolean enPreavisoBloqueo;
    boolean inconsistenciaHumedad;
    String motivoDetalle;
}
