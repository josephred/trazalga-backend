package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detalle individual de marca generada o confirmada durante el recálculo (TC.10).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleMarcaRecalculoDTO {
    private String tipoDeclaracion;
    private Long declaracionId;
    private String marca;
    private String detalle;
    private boolean esNueva;
}
