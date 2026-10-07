package com.trazalga.api.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaMarcaDTO {
    private Long id;
    private String declaracionTipo;
    private Long declaracionId;
    private String marca;
    private String detalle;
    private Date fechaDeteccion;
    private Boolean resuelta;
    private String estadoGestion; // PENDIENTE, DERIVADA_CITACION, RESUELTA
    private String resolucionTipo;
    private String observacionResolucion;
}
