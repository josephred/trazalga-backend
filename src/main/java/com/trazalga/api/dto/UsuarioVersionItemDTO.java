package com.trazalga.api.dto;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la información de versión de app de un usuario (TP.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioVersionItemDTO {
    private Long id;
    private String rut;
    private String nombreCompleto;
    private Long perfilId;
    private String perfilNombre;
    private String appVersion;
    private Date appVersionAt;
    private boolean esAnteriorA110;
}
