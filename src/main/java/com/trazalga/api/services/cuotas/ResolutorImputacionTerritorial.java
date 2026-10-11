package com.trazalga.api.services.cuotas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trazalga.api.services.parametros.ParametrosService;

/**
 * Resolutor centralizado de estrategias de imputación territorial (TC.8 / D1).
 * Resuelve la regla aplicable según el tipo de declaración y el parámetro general
 * de configuración 'cuota_imputacion_armador'.
 */
@Component
public class ResolutorImputacionTerritorial {

    private final ReglaImputacionInscripcion inscripcion;
    private final ReglaImputacionCaletaDesembarque caletaDesembarque;
    private final ParametrosService parametrosService;

    @Autowired
    public ResolutorImputacionTerritorial(
            ReglaImputacionInscripcion inscripcion,
            ReglaImputacionCaletaDesembarque caletaDesembarque,
            ParametrosService parametrosService) {
        this.inscripcion = inscripcion;
        this.caletaDesembarque = caletaDesembarque;
        this.parametrosService = parametrosService;
    }

    /**
     * Resuelve la regla de imputación según el tipo de actor / declaración:
     * - RECOLECTOR: siempre INSCRIPCION (con respaldo a desembarque por K15).
     * - ARMADOR: según parámetro 'cuota_imputacion_armador' (default INSCRIPCION por D1).
     * - AREA / otros: CALETA_DESEMBARQUE.
     *
     * @param tipoDeclaracion "RECOLECTOR", "ARMADOR", "AREA", etc.
     * @return Estrategia de imputación territorial correspondiente.
     */
    public ReglaImputacionTerritorial resolver(String tipoDeclaracion) {
        if ("RECOLECTOR".equalsIgnoreCase(tipoDeclaracion)) {
            return inscripcion;
        }

        if ("ARMADOR".equalsIgnoreCase(tipoDeclaracion)) {
            String modo = (parametrosService != null && parametrosService.cuotas() != null)
                    ? parametrosService.cuotas().imputacionArmador()
                    : "INSCRIPCION";
            if ("CALETA_DESEMBARQUE".equalsIgnoreCase(modo)) {
                return caletaDesembarque;
            }
            return inscripcion; // Default acordado en D1
        }

        // Para AMERB / AREA o declaracion general
        return caletaDesembarque;
    }

    public ReglaImputacionTerritorial getInscripcion() {
        return inscripcion;
    }

    public ReglaImputacionTerritorial getCaletaDesembarque() {
        return caletaDesembarque;
    }
}
