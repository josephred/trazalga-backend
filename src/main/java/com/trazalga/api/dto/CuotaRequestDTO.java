package com.trazalga.api.dto;

import java.util.Date;
import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para creación y actualización de cuotas de extracción (TC.2 / TC.9).
 * Contiene únicamente identificadores y campos de negocio editables.
 * No expone campos de auditoría ni variables de control de estado o cierre administrativo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuotaRequestDTO {

    private Long id;

    @NotNull(message = "La especie es obligatoria")
    private Long especieId;

    @NotNull(message = "El método de extracción es obligatorio")
    private Long extraccionTipoId;

    @NotNull(message = "El límite en kg es obligatorio")
    @Positive(message = "El límite de la cuota debe ser mayor que 0 kg")
    private Double limiteKg;

    private String perfil;

    @Builder.Default
    private String ambito = "AREA_LIBRE";

    private Long regionId;
    private Long macrozonaId;
    private Long provinciaId;
    private Long comunaId;
    private Set<Long> comunaIds;

    private Long usuarioId;
    private Long amerbId;
    private Long humedadEstadoId;

    @Builder.Default
    private String nivelAgregacion = "COMUNA";

    @Builder.Default
    private String metrica = "CAPTURA";

    @Builder.Default
    private Boolean esPlantilla = false;

    @Builder.Default
    private String modoAccion = "SOLO_ALERTA";

    private String periodo;
    private Date fechaInicio;
    private Date fechaFin;
    private String resolucion;

    @Builder.Default
    private Boolean activo = true;
}
