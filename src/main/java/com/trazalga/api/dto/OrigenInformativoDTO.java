package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TV.1 / TV.3 · DTO para orígenes biológicos vinculados a una recepción en planta.
 * No se compara con la romana; es puramente informativo para trazabilidad hacia atrás.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrigenInformativoDTO {
    private Long origenId;
    private String tipoDeclaracion; // RECOLECTOR | ARMADOR | AREA
    private String folio;
    private String declaradorNombre;
    private String declaradorRut;
    private double kg;
    private String humedadNombre;
    private Date fechaDeclaracion;
    private String caletaNombre;
    private String comunaNombre;
    @Builder.Default
    private String leyendaInformativa = "informativo: no se compara";
}
