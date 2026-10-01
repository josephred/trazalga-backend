package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.models.FactorConversionModel;

@ExtendWith(MockitoExtension.class)
public class CapturaServiceTest {

    @Mock
    private FactorConversionService factorConversionService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @InjectMocks
    private CapturaService capturaService;

    private FactorConversionModel factorVigente;

    @BeforeEach
    void setUp() {
        factorVigente = new FactorConversionModel();
        factorVigente.setId(10L);
        factorVigente.setFactor(new BigDecimal("3.5800"));
    }

    @Test
    void testCalcularCapturaConFactorVigente() {
        when(factorConversionService.findFactorVigente(eq(1L), eq(2L), any(Date.class)))
                .thenReturn(Optional.of(factorVigente));

        CalculoCapturaResult result = capturaService.calcular(1L, 2L, new Date(), new BigDecimal("1000.00"));

        assertTrue(result.isExitoso());
        assertEquals(new BigDecimal("3580.00"), result.getCaptura());
        assertEquals(new BigDecimal("3.5800"), result.getFactorAplicado());
        assertEquals(10L, result.getFactorConversionId());
    }

    @Test
    void testCalcularCapturaSinFactorPoliticaDefault() {
        when(factorConversionService.findFactorVigente(anyLong(), anyLong(), any(Date.class)))
                .thenReturn(Optional.empty());
        when(configuracionGeneralService.getValor("captura_politica_sin_factor", "USAR_DEFAULT"))
                .thenReturn("USAR_DEFAULT");
        when(configuracionGeneralService.getDouble("captura_factor_default", 1.0))
                .thenReturn(1.0);

        CalculoCapturaResult result = capturaService.calcular(1L, 2L, new Date(), new BigDecimal("500.00"));

        assertTrue(result.isExitoso());
        assertEquals(new BigDecimal("500.00"), result.getCaptura());
        assertEquals(new BigDecimal("1.0000"), result.getFactorAplicado());
        assertNull(result.getFactorConversionId());
    }

    @Test
    void testCalcularCapturaSinFactorPoliticaRechazar() {
        when(factorConversionService.findFactorVigente(anyLong(), anyLong(), any(Date.class)))
                .thenReturn(Optional.empty());
        when(configuracionGeneralService.getValor("captura_politica_sin_factor", "USAR_DEFAULT"))
                .thenReturn("RECHAZAR_DECLARACION");

        CalculoCapturaResult result = capturaService.calcular(1L, 2L, new Date(), new BigDecimal("500.00"));

        assertFalse(result.isExitoso());
        assertNotNull(result.getMensaje());
        assertTrue(result.getMensaje().contains("No existe un factor"));
    }

    @Test
    void testCalcularDesembarqueNegativoRechazado() {
        CalculoCapturaResult result = capturaService.calcular(1L, 2L, new Date(), new BigDecimal("-10.00"));

        assertFalse(result.isExitoso());
        assertTrue(result.getMensaje().contains("mayor o igual a 0"));
    }

    @Test
    void testCalcularCapturaIgualADesembarqueValidoConFactorUno() {
        FactorConversionModel factorUno = new FactorConversionModel();
        factorUno.setId(8L);
        factorUno.setFactor(new BigDecimal("1.0000")); // Caso Huiro Macro Húmedo

        when(factorConversionService.findFactorVigente(eq(8L), eq(1L), any(Date.class)))
                .thenReturn(Optional.of(factorUno));

        CalculoCapturaResult result = capturaService.calcular(8L, 1L, new Date(), new BigDecimal("1000.00"));

        assertTrue(result.isExitoso());
        assertEquals(new BigDecimal("1000.00"), result.getCaptura());
        assertEquals(new BigDecimal("1.0000"), result.getFactorAplicado());
    }

    @Test
    void testInvarianteRechazaCapturaMenorQueDesembarque() {
        FactorConversionModel factorMenorQueUno = new FactorConversionModel();
        factorMenorQueUno.setId(99L);
        factorMenorQueUno.setFactor(new BigDecimal("0.8500")); // Factor anómalo < 1

        when(factorConversionService.findFactorVigente(eq(1L), eq(2L), any(Date.class)))
                .thenReturn(Optional.of(factorMenorQueUno));

        CalculoCapturaResult result = capturaService.calcular(1L, 2L, new Date(), new BigDecimal("1000.00"));

        assertFalse(result.isExitoso());
        assertNotNull(result.getMensaje());
        assertTrue(result.getMensaje().contains("no puede ser menor que el desembarque físico"));
    }

    private static record DeclaracionSintetica(long id, Long especieId, Long humedadEstadoId, BigDecimal desembarqueKg) {}

