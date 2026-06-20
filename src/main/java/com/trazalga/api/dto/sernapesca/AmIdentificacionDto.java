package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Área de manejo (AMERB) de Sernapesca (/area-manejo/por-region) -> amerb. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AmIdentificacionDto {

    @JsonProperty("cd_Area")
    private Integer cdArea;

    private ComboIntDto caleta;
    private ComboIntDto region;

    private Integer folioOrganizacion;
    private String nombreOrganizacion;

    @JsonProperty("fc_Decreto")
    private String fcDecreto;

    @JsonProperty("nm_Sector")
    private String nmSector;

    @JsonProperty("nr_Decreto")
    private Integer nrDecreto;
}
