package com.trazalga.api.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculoCapturaResult {
    private boolean exitoso;
    private BigDecimal captura;
    private BigDecimal factorAplicado;
    private Long factorConversionId;
    private String mensaje;
}
