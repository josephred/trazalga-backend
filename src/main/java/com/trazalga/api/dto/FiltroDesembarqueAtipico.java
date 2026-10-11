package com.trazalga.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filtro de consulta para auditoría de desembarques atípicos (TD.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltroDesembarqueAtipico {
    private Date startDate;
    private Date endDate;
    private Long especieId;
    private Long comunaId;
    private Long provinciaId;
    private Long regionId;
    private Long caletaId;
    private Long usuarioId;
    private String perfil;        // TODOS | RECOLECTOR | ARMADOR | AREA
    private String fuente;        // TODAS | MARCA | VIGENTE
    private String estadoGestion; // PENDIENTE | EN_REVISION | RESUELTA | DESCARTADA | SIN_MARCA | SIN_GESTIONAR
    private String criterioTipo;  // UMBRAL | ESTADISTICO
}
