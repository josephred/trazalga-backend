package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrazabilidadNodoDTO {
    private String idUnico; // ej. RECOLECTOR:1
    private String tipoNodo; // ej. RECOLECTOR, COMERCIALIZADOR, PLANTA_PRODUCCION
    private String nombreActor;
    private String rutActor;
    private Date fecha;
    private BigDecimal cantidad;
    private String descripcionEvento; // ej. "Extracción en Caleta San Pedro" o "Despacho a Planta"
    private Long idDeclaracion;
    private String folio;

    // Campos detallados enriquecidos para la ficha modal:
    private String hora;
    private String codigoSernapesca;
    private String especie;
    private String estadoHumedad;
    private String porcentajeHumedad;
    private String metodoExtraccion;
    private String caleta;
    private String comuna;
    private String region;
    private String varaderoOAmerb;
    private Double latitud;
    private Double longitud;
    private String nombreDestinatario;
    private String rutDestinatario;
    private String vehiculoTransporte;
    private String patente;
    private String patenteCarro;
    private String chofer;
    private String rutChofer;
    private String docTipo;
    private String docNumero;
    private Date docFecha;
    private String declaracionesSeleccionadas;
    private String estado;
    private String embarcacion;
    private String buzo;
}
