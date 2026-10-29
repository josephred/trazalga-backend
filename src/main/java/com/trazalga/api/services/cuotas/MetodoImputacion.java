package com.trazalga.api.services.cuotas;

/**
 * Método de extracción con que una declaración de origen consume cuota (refinamiento 23-10, punto 1).
 *
 * <p>Lo declarado no se reescribe: {@code extraccion_tipo_id} guarda el método declarado y
 * {@code extraccion_tipo_imputacion_id} guarda, sólo cuando hay imputación cruzada por veda, el
 * método cuya cuota recibe la captura (p. ej. varado para un barreteado en veda). Si es nulo, la
 * declaración consume con su método declarado.</p>
 *
 * <p>Toda consulta de consumo de cuotas filtra por método con {@link #sqlMetodoEfectivo(String)},
 * para que exista una sola definición.</p>
 */
public final class MetodoImputacion {

    public static final String COLUMNA_IMPUTACION = "extraccion_tipo_imputacion_id";

    private MetodoImputacion() {}

    /**
     * Expresión SQL del método con que consume cuota la declaración del alias dado.
     *
     * @param alias alias SQL de la tabla de declaraciones (p. ej. "d").
     * @return {@code COALESCE(d.extraccion_tipo_imputacion_id, d.extraccion_tipo_id)}
     */
    public static String sqlMetodoEfectivo(String alias) {
        return "COALESCE(" + alias + "." + COLUMNA_IMPUTACION + ", " + alias + ".extraccion_tipo_id)";
    }

    /**
     * Versión en memoria de {@link #sqlMetodoEfectivo(String)}.
     */
    public static Long metodoEfectivo(Long extraccionTipoImputacionId, Long extraccionTipoId) {
        return extraccionTipoImputacionId != null ? extraccionTipoImputacionId : extraccionTipoId;
    }
}
