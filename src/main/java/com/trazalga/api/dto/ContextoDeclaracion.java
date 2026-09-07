package com.trazalga.api.dto;

import java.math.BigDecimal;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextoDeclaracion {
    private String tipoDeclaracion; // "RECOLECTOR", "ARMADOR", "AREA"
    private Long usuarioId;
    private Long buzoId;
    private Long embarcacionId;
    private Long amerbId;
    private Long especieId;
    private Long humedadEstadoId;
    private Long extraccionTipoId;
    private Long comunaDesembarqueId;
    private Long comunaInscripcionId;
    private Long regionId;
    private Date fechaExtraccion;
    private Date fechaDeclaracion;
    private BigDecimal desembarqueKg;
}
