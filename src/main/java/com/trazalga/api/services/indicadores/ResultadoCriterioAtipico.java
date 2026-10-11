package com.trazalga.api.services.indicadores;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de la evaluación de un criterio de desembarque atípico (TD.1 / TD.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoCriterioAtipico {
    private boolean atipico;
    private String criterioClave; // UMBRAL_ABSOLUTO | DESVIACION_ESTADISTICA
    private String parametro;     // "desembarque_umbral_atipico_kg" | "desembarque_atipico_sigma"
    private String umbral;
    private String valor;
    private String unidad;
    private String texto;
}
