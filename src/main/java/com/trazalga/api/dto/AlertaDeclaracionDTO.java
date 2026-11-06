package com.trazalga.api.dto;

/**
 * Alerta de fiscalización de una declaración de origen, tal como la recibe la app al guardar
 * o al listar sus declaraciones (refinamiento 30-10, punto 3).
 *
 * @param marca   código de la marca (EN_VEDA, CUOTA_EXCEDIDA, …).
 * @param detalle texto de la marca, en es-CL.
 */
public record AlertaDeclaracionDTO(String marca, String detalle) {
}
