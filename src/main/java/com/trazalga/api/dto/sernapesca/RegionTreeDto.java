package com.trazalga.api.dto.sernapesca;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Región (con sus comunas y caletas) del endpoint /commons/region-comuna-caleta. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegionTreeDto {

    private Integer codRegion;
    private String nombreRegion;
    private List<ComunaTreeDto> comunas;
}
