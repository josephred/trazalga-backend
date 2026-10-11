package com.trazalga.api.services.indicadores;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.services.ConfiguracionGeneralService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesviacionEstadisticaCriterioTest {

    @Mock
    private ConfiguracionGeneralService configService;

    private DesviacionEstadisticaCriterio criterio;

    @BeforeEach
    void setUp() {
        criterio = new DesviacionEstadisticaCriterio(configService);
    }

    @Test
    @DisplayName("TD.2: Interruptor desactivado no dispara evaluación estadística")
    void testInterruptorDesactivado_RetornaNoAtipico() {
        when(configService.getBoolean("desembarque_atipico_estadistico_activo", false)).thenReturn(false);

        ContextoEvaluacionAtipico ctx = ContextoEvaluacionAtipico.builder()
                .kilos(1150.0)
                .historicoKilos(List.of(800.0, 800.0, 800.0, 800.0, 800.0, 800.0, 800.0, 800.0, 800.0, 800.0))
                .build();

        ResultadoCriterioAtipico res = criterio.evaluar(ctx);
        assertFalse(res.isAtipico(), "Con el parámetro apagado no debe marcarse");
    }

    @Test
    @DisplayName("TD.2: Muestra inferior a mínimo (10) no dispara evaluación")
    void testMuestraInsuficiente_RetornaNoAtipico() {
        when(configService.getBoolean("desembarque_atipico_estadistico_activo", false)).thenReturn(true);
        when(configService.getDouble("desembarque_atipico_sigma", 2.5)).thenReturn(2.5);
        when(configService.getInt("desembarque_atipico_min_muestras", 10)).thenReturn(10);

        ContextoEvaluacionAtipico ctx = ContextoEvaluacionAtipico.builder()
                .kilos(1150.0)
                .historicoKilos(List.of(800.0, 850.0, 750.0, 800.0)) // solo 4 muestras
                .build();

        ResultadoCriterioAtipico res = criterio.evaluar(ctx);
        assertFalse(res.isAtipico());
    }

    @Test
    @DisplayName("TD.2: Caso de aceptación del plan: 12 declaraciones con media 800 y desv 100, faena 1.150 kg (z = 3.5)")
    void testCasoAceptacionPlan_DisparaZScore() {
        when(configService.getBoolean("desembarque_atipico_estadistico_activo", false)).thenReturn(true);
        when(configService.getDouble("desembarque_atipico_sigma", 2.5)).thenReturn(2.5);
        when(configService.getInt("desembarque_atipico_min_muestras", 10)).thenReturn(10);

        // Muestra de 12 elementos con media = 800 y desv aprox = 100
        // Valores simétricos alrededor de 800
        List<Double> muestra = List.of(
                700.0, 900.0,
                700.0, 900.0,
                700.0, 900.0,
                700.0, 900.0,
                700.0, 900.0,
                700.0, 900.0
        );

        ContextoEvaluacionAtipico ctx = ContextoEvaluacionAtipico.builder()
                .kilos(1150.0)
                .historicoKilos(muestra)
                .build();

        ResultadoCriterioAtipico res = criterio.evaluar(ctx);

        assertTrue(res.isAtipico(), "Debe marcarse como atípico estadístico");
        assertEquals("DESVIACION_ESTADISTICA", res.getCriterioClave());
        assertEquals("desembarque_atipico_sigma", res.getParametro());
        assertEquals("sigma", res.getUnidad());
        assertNotNull(res.getTexto());
        assertTrue(res.getTexto().contains("z = 3,3 > 2,5") || res.getTexto().contains("z = 3,4 > 2,5") || res.getTexto().contains("z = 3,5 > 2,5"),
                "El texto debe reportar z > sigma: " + res.getTexto());
        assertTrue(res.getTexto().contains("media 800 kg"), "Debe reportar media 800 kg");
        assertTrue(res.getTexto().contains("n = 12"), "Debe reportar tamaño muestral 12");
    }

    @Test
    @DisplayName("TD.2: Valor dentro de rango normal (z < 2.5) no se marca")
    void testValorNormal_NoDispara() {
        when(configService.getBoolean("desembarque_atipico_estadistico_activo", false)).thenReturn(true);
        when(configService.getDouble("desembarque_atipico_sigma", 2.5)).thenReturn(2.5);
        when(configService.getInt("desembarque_atipico_min_muestras", 10)).thenReturn(10);

        List<Double> muestra = List.of(
                700.0, 900.0, 700.0, 900.0, 700.0, 900.0,
                700.0, 900.0, 700.0, 900.0, 700.0, 900.0
        );

        ContextoEvaluacionAtipico ctx = ContextoEvaluacionAtipico.builder()
                .kilos(850.0) // z < 0.5
                .historicoKilos(muestra)
                .build();

        ResultadoCriterioAtipico res = criterio.evaluar(ctx);
        assertFalse(res.isAtipico());
    }
}
