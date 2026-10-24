package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReporteRelacionesTransporteDTO {

    @Builder.Default
    private List<FilaRelacionDTO> filas = new ArrayList<>();

    @Builder.Default
    private List<AvisoTransporteDTO> avisos = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilaRelacionDTO {
        private Long transportistaId;
        private String transportistaNombre;
        private String transportistaRut;

        private Long vehiculoId;
        private String vehiculoPatente;
        private String tipoVehiculo;

        private Long comercializadorId;
        private String comercializadorNombre;
        private String comercializadorRut;

        private int viajes;
        private double kilos;
        private List<String> plantasDestino;
        private Date primeraFecha;
        private Date ultimaFecha;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvisoTransporteDTO {
        private String tipo; // MULTI_COMERCIALIZADOR, MULTI_PATENTE
        private String titulo;
        private String detalle;
        private String entidadReferencia;
        private int cantidadObservada;
    }
}
