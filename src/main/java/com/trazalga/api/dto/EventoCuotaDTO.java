package com.trazalga.api.dto;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para eventos del ciclo de vida de cuotas de extracción (TC.2 / D11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoCuotaDTO {
    private Long id;
    private Long cuotaId;
    private String tipo;
    private String motivo;
    private String detalle;
    private Long usuarioId;
    private String usuarioNombre;
    private Date createdAt;
}
