package com.trazalga.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO enriquecido para la visualización de hallazgos en la Consola y Widgets (TA.3).
 * Incluye los metadatos normativos de la marca, el criterio estructurado y
 * los datos operativos enriquecidos de la declaración asociada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallazgoDetalleDTO {

    // Identificación y estado de la marca
    private Long id;
    private String marca;
    private Long reglaId;
    private String detalle;
    private Boolean resuelta;
    private String estadoGestion;
    private String resolucionTipo;
    private String observacionResolucion;
    private Date fechaResolucion;
    private Long resueltaPorUsuarioId;
    private Date createdAt;
    private String origen;
    private String claveIdempotencia;

    // Criterio estructurado
    private String criterioParametro;
    private String criterioUmbral;
    private String criterioValor;
    private String criterioUnidad;
    private String criterioTexto;

    // Datos enriquecidos de la declaración
    private String declaracionTipo;
    private Long declaracionId;
    private String folio;
    private Date fechaDeclaracion;
    private Date fechaExtraccion;
    private String hora;
    private Double kilos;
    private Double captura;
    private String especie;
    private String humedad;
    private String caleta;
    private String comuna;
    private String actorRut;
    private String actorNombre;
    private String actorPerfil;
    private String rpa;
    private String embarcacionCodigo;
    private String embarcacionNombre;
}
