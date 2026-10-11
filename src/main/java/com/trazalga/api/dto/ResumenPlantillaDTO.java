package com.trazalga.api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumen consolidado del consumo de una plantilla individual (TM.2 / K8).
 * Contiene el recuento de personas con actividad, sobre límite y el máximo porcentaje.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenPlantillaDTO {
    private int personasConActividad;
    private int personasSobreLimite;
    private Double maxPorcentaje;
    private String textoConsumo; // e.g. "1 de 2 personas sobre su tope (máx. 110 %)"
    private BigDecimal consumoTotal;
    private List<ConsumoPersonaDTO> detallePersonas;
}
