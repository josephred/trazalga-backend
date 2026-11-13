package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoAdminDTO {
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Long humedadEstadoId;
    private String humedadEstadoNombre;
    private Long especieId;
    private String especieNombre;
    private Boolean activo;
    private String fuente;
    private LocalDateTime actualizadoEn;
    private String actualizadoPor;
    private long usosProduccion;
    private long usosDestino;
    private long usosTotales;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductoAdminRequest {
        private String codigo;
        private String nombre;
        private String descripcion;
        private Long humedadEstadoId;
        private Long especieId;
        private Boolean activo;
        private String fuente;
    }
}

