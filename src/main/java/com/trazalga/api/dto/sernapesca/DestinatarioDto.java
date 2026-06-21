package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DestinatarioDto {

    private String rut;
    private String nombre;
    private Boolean planta;
    private Boolean comercializadora;
    private Boolean otro;
    private Boolean embarcacionTransportadora;
    private Integer cdDestinatario;
    private TipoDestino tipoDestino;
    private String direccion;
    private Boolean activo;
    private String nombreCiudad;
    private String nombreRegion;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TipoDestino {
        private Integer codigo;
        private String valor;
    }
}
