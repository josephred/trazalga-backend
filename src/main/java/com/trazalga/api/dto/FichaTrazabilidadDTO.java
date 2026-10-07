package com.trazalga.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaTrazabilidadDTO {
    private String tipoConsulta;
    private Long idConsulta;
    private Date fechaConsulta;

    // 1. Resumen de alertas arriba de todo
    private FichaAlertasDTO alertas;

    // 2. Orígenes (primero la declaración consultada, marcada; después las demás de la misma carga)
    @Builder.Default
    private List<FichaOrigenDTO> origenes = new ArrayList<>();

    // 3. Comercializadores (uno por salto; vacío si la venta fue directa a planta)
    @Builder.Default
    private List<FichaComercializadorDTO> comercializadores = new ArrayList<>();

    // 4. Planta de abastecimiento
    private FichaPlantaDTO planta;

    // Totales y metadatos del lote
    private BigDecimal totalKgOrigen;
    private String especiePredominante;
    private String humedadPredominante;
}
