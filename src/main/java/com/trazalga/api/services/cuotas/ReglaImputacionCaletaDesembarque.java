package com.trazalga.api.services.cuotas;

import org.springframework.stereotype.Component;

import com.trazalga.api.dto.ContextoDeclaracion;

/**
 * Estrategia de imputación por comuna de la caleta de desembarque (TC.8).
 * Atribuye la extracción a la comuna física donde se produce el desembarque.
 */
@Component("reglaImputacionCaletaDesembarque")
public class ReglaImputacionCaletaDesembarque implements ReglaImputacionTerritorial {

    public static final String CLAVE = "CALETA_DESEMBARQUE";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Long comunaImputacion(ContextoDeclaracion ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getComunaDesembarqueId() != null) {
            return ctx.getComunaDesembarqueId();
        }
        // Respaldo de seguridad si sólo viniera inscripción
        return ctx.getComunaInscripcionId();
    }

    @Override
    public String sqlComuna(String aliasDeclaracion, String aliasUsuario) {
        String d = (aliasDeclaracion != null && !aliasDeclaracion.isBlank()) ? aliasDeclaracion : "d";
        return String.format("%s.comuna_id", d);
    }
}
