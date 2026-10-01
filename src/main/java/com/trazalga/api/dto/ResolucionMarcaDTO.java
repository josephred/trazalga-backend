package com.trazalga.api.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolucionMarcaDTO {
    private String resolucionTipo; // LIBERADA | DECOMISO | SANCION | DESCARTADA
    private String observacion;
}
