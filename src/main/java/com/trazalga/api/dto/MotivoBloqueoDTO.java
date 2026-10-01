package com.trazalga.api.dto;

import java.util.Date;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotivoBloqueoDTO {
    private String marca;
    private String detalle;
    private Date fechaDeteccion;
    private Long marcaId;
    private String resolucionTipo;
    private String observacionResolucion;
    private String estadoGestion;
}
