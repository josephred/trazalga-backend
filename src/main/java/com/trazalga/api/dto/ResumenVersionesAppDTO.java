package com.trazalga.api.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO con el resumen de versiones de app móviles activas en el sistema (TP.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenVersionesAppDTO {
    private int totalUsuarios;
    private long totalPlantas;
    private long totalPlantasAnterioresA110;
    private Map<String, Long> distribucionVersiones;
    private List<UsuarioVersionItemDTO> plantasConAppAnteriorA110;
    private List<UsuarioVersionItemDTO> usuarios;
}
