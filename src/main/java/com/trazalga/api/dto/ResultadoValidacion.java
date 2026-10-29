package com.trazalga.api.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoValidacion {

    public enum DecisionValidacion {
        PERMITIR,
        MARCAR,
        RECHAZAR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarcaItem {
        private String marca; // EN_VEDA | LED_EXCEDIDO | CUOTA_EXCEDIDA | POSTERIOR_CIERRE | DECLARACION_EXTEMPORANEA | DESEMBARQUE_ATIPICO
        private String detalle;
        private Long reglaId;
        private String regla;
        private com.trazalga.api.services.hallazgos.CriterioHallazgo criterio;
    }

    private DecisionValidacion decision;
    private String motivoRechazo;

    @Builder.Default
    private List<MarcaItem> marcas = new ArrayList<>();

    @Builder.Default
    private List<String> advertencias = new ArrayList<>();

    private BigDecimal capturaCalculada;
    private BigDecimal factorAplicado;
    private Long factorConversionId;

    /**
     * Método cuya cuota recibe la captura por imputación cruzada por veda (refinamiento 23-10).
     * Nulo cuando la declaración consume con su método declarado. Se persiste en
     * {@code extraccion_tipo_imputacion_id}.
     */
    private Long extraccionTipoImputacionId;

    /** Texto es-CL que explica la imputación cruzada, o su ausencia, cuando la declaración está en veda. */
    private String imputacionDetalle;

    public boolean esRechazado() {
        return decision == DecisionValidacion.RECHAZAR;
    }

    public boolean requiereMarca() {
        return decision == DecisionValidacion.MARCAR || (!marcas.isEmpty() && decision != DecisionValidacion.RECHAZAR);
    }
}
