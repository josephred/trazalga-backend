package com.trazalga.api.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado del recálculo de la imputación cruzada por veda (refinamiento 23-10, punto 1).
 * {@code detalle} lista las declaraciones cuya imputación cambia y las que quedan sin cuota de destino.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenImputacionCruzadaDTO {

    private String desde;
    private String hasta;
    private boolean aplicado;
    private boolean reglaActiva;
    private String metodoEnVeda;
    private String metodoDestino;
    private int declaracionesEvaluadas;
    private int imputacionesCruzadas;
    private int sinCuotaDestino;
    private int cambios;
    private String mensaje;

    @Builder.Default
    private List<Item> detalle = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String tipo;
        private Long declaracionId;
        private String folio;
        private String fechaExtraccion;
        private String especie;
        private String metodoDeclarado;
        private Long imputacionAnteriorId;
        private Long imputacionNuevaId;
        /** NO_APLICA | CRUZADA | SIN_CUOTA_DESTINO */
        private String estado;
        private boolean cambia;
        private Long cuotaDestinoId;
        private String detalle;
    }
}
