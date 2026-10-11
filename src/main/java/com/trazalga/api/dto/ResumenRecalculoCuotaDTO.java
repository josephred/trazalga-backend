package com.trazalga.api.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO con el resumen del recálculo de hallazgos de una cuota (TC.10).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenRecalculoCuotaDTO {
    private Long cuotaId;
    private String cuotaNombre;
    private int declaracionesEvaluadas;
    private int marcasNuevas;
    private int marcasExistentes;
    private BigDecimal consumoFinalKg;
    private BigDecimal limiteKg;
    private Double porcentajeConsumoFinal;
    private Date ejecutadoAt;
    private List<DetalleMarcaRecalculoDTO> marcasGeneradas;
}
