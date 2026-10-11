package com.trazalga.api.services.indicadores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;

import lombok.NoArgsConstructor;

/**
 * Criterio de umbral absoluto para desembarques atípicos (TD.1).
 * Compara los kilos desembarcados contra el parámetro desembarque_umbral_atipico_kg (default 5.000 kg).
 */
@Component
@NoArgsConstructor
public class UmbralAbsolutoCriterio implements CriterioAtipico {

    private ConfiguracionGeneralService configService;

    @Autowired
    public UmbralAbsolutoCriterio(ConfiguracionGeneralService configService) {
        this.configService = configService;
    }

    @Override
    public String clave() {
        return "UMBRAL_ABSOLUTO";
    }

    @Override
    public ResultadoCriterioAtipico evaluar(ContextoEvaluacionAtipico ctx) {
        if (ctx == null || ctx.getKilos() == null) {
            return ResultadoCriterioAtipico.builder().atipico(false).build();
        }

        double umbral = (configService != null)
                ? configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)
                : 5000.0;

        double kilos = ctx.getKilos();
        if (kilos > umbral) {
            CriterioHallazgo ch = CriterioHallazgo.deKilos("desembarque_umbral_atipico_kg", umbral, kilos);
            return ResultadoCriterioAtipico.builder()
                    .atipico(true)
                    .criterioClave(clave())
                    .parametro("desembarque_umbral_atipico_kg")
                    .umbral(String.valueOf(umbral))
                    .valor(String.valueOf(kilos))
                    .unidad("kg")
                    .texto(ch.texto())
                    .build();
        }

        return ResultadoCriterioAtipico.builder().atipico(false).build();
    }
}
