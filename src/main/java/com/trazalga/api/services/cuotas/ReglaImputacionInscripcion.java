package com.trazalga.api.services.cuotas;

import org.springframework.stereotype.Component;

import com.trazalga.api.dto.ContextoDeclaracion;

/**
 * Estrategia de imputación por comuna de inscripción del pescador / armador (TC.8).
 * En caso de que el pescador no tenga comuna de inscripción asignada,
 * respalda a la comuna de desembarque para cerrar la brecha K15.
 */
@Component("reglaImputacionInscripcion")
public class ReglaImputacionInscripcion implements ReglaImputacionTerritorial {

    public static final String CLAVE = "INSCRIPCION";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Long comunaImputacion(ContextoDeclaracion ctx) {
        if (ctx == null) {
            return null;
        }
        // Respaldo de inscripción a desembarque (K15)
        if (ctx.getComunaInscripcionId() != null) {
            return ctx.getComunaInscripcionId();
        }
        return ctx.getComunaDesembarqueId();
    }

    @Override
    public String sqlComuna(String aliasDeclaracion, String aliasUsuario) {
        String u = (aliasUsuario != null && !aliasUsuario.isBlank()) ? aliasUsuario : "u";
        String d = (aliasDeclaracion != null && !aliasDeclaracion.isBlank()) ? aliasDeclaracion : "d";
        return String.format("COALESCE(%s.comuna_id, %s.comuna_id)", u, d);
    }
}
