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
}
