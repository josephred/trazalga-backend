package com.trazalga.api.dto;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filtro para la consulta paginada de sobrepasos de cuotas (TQ.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltroSobrepasos {
    private Date startDate;
    private Date endDate;
    private Long cuotaId;
    private Long comunaId;
    private Long extraccionTipoId;
    private List<String> marcas;   // CUOTA_EXCEDIDA, POSTERIOR_CIERRE, DECLARACION_EXTEMPORANEA
    private String estadoGestion;  // PENDIENTE | EN_REVISION | RESUELTA | DESCARTADA
    private Boolean resuelta;
}
