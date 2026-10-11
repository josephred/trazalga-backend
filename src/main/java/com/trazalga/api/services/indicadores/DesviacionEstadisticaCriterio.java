package com.trazalga.api.services.indicadores;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.parametros.ParametrosService;

import lombok.NoArgsConstructor;

/**
 * Criterio de desviación estadística para desembarques atípicos (TD.2 / D7).
 * Evalúa si la faena supera en z > sigma el promedio histórico del grupo (perfil, caleta, especie, humedad).
 * Controlado por el parámetro 'desembarque_atipico_estadistico_activo' (default false).
 */
@Component
@NoArgsConstructor
public class DesviacionEstadisticaCriterio implements CriterioAtipico {

    private ConfiguracionGeneralService configService;
    private ParametrosService parametrosService;

    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");

    @Autowired
    public DesviacionEstadisticaCriterio(ConfiguracionGeneralService configService, ParametrosService parametrosService) {
        this.configService = configService;
        this.parametrosService = parametrosService;
    }

    public DesviacionEstadisticaCriterio(ConfiguracionGeneralService configService) {
        this.configService = configService;
    }

    @Override
    public String clave() {
        return "DESVIACION_ESTADISTICA";
    }

    @Override
    public ResultadoCriterioAtipico evaluar(ContextoEvaluacionAtipico ctx) {
        if (ctx == null || ctx.getKilos() == null) {
            return ResultadoCriterioAtipico.builder().atipico(false).build();
        }

        boolean activo = false;
        if (parametrosService != null && parametrosService.desembarque() != null) {
            activo = parametrosService.desembarque().estadisticoActivo();
        } else if (configService != null) {
            activo = configService.getBoolean("desembarque_atipico_estadistico_activo", false);
        }

        if (!activo) {
            return ResultadoCriterioAtipico.builder().atipico(false).build();
        }

        double sigma = (configService != null)
                ? configService.getDouble("desembarque_atipico_sigma", 2.5)
                : 2.5;
        int minMuestras = (configService != null)
                ? configService.getInt("desembarque_atipico_min_muestras", 10)
                : 10;

        List<Double> historico = ctx.getHistoricoKilos();
        if (historico == null || historico.size() < minMuestras) {
            return ResultadoCriterioAtipico.builder().atipico(false).build();
        }

        int n = historico.size();
        double sum = 0.0;
        for (Double val : historico) {
            sum += (val != null ? val : 0.0);
        }
        double media = sum / n;

        double sumSq = 0.0;
        for (Double val : historico) {
            double diff = (val != null ? val : 0.0) - media;
            sumSq += diff * diff;
        }
        double desv = (n > 1) ? Math.sqrt(sumSq / (n - 1)) : 0.0;

        if (desv <= 0.0001) {
            return ResultadoCriterioAtipico.builder().atipico(false).build();
        }

        double kilos = ctx.getKilos();
        double z = (kilos - media) / desv;

        if (z > sigma) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_CL);
            symbols.setGroupingSeparator('.');
            symbols.setDecimalSeparator(',');

            DecimalFormat df1 = new DecimalFormat("0.0", symbols);
            DecimalFormat dfKilos = new DecimalFormat("#,##0", symbols);

            String zStr = df1.format(z);
            String sigmaStr = df1.format(sigma);
            String mediaStr = dfKilos.format(Math.round(media));
            String desvStr = dfKilos.format(Math.round(desv));

            String texto = String.format("z = %s > %s (media %s kg, desv. %s kg, n = %d)",
                    zStr, sigmaStr, mediaStr, desvStr, n);

            return ResultadoCriterioAtipico.builder()
                    .atipico(true)
                    .criterioClave(clave())
                    .parametro("desembarque_atipico_sigma")
                    .umbral(String.valueOf(sigma))
                    .valor(String.format(Locale.US, "%.2f", z))
                    .unidad("sigma")
                    .texto(texto)
                    .build();
        }

        return ResultadoCriterioAtipico.builder().atipico(false).build();
    }
}
