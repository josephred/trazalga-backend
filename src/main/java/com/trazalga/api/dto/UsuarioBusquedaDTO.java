package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para resultados de búsqueda de usuarios / personas (TM.1).
 * Utilizado por el selector asíncrono de cuotas individuales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioBusquedaDTO {
    private Long id;
    private String rut;
    private String nombre;
    private String perfil;
    private String comuna;
    private Long comunaId;
    private Long regionId;
}
