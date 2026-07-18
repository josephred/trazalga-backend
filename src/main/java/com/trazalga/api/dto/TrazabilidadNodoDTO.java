package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrazabilidadNodoDTO {
    private String idUnico; // ej. RECOLECTOR:1
    private String tipoNodo; // ej. RECOLECTOR, COMERCIALIZADOR, PLANTA_PRODUCCION
    private String nombreActor;
    private String rutActor;
    private Date fecha;
    private BigDecimal cantidad;
    private String descripcionEvento; // ej. "Extracción en Caleta San Pedro" o "Despacho a Planta"
    private Long idDeclaracion;
    private String folio;
}
