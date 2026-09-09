package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlCuotaDiariaDTO {
    private String especieNombre;
    private BigDecimal volumenExtraido;
    private BigDecimal limiteCuota; // Límite efectivo en la métrica evaluada (para compatibilidad)
    private BigDecimal limiteNominal; // Límite original declarado en la cuota (ej. 5.000 kg secos)
    private BigDecimal limiteEfectivo; // Límite tras aplicar factor de conversión (ej. 17.900 kg captura)
    private String humedadEstadoNombre; // Estado de humedad nominal (ej. "Seco")
    private String metrica; // CAPTURA o DESEMBARQUE
    private BigDecimal factorConversion; // Factor de conversión aplicado (ej. 3.5800)
    private String descripcionEquivalencia; // Ej: "5.000 kg secos ≡ 17.900 kg captura (factor 3,58)"
    private Double porcentajeUso;
    // Alcance de la cuota: "Global", nombre del actor o nombre del área de manejo
    private String alcance;
}
