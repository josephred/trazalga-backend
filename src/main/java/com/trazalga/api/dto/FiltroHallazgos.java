package com.trazalga.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filtro de consulta para la búsqueda paginada de hallazgos (TA.3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltroHallazgos {
    private String marca;
    private String estadoGestion; // PENDIENTE | DERIVADA_CITACION | RESUELTA
    private Boolean resuelta;
    private String tipo; // declaracionTipo: RECOLECTOR | ARMADOR | AREA | COMERCIALIZADOR | PLANTA_ABASTECIMIENTO
    private Long reglaId;
    private Date desde;
    private Date hasta;
    private String rpa;
}
