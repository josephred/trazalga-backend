package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Método de recolección (/commons/metodo-recoleccion/buscar) -> extraccion_tipo. */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetodoRecoleccionDto {

    private Integer id;
    private String nombre;
    private Boolean esExcepcion;
}
