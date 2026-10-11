package com.trazalga.api.services.indicadores;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contexto de evaluación para criterios de desembarque atípico (TD.1 / TD.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextoEvaluacionAtipico {
    private String tipo;           // RECOLECTOR | ARMADOR | AREA
    private Long declaracionId;
    private Double kilos;          // Desembarque físico
    private String perfil;
    private Long especieId;
    private Long humedadId;
    private Long caletaId;
    private Long comunaId;
    private Long usuarioId;
    private Date fechaDeclaracion;
    private List<Double> historicoKilos; // Para evaluación estadística (TD.2)
}
