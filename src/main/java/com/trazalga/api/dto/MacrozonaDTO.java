package com.trazalga.api.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
public class MacrozonaDTO {

    private Long id;
    private String nombre;
    private String codigo;
    private String descripcion;
    private Boolean esNacional;
    private Boolean activo;
    private Date createdAt;
    private Date updatedAt;

    @Builder.Default
    private List<MacrozonaRegionDTO> regiones = new ArrayList<>();

    @Builder.Default
    private List<Long> regionIds = new ArrayList<>();

}
