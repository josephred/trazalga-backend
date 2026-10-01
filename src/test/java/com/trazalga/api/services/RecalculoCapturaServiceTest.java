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

    @Test
    @org.junit.jupiter.api.DisplayName("Recálculo masivo histórico reproduce sesión 25-sep: 2.300t -> ~4.685t (factor global ~2.035 in [2.025, 2.045])")
    void testRecalculoHistoricoVolumenCompleto2300Toneladas() {
        EspecieModel sp = EspecieModel.builder().id(9L).nombre("Huiro Negro").build();
        HumedadEstadoModel stHumedo = HumedadEstadoModel.builder().id(1L).nombre("Húmedo").build();
        HumedadEstadoModel stSemiHumedo = HumedadEstadoModel.builder().id(2L).nombre("Semihúmedo").build();
        HumedadEstadoModel stSemiSeco = HumedadEstadoModel.builder().id(3L).nombre("Semiseco").build();
        HumedadEstadoModel stSeco = HumedadEstadoModel.builder().id(4L).nombre("Seco").build();

        FactorConversionModel fcHumedo = FactorConversionModel.builder().id(1L).factor(new BigDecimal("1.1300")).build();
        FactorConversionModel fcSemiHumedo = FactorConversionModel.builder().id(2L).factor(new BigDecimal("1.7500")).build();
        FactorConversionModel fcSemiSeco = FactorConversionModel.builder().id(3L).factor(new BigDecimal("2.7000")).build();
        FactorConversionModel fcSeco = FactorConversionModel.builder().id(4L).factor(new BigDecimal("3.5800")).build();

        when(factorConversionService.findFactorVigente(eq(9L), eq(1L), any(Date.class))).thenReturn(Optional.of(fcHumedo));
        when(factorConversionService.findFactorVigente(eq(9L), eq(2L), any(Date.class))).thenReturn(Optional.of(fcSemiHumedo));
        when(factorConversionService.findFactorVigente(eq(9L), eq(3L), any(Date.class))).thenReturn(Optional.of(fcSemiSeco));
        when(factorConversionService.findFactorVigente(eq(9L), eq(4L), any(Date.class))).thenReturn(Optional.of(fcSeco));

        // Recolectores: 1.070.000 kg húmedo + 300.000 kg semihúmedo = 1.370.000 kg
        DeclaracionRecolectorModel dr1 = DeclaracionRecolectorModel.builder()
                .id(101L).folioOrigen("REC-01").especie(sp).humedadEstado(stHumedo).fechaDeclaracion(new Date())
                .desembarque(new BigDecimal("1070000.00")).captura(new BigDecimal("1070000.00")).build();
        DeclaracionRecolectorModel dr2 = DeclaracionRecolectorModel.builder()
                .id(102L).folioOrigen("REC-02").especie(sp).humedadEstado(stSemiHumedo).fechaDeclaracion(new Date())
                .desembarque(new BigDecimal("300000.00")).captura(new BigDecimal("300000.00")).build();

        // Armadores: 430.000 kg semiseco
        DeclaracionArmadorModel da1 = DeclaracionArmadorModel.builder()
                .id(201L).folioOrigen("ARM-01").especie(sp).humedadEstado(stSemiSeco).fechaDeclaracion(new Date())
                .desembarque(new BigDecimal("430000.00")).captura(430000.0).build();

        // Áreas de manejo: 500.000 kg seco
        DeclaracionAreaModel dar1 = DeclaracionAreaModel.builder()
                .id(301L).folioOrigen("AMERB-01").especie(sp).humedadEstado(stSeco).fechaDeclaracion(new Date())
                .desembarque(500000.0).captura(500000.0).build();

        when(declaracionRecolectorRepository.findAll()).thenReturn(List.of(dr1, dr2));
        when(declaracionArmadorRepository.findAll()).thenReturn(List.of(da1));
        when(declaracionAreaRepository.findAll()).thenReturn(List.of(dar1));

        RecalculoResumenDTO resumen = recalculoCapturaService.recalcularHistorico(false);

        assertEquals(4, resumen.getTotalProcesadas());
        assertEquals(4, resumen.getTotalActualizadas());
        assertEquals(0, resumen.getTotalOmitidas());
        assertEquals(new BigDecimal("2300000.00"), resumen.getTotalDesembarqueKg());
        assertEquals(new BigDecimal("4685100.00"), resumen.getTotalCapturaNuevaKg());

        BigDecimal factorPonderado = resumen.getTotalCapturaNuevaKg().divide(resumen.getTotalDesembarqueKg(), 4, java.math.RoundingMode.HALF_UP);
        assertTrue(factorPonderado.compareTo(new BigDecimal("2.0250")) >= 0, "Factor ponderado debe ser >= 2.0250");
        assertTrue(factorPonderado.compareTo(new BigDecimal("2.0450")) <= 0, "Factor ponderado debe ser <= 2.0450");
        assertTrue(Math.abs(factorPonderado.doubleValue() - 2.0350) <= 0.0100, "Diferencia con 2.0350 debe ser <= 0.0100");

        // Invariante fila por fila
        assertTrue(dr1.getCaptura().compareTo(dr1.getDesembarque()) >= 0, "Fila dr1: captura >= desembarque");
        assertTrue(dr2.getCaptura().compareTo(dr2.getDesembarque()) >= 0, "Fila dr2: captura >= desembarque");
        assertTrue(BigDecimal.valueOf(da1.getCaptura()).compareTo(da1.getDesembarque()) >= 0, "Fila da1: captura >= desembarque");
        assertTrue(dar1.getCaptura() >= dar1.getDesembarque(), "Fila dar1: captura >= desembarque");

        verify(declaracionRecolectorRepository, times(1)).save(dr1);
        verify(declaracionRecolectorRepository, times(1)).save(dr2);
        verify(declaracionArmadorRepository, times(1)).save(da1);
        verify(declaracionAreaRepository, times(1)).save(dar1);
    }
}
