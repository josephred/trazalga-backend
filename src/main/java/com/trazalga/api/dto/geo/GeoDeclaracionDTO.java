package com.trazalga.api.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value Object con los metadatos de doble georreferenciación de una declaración (TG.3).
 * Incluye el GPS capturado por el dispositivo, el punto de referencia de origen físico,
 * la distancia Haversine en kilómetros, la evaluación y el umbral aplicado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoDeclaracionDTO {

    private GpsInfo gps;
    private ReferenciaInfo referencia;
    private Double distanciaKm;
    private String evaluacion; // COHERENTE, LEJANA, PRECISION_BAJA, SIN_GPS, SIN_REFERENCIA
    private Double umbralKm;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpsInfo {
        private Double lat;
        private Double lon;
        private Double precisionM;
        private String capturadoEn;
        private Boolean offline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenciaInfo {
        private String tipo;
        private Long id;
        private String nombre;
        private Double lat;
        private Double lon;
        private String fuente;
    }
}
