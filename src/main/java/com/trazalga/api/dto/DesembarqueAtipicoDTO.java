package com.trazalga.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el reporte y auditoría de hallazgos de desembarque atípico (TD.1).
 * Modela registros de la unión de marcas activas (DESEMBARQUE_ATIPICO) y
 * declaraciones operativas sin marca que superan el umbral vigente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesembarqueAtipicoDTO {

    // Identificación de la fila
    private String id;
    private String folio;
    private String tipo;           // RECOLECTOR | ARMADOR | AREA
    private String perfil;         // RECOLECTOR | ARMADOR | AREA
    private Long declaracionId;

    // Actor y embarcación
    private String rut;
    private String nombre;
    private String embarcacionCodigo;
    private String embarcacionNombre;

    // Ubicación
    private String caleta;
    private String comuna;
    private String provincia;
    private String region;

    // Temporalidad
    private Date fechaDeclaracion;
    private Date fechaExtraccion;
    private String hora;

    // Recurso y volúmenes
    private String especie;
    private String humedad;
    private Double kilos;          // Desembarque físico
    private Double captura;        // Captura biológica calculada

    // Criterio estructurado de auditoría
    private String criterioTexto;
    private String criterioParametro;
    private String criterioUmbral;
    private String criterioValor;
    private String criterioUnidad;
    private String fuente;         // MARCA | VIGENTE

    // Metadatos de la marca de fiscalización
    private Long marcaId;
    private Boolean resuelta;
    private String estadoGestion;  // PENDIENTE | EN_REVISION | RESUELTA | DESCARTADA | SIN_MARCA
}
