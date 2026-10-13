package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TV.1 · DTO para un despacho que fue consumido por una recepción en planta.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DespachoVariacionDTO {
    private Long declaracionId;
    private String tipoDeclaracion; // COMERCIALIZADOR | RECOLECTOR | ARMADOR | AREA
    private String folio;
    private String guiaDespacho;
    private String documentoTributarioTipo;
    private String documentoTributarioNumero;
    private String patente;
    private String declaradorNombre;
    private String declaradorRut;
    private double kg;
    private Long humedadId;
    private String humedadNombre;
    private Date fechaSalida;
    private Double horasTransito;
    private Integer origenesCount;
}
