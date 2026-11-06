package com.trazalga.api.services.cuotas;

import com.trazalga.api.dto.ContextoDeclaracion;

/**
 * Estrategia de Imputación Territorial (TC.8 / K14 / K15).
 * Define la regla para determinar a qué comuna territorial se atribuye
 * una extracción para la validación y el cálculo de consumo de cuotas.
 * <p>
 * Desde el 06-11-2026 la única regla es {@link ReglaImputacionInscripcion}: la cuota se descuenta
 * en la comuna del usuario que declara ({@code usuario.comuna_id}), para recolectores, armadores y
 * áreas de manejo.
 */
public interface ReglaImputacionTerritorial {

    /**
     * Clave identificadora de la estrategia.
     */
    String clave();

    /**
     * Determina el ID de la comuna de imputación para la validación en memoria.
     *
     * @param ctx Contexto de la declaración a validar.
     * @return ID de la comuna a imputar territorialmente, o null si no aplica.
     */
    Long comunaImputacion(ContextoDeclaracion ctx);

    /**
     * Genera la expresión SQL que resuelve la comuna de imputación para los JOINs y filtros
     * territoriales de una tabla de declaraciones de origen.
     *
     * @param tabla            {@code declaracion_recolector}, {@code declaracion_armador} o {@code declaracion_area}.
     * @param aliasDeclaracion Alias SQL de la tabla de declaraciones (ej. "d").
     * @param aliasUsuario     Alias SQL de la tabla de usuarios (ej. "u"), unida por {@code usuario_id}.
     * @return Fragmento SQL, por ejemplo {@code COALESCE(u.comuna_id, d.comuna_id)}.
     */
    String sqlComuna(String tabla, String aliasDeclaracion, String aliasUsuario);
}
