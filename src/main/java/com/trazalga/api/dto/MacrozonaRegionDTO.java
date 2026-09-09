package com.trazalga.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MacrozonaRegionDTO {

    private Long id;
    private Long macrozonaId;
    private Long regionId;
    private String regionNombre;
    private String regionCodigo;
    private Date vigenciaInicio;
    private Date vigenciaFin;

}
