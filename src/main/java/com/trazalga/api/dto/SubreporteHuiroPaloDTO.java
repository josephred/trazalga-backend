package com.trazalga.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tarjeta "Subreporte Huiro Palo" (refinamiento 23-10-2026, punto 3).
 * <p>
 * Estima los kilos de fronda que no quedan declarados cuando el huiro palo barreteado se declara
 * "sin fronda". Base: captura biológica corregida de esas declaraciones, con el método declarado
 * (aunque la cuota que descuenta sea otra por la imputación cruzada por veda).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubreporteHuiroPaloDTO {

    private String desde;
    private String hasta;
    /** Fecha con que se filtra: EXTRACCION o DECLARACION (parámetro cuota_fecha_imputacion). */
    private String criterioFecha;

    private boolean activo;
    /** Los nombres de especie, método y composición calzan con los maestros y el porcentaje es válido. */
    private boolean configuracionValida;
    private String mensaje;

    // Parámetros vigentes, tal como se aplicaron
    private String especie;
    private String metodo;
    private String composicion;
    private double factorPct;
    private String formula;
    /** Multiplicador aplicado a la captura sin fronda (0,22 o 0,2821 con 22 %). */
    private double multiplicador;

    // Totales del rango
    private long declaraciones;
    /** Captura biológica corregida declarada sin fronda (kg). */
    private double capturaSinFrondaKg;
    /** Kilos de fronda no declarados, estimados (kg). */
    private double subreporteKg;
    /** Captura sin fronda + subreporte (kg). */
    private double capturaEstimadaKg;

    /** Declaraciones de la especie y el método sin composición informada: no entran en el cálculo. */
    private long declaracionesSinComposicion;
    private double capturaSinComposicionKg;
    /** Declaraciones de la especie y el método con otra composición (por ejemplo, con fronda): no aplica. */
    private long declaracionesOtraComposicion;
    private double capturaOtraComposicionKg;

    private List<Grupo> porComuna;
    private List<Grupo> porMes;
    private List<Grupo> porPerfil;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Grupo {
        /** Comuna: id; mes: AAAA-MM; perfil: RECOLECTOR, ARMADOR o AREA. */
        private String clave;
        private String etiqueta;
        private long declaraciones;
        private double capturaSinFrondaKg;
        private double subreporteKg;
    }
}
