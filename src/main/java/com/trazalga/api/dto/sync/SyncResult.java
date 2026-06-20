package com.trazalga.api.dto.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resumen del poblamiento de una tabla a partir del API de Sernapesca. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {

    private String entidad;
    private boolean ok;
    private int obtenidos;
    private int insertados;
    private int actualizados;
    private int omitidos;
    private String mensaje;

    public static SyncResult error(String entidad, String mensaje) {
        return SyncResult.builder()
                .entidad(entidad)
                .ok(false)
                .mensaje(mensaje)
                .build();
    }
}
