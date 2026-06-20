package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Embarcación de Sernapesca (/embarcacion/por-region). */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbarcacionDto {

    private Integer folioRpa;
    private String nombreNave;
    private String nrMatricula;
    private Integer codigoTipoNave;
    private Boolean certificable;
    private String codigoCapitania;
}
