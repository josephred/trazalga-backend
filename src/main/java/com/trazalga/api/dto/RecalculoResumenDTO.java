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
public class RecalculoResumenDTO {
    private boolean dryRun;
    private long totalProcesadas;
    private long totalActualizadas;
    private long totalOmitidas;
    private BigDecimal totalDesembarqueKg;
    private BigDecimal totalCapturaAnteriorKg;
    private BigDecimal totalCapturaNuevaKg;
    private BigDecimal variacionTotalKg;
    
    @Builder.Default
    private List<ItemRegularizacionDTO> regularizaciones = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRegularizacionDTO {
        private String tipoDeclaracion;
        private Long id;
        private String folio;
        private String fecha;
        private String motivo;
    }
}
