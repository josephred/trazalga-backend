package com.trazalga.api.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoberturaCoordenadasDto {

    // Totales Globales
    private long totalCaletas;
    private long caletasConGps;
    private double porcentajeCaletas;

    private long totalVaraderos;
    private long varaderosConGps;
    private double porcentajeVaraderos;

    private long totalAmerb;
    private long amerbConGps;
    private double porcentajeAmerb;

    private long totalEntidades;
    private long totalConGps;
    private double porcentajeTotal;

    @Builder.Default
    private List<CoberturaRegionDto> porRegion = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoberturaRegionDto {
        private Long regionId;
        private String regionNombre;

        private long caletasTotal;
        private long caletasConGps;
        private double porcentajeCaletas;

        private long varaderosTotal;
        private long varaderosConGps;
        private double porcentajeVaraderos;

        private long amerbTotal;
        private long amerbConGps;
        private double porcentajeAmerb;

        private long totalEntidades;
        private long totalConGps;
        private double porcentajeTotal;
    }
}
