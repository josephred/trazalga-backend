package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pescador / Recolector de Orilla / Buzo (/pescadores/recolector/por-region) -> buzo. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PescadorDto {

    private Integer folioRpa;
    private Long rut;
    private String dv;
    private String rutCompleto;
    private String nombreCompleto;
    private ComboIntDto region;
    private java.util.List<ComboIntDto> categorias;
}
