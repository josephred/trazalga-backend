package com.trazalga.api.services.cuotas;

import com.trazalga.api.dto.ContextoDeclaracion;

/**
 * Estrategia de Imputación Territorial (TC.8 / K14 / K15 / D1).
 * Define la regla para determinar a qué comuna territorial se atribuye
 * una extracción para la validación y el cálculo de consumo de cuotas.
 */
public interface ReglaImputacionTerritorial {

    /**
     * Clave identificadora de la estrategia (INSCRIPCION o CALETA_DESEMBARQUE).
     */
    String clave();

    /**
     * Determina el ID de la comuna de imputación para la validación en memoria.
     * Cierra K15 al respaldar a comuna de desembarque si falta comuna de inscripción.
     *
     * @param ctx Contexto de la declaración a validar.
     * @return ID de la comuna a imputar territorialmente, o null si no aplica.
     */
    Long comunaImputacion(ContextoDeclaracion ctx);

    /**
     * Genera la expresión SQL que resuelve la comuna para los JOINs y filtros territoriales.
     *
     * @param aliasDeclaracion Alias SQL de la tabla de declaraciones (ej. "d").
     * @param aliasUsuario     Alias SQL de la tabla de usuarios (ej. "u").
     * @return Fragmento SQL (ej. "COALESCE(u.comuna_id, d.comuna_id)" o "d.comuna_id").
     */
    String sqlComuna(String aliasDeclaracion, String aliasUsuario);
}
