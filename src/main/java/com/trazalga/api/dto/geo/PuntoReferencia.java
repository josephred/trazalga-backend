package com.trazalga.api.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa el punto geográfico de referencia resuelto para una declaración
 * de origen extractivo o fiscalización (TG.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PuntoReferencia {
    private String tipo;     // "VARADERO", "CALETA", "AMERB", "LUGAR_DECLARACION"
    private Long id;         // ID de la entidad (varadero.id, caleta.id, amerb.id)
    private String nombre;   // Nombre descriptivo del punto
    private Double lat;      // Latitud WGS84 decimal
    private Double lon;      // Longitud WGS84 decimal
    private String fuente;   // "VARADERO_DECLARADO", "CALETA", "VARADERO_CALETA", "AMERB", "GPS_DECLARACION"

    /**
     * Constructor de retrocompatibilidad para ValidacionDeclaracionService
     */
    public PuntoReferencia(String tipo, String nombre, Double lat, Double lon) {
        this.tipo = tipo;
        this.nombre = nombre;
        this.lat = lat;
        this.lon = lon;
        this.fuente = tipo;
    }
}
