package com.trazalga.api.services.indicadores;

/**
 * Estrategia de evaluación para desembarques atípicos (TD.1 / TD.2).
 */
public interface CriterioAtipico {
    String clave();
    ResultadoCriterioAtipico evaluar(ContextoEvaluacionAtipico ctx);
}
