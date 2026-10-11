package com.trazalga.api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que describe el consumo de cuota individual por persona (TM.2 / K8).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumoPersonaDTO {
    private Long usuarioId;
    private String rut;
    private String nombre;
    private String perfil;
    private String comunaInscripcion;
    private List<String> embarcacionesUsadas;
    private int cantidadDeclaraciones;
    private BigDecimal consumo;
    private BigDecimal limiteEfectivo;
    private Double porcentaje;
    private BigDecimal exceso;
}
