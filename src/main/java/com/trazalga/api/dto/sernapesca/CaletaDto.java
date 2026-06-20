package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Caleta dentro de la jerarquía region-comuna-caleta de Sernapesca. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaletaDto {

    private Integer codCaleta;
    private String nombreCaleta;
    private Integer codComuna;
    private Integer codPuerto;
    private Integer codRegion;
}
