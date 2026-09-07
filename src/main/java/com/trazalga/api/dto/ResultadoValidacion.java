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
        private String marca; // EN_VEDA | LED_EXCEDIDO | CUOTA_EXCEDIDA | POSTERIOR_CIERRE | DESEMBARQUE_ATIPICO
        private String detalle;
        private Long reglaId;
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

    public boolean esRechazado() {
        return decision == DecisionValidacion.RECHAZAR;
    }

    public boolean requiereMarca() {
        return decision == DecisionValidacion.MARCAR || (!marcas.isEmpty() && decision != DecisionValidacion.RECHAZAR);
    }
}
