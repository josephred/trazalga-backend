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
    private BigDecimal limiteCuota;
    private Double porcentajeUso;
    // Alcance de la cuota: "Global", nombre del actor o nombre del área de manejo
    private String alcance;
}
