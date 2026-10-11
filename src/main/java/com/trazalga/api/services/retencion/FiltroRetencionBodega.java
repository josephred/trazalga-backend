package com.trazalga.api.services.retencion;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filtro de consulta para lotes en bodega virtual (TR.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltroRetencionBodega {
    private Date startDate;
    private Date endDate;
    private Long comercializadorId;
    private Long especieId;
    private String semaforo;
    private Boolean soloRetenidas;
    private Boolean soloEnBodega;
}
