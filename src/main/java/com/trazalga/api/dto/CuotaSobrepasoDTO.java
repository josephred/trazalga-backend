package com.trazalga.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO enriquecido para la auditoría y reporte de sobrepasos de cuota (TQ.1).
 * Unifica los datos de la marca de fiscalización (CUOTA_EXCEDIDA, POSTERIOR_CIERRE, DECLARACION_EXTEMPORANEA),
 * los atributos normativos de la cuota y los datos operativos de la declaración causante.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuotaSobrepasoDTO {

    // Identificación de la marca
    private Long marcaId;
    private String marca;          // CUOTA_EXCEDIDA | POSTERIOR_CIERRE | DECLARACION_EXTEMPORANEA
    private String detalle;
    private Boolean resuelta;
    private String estadoGestion;  // PENDIENTE | EN_REVISION | RESUELTA | DESCARTADA
    private Date createdAt;

    // Criterio estructurado
    private String criterioParametro;
    private String criterioUmbral;
    private String criterioValor;
    private String criterioUnidad;
    private String criterioTexto;

    // Atributos normativos de la cuota (reglaId)
    private Long cuotaId;
    private String cuotaAlcance;   // «Comunal: Coquimbo», «Provincia de Huasco», «Regional: Atacama», etc.
    private String cuotaEspecie;
    private String cuotaMetodo;    // Varado / Barreteado
    private String cuotaVigencia;  // «2026-10»
    private Double cuotaLimiteEfectivo;
    private String cuotaHumedad;

    // Métricas del sobrepaso
    private Double consumoAcumulado; // Valor observado con esta faena
    private Double porcentajeConsumo; // Porcentaje alcanzado sobre el límite
    private Double excesoKg;         // Kilos en exceso sobre el límite efectivo

    // Datos operativos de la declaración causante
    private String declaracionTipo;  // RECOLECTOR | ARMADOR | AREA
    private Long declaracionId;
    private String folio;
    private Date fechaDeclaracion;
    private Date fechaExtraccion;
    private String hora;
    private Double kilos;            // Desembarque físico
    private Double captura;          // Captura biológica calculada

    // Actor y embarcación
    private String actorRut;
    private String actorNombre;
    private String actorPerfil;
    private String embarcacionCodigo;
    private String embarcacionNombre;
    private String caleta;
    private String comuna;
}
