package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.RecalculoResumenDTO;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

@ExtendWith(MockitoExtension.class)
public class RecalculoCapturaServiceTest {

    @Mock
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Mock
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

    @Mock
    private IDeclaracionAreaRepository declaracionAreaRepository;

    @Mock
    private FactorConversionService factorConversionService;

    @Mock
    private ConfiguracionAuditoriaService configuracionAuditoriaService;

    @InjectMocks
    private RecalculoCapturaService recalculoCapturaService;

    private EspecieModel huiroNegro;
    private HumedadEstadoModel estadoSeco;
    private FactorConversionModel factorSeco;

    @BeforeEach
    void setUp() {
        huiroNegro = EspecieModel.builder().id(9L).nombre("Huiro Negro").build();
        estadoSeco = HumedadEstadoModel.builder().id(4L).nombre("Seco").build();
        factorSeco = FactorConversionModel.builder()
                .id(18L)
                .factor(new BigDecimal("3.5800"))
                .build();
    }

    @Test
    void testRecalculoModoSimulacionNoPersiste() {
        DeclaracionRecolectorModel dr = DeclaracionRecolectorModel.builder()
                .id(100L)
                .folioOrigen("RO-001")
                .especie(huiroNegro)
                .humedadEstado(estadoSeco)
                .fechaExtraccion(new Date())
                .desembarque(new BigDecimal("1000.00"))
                .captura(new BigDecimal("1000.00")) // Valor viejo sin factor aplicado
                .factorAplicado(null)
                .build();

        when(declaracionRecolectorRepository.findAll()).thenReturn(List.of(dr));
        when(declaracionArmadorRepository.findAll()).thenReturn(List.of());
        when(declaracionAreaRepository.findAll()).thenReturn(List.of());
        when(factorConversionService.findFactorVigente(eq(9L), eq(4L), any(Date.class)))
                .thenReturn(Optional.of(factorSeco));

        RecalculoResumenDTO resumen = recalculoCapturaService.recalcularHistorico(true);

        assertTrue(resumen.isDryRun());
        assertEquals(1, resumen.getTotalProcesadas());
        assertEquals(1, resumen.getTotalActualizadas());
        assertEquals(0, resumen.getTotalOmitidas());
        assertEquals(new BigDecimal("1000.00"), resumen.getTotalDesembarqueKg());
        assertEquals(new BigDecimal("1000.00"), resumen.getTotalCapturaAnteriorKg());
        assertEquals(new BigDecimal("3580.00"), resumen.getTotalCapturaNuevaKg());
        assertEquals(new BigDecimal("2580.00"), resumen.getVariacionTotalKg());

        // En dryRun no debe guardarse en repositorio ni en auditoría
        verify(declaracionRecolectorRepository, never()).save(any());
        verify(configuracionAuditoriaService, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testRecalculoPersisteYAudita() {
        DeclaracionRecolectorModel dr = DeclaracionRecolectorModel.builder()
                .id(100L)
                .folioOrigen("RO-001")
                .especie(huiroNegro)
                .humedadEstado(estadoSeco)
                .fechaExtraccion(new Date())
                .desembarque(new BigDecimal("1000.00"))
                .captura(new BigDecimal("1000.00"))
                .factorAplicado(null)
                .build();

        when(declaracionRecolectorRepository.findAll()).thenReturn(List.of(dr));
        when(declaracionArmadorRepository.findAll()).thenReturn(List.of());
        when(declaracionAreaRepository.findAll()).thenReturn(List.of());
        when(factorConversionService.findFactorVigente(eq(9L), eq(4L), any(Date.class)))
                .thenReturn(Optional.of(factorSeco));

        RecalculoResumenDTO resumen = recalculoCapturaService.recalcularHistorico(false);

        assertFalse(resumen.isDryRun());
        assertEquals(1, resumen.getTotalActualizadas());

        verify(declaracionRecolectorRepository, times(1)).save(dr);
        assertEquals(new BigDecimal("3580.00"), dr.getCaptura());
        assertEquals(new BigDecimal("3.5800"), dr.getFactorAplicado());
        assertEquals(18L, dr.getFactorConversionId());

        verify(configuracionAuditoriaService, times(1)).registrar(
                eq("DECLARACION_RECOLECTOR"),
                eq("100"),
                eq("captura_biologica"),
                anyString(),
                anyString(),
                isNull());
    }

    @Test
    void testDeclaracionIncompletaPasaARegularizacion() {
        DeclaracionRecolectorModel drIncompleta = DeclaracionRecolectorModel.builder()
                .id(200L)
                .folioOrigen("RO-INC")
                .especie(null) // Sin especie
                .humedadEstado(estadoSeco)
                .desembarque(new BigDecimal("500.00"))
                .build();

        when(declaracionRecolectorRepository.findAll()).thenReturn(List.of(drIncompleta));
        when(declaracionArmadorRepository.findAll()).thenReturn(List.of());
        when(declaracionAreaRepository.findAll()).thenReturn(List.of());

        RecalculoResumenDTO resumen = recalculoCapturaService.recalcularHistorico(true);

        assertEquals(1, resumen.getTotalProcesadas());
        assertEquals(0, resumen.getTotalActualizadas());
        assertEquals(1, resumen.getTotalOmitidas());
        assertEquals(1, resumen.getRegularizaciones().size());
        assertEquals("RO-INC", resumen.getRegularizaciones().get(0).getFolio());
        assertTrue(resumen.getRegularizaciones().get(0).getMotivo().contains("Falta especie"));
    }
}
