package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisFrecuentesDTO {

    @Builder.Default
    private List<ChoferFrecuenteDTO> choferes = new ArrayList<>();

    @Builder.Default
    private List<VehiculoFrecuenteDTO> vehiculos = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoferFrecuenteDTO {
        private Long transportistaId;
        private String nombre;
        private String rut;
        private int veces;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehiculoFrecuenteDTO {
        private Long vehiculoId;
        private String patente;
        private String tipoVehiculo;
        private boolean esRampla;
        private int veces;
    }
}
