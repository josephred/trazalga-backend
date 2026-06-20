package com.trazalga.api.dto.sernapesca;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Comuna (con sus caletas) dentro de la jerarquía region-comuna-caleta. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComunaTreeDto {

    private Integer codComuna;
    private String nombreComuna;
    private List<CaletaDto> caletas;
}
