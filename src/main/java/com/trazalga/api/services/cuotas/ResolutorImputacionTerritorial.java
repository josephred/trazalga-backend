package com.trazalga.api.services.cuotas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolutor centralizado de la imputación territorial de cuotas (TC.8).
 * <p>
 * Desde el 06-11-2026 (refinamiento 30-10) todas las declaraciones de origen descuentan cuota en la
 * comuna del usuario que declara ({@code usuario.comuna_id}): recolectores, armadores y áreas de manejo.
 * El parámetro {@code cuota_imputacion_armador} quedó sin efecto.
 */
@Component
public class ResolutorImputacionTerritorial {

    private final ReglaImputacionInscripcion inscripcion;

    @Autowired
    public ResolutorImputacionTerritorial(ReglaImputacionInscripcion inscripcion) {
        this.inscripcion = inscripcion;
    }

    /**
     * Regla de imputación para un tipo de declaración. Es la misma para todos: comuna del usuario,
     * con respaldo a la comuna de desembarque si el usuario no tiene comuna.
     *
     * @param tipoDeclaracion "RECOLECTOR", "ARMADOR", "AREA", etc.
     */
    public ReglaImputacionTerritorial resolver(String tipoDeclaracion) {
        return inscripcion;
    }

    public ReglaImputacionTerritorial getInscripcion() {
        return inscripcion;
    }
}
