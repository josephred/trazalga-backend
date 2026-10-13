package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * TV.3 · Bloque resumen comercializadorPlanta para GET /api/reportes/variacion-peso.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenComercializadorPlantaDTO {
    private long totalRecepciones;
    /** Recepciones con romana cuyos despachos no se encontraron: no evaluables, fuera del semáforo y del promedio. */
    private long recepcionesSinDespachos;
    private long recepcionesVerde;
    private long recepcionesAmarillo;
    private long recepcionesRojo;
    private java.util.Map<String, Long> totalesPorSemaforo;
    private Double promedioDeltaPct;
    private long sinHumedadVerificada;
    private List<ResultadoVariacionPesoDTO> topDesviaciones;
    private List<ResultadoVariacionPesoDTO> items;
}
