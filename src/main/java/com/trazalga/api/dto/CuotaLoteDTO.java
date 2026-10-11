package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuotaLoteDTO {
    private Integer anio;
    private String ambito; // default "AREA_LIBRE"
    private String nivelAgregacion; // default "COMUNA"
    private Long regionId;
    private Long provinciaId;
    private Set<Long> comunaIds;
    private Long usuarioId;
    private Boolean esPlantilla;
    private Long especieId;
    private Long extraccionTipoId;
    private Long humedadEstadoId;
    private String metrica; // "DESEMBARQUE" o "CAPTURA"
    private String modoAccion; // "SOLO_ALERTA", "BLOQUEO_DECLARACION"
    private String resolucion;
    private List<CuotaMesItemDTO> meses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CuotaMesItemDTO {
        private Integer mes; // 1..12
        private Double limiteKg;
        private Boolean activo; // default true
    }
}