    @Test
    @org.junit.jupiter.api.DisplayName("Recálculo con mezcla de estados de humedad reproduce 2.300t -> ~4.680t (factor ≈2.035)")
    void testRecalculoConMezclaHumedades() {
        // Reproducción del caso observado en la sesión del 25-sep:
        // Desembarque total: 2.300 t (2.300.000 kg)
        // Factores legales: Húmedo=1.1300, Semihúmedo=1.7500, Semiseco=2.7000, Seco=3.5800
        // Mezcla ponderada:
        // - Húmedo:      1.070.000 kg x 1.1300 = 1.209.100 kg
        // - Semihúmedo:    300.000 kg x 1.7500 =   525.000 kg
        // - Semiseco:      430.000 kg x 2.7000 = 1.161.000 kg
        // - Seco:          500.000 kg x 3.5800 = 1.790.000 kg
        // Total desembarque = 2.300.000 kg (2.300 t)
        // Total captura     = 4.685.100 kg (~4.685 t -> factor 2.0370 in [2.025, 2.045])

        java.util.List<DeclaracionSintetica> declaraciones = java.util.List.of(
            // Húmedo: 1.070.000 kg
            new DeclaracionSintetica(1L, 9L, 1L, new BigDecimal("600000.00")),
            new DeclaracionSintetica(2L, 9L, 1L, new BigDecimal("470000.00")),
            // Semihúmedo: 300.000 kg
            new DeclaracionSintetica(3L, 9L, 2L, new BigDecimal("180000.00")),
            new DeclaracionSintetica(4L, 9L, 2L, new BigDecimal("120000.00")),
            // Semiseco: 430.000 kg
            new DeclaracionSintetica(5L, 9L, 3L, new BigDecimal("250000.00")),
            new DeclaracionSintetica(6L, 9L, 3L, new BigDecimal("180000.00")),
            // Seco: 500.000 kg
            new DeclaracionSintetica(7L, 9L, 4L, new BigDecimal("320000.00")),
            new DeclaracionSintetica(8L, 9L, 4L, new BigDecimal("180000.00"))
        );

        FactorConversionModel fcHumedo = new FactorConversionModel();
        fcHumedo.setId(1L);
        fcHumedo.setFactor(new BigDecimal("1.1300"));

        FactorConversionModel fcSemiHumedo = new FactorConversionModel();
        fcSemiHumedo.setId(2L);
        fcSemiHumedo.setFactor(new BigDecimal("1.7500"));

        FactorConversionModel fcSemiSeco = new FactorConversionModel();
        fcSemiSeco.setId(3L);
        fcSemiSeco.setFactor(new BigDecimal("2.7000"));

        FactorConversionModel fcSeco = new FactorConversionModel();
        fcSeco.setId(4L);
        fcSeco.setFactor(new BigDecimal("3.5800"));

        when(factorConversionService.findFactorVigente(eq(9L), eq(1L), any(Date.class)))
                .thenReturn(Optional.of(fcHumedo));
        when(factorConversionService.findFactorVigente(eq(9L), eq(2L), any(Date.class)))
                .thenReturn(Optional.of(fcSemiHumedo));
        when(factorConversionService.findFactorVigente(eq(9L), eq(3L), any(Date.class)))
                .thenReturn(Optional.of(fcSemiSeco));
        when(factorConversionService.findFactorVigente(eq(9L), eq(4L), any(Date.class)))
                .thenReturn(Optional.of(fcSeco));

        Date fecha = new Date();
        BigDecimal totalDesembarque = BigDecimal.ZERO;
        BigDecimal totalCaptura = BigDecimal.ZERO;

        for (DeclaracionSintetica d : declaraciones) {
            CalculoCapturaResult res = capturaService.calcular(d.especieId(), d.humedadEstadoId(), fecha, d.desembarqueKg());

            assertTrue(res.isExitoso(), "El cálculo debe ser exitoso para la declaración " + d.id());
            assertNotNull(res.getCaptura(), "Captura no debe ser nula");
            assertNotNull(res.getFactorAplicado(), "Factor aplicado no debe ser nulo");

            // Invariante estricta: captura >= desembarque en CADA fila individual
            assertTrue(res.getCaptura().compareTo(d.desembarqueKg()) >= 0,
                    String.format("Violación de invariante en fila %d: captura (%s) < desembarque (%s)",
                            d.id(), res.getCaptura(), d.desembarqueKg()));

            totalDesembarque = totalDesembarque.add(d.desembarqueKg());
            totalCaptura = totalCaptura.add(res.getCaptura());
        }

        // Verificación de totales agregados
        assertEquals(new BigDecimal("2300000.00"), totalDesembarque, "Total desembarque debe ser 2.300 t (2.300.000 kg)");
        assertEquals(new BigDecimal("4685100.00"), totalCaptura, "Total captura debe ser ~4.685 t (4.685.100 kg)");

        BigDecimal factorGlobal = totalCaptura.divide(totalDesembarque, 4, java.math.RoundingMode.HALF_UP);

        // Criterio T2.3: factor dentro del rango esperado [2.025, 2.045] (tolerancia +-0.01 respecto a 2.035)
        assertTrue(factorGlobal.compareTo(new BigDecimal("2.0250")) >= 0, "Factor global debe ser >= 2.0250");
        assertTrue(factorGlobal.compareTo(new BigDecimal("2.0450")) <= 0, "Factor global debe ser <= 2.0450");
        assertTrue(Math.abs(factorGlobal.doubleValue() - 2.0350) <= 0.0100, "Diferencia con 2.0350 debe ser <= 0.0100");
    }
}
