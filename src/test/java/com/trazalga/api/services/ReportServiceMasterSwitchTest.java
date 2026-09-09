package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.repositories.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class ReportServiceMasterSwitchTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private ReportService reportService;

    @Test
    void testRetencionBodega_Desactivada_RetornaEstadoInactivoSinFilas() {
        when(configService.getBoolean("retencion_bodega_activo", true)).thenReturn(false);
        when(configService.getInt("retencion_bodega_dias_amarilla", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_naranja", 5)).thenReturn(5);
        when(configService.getInt("retencion_bodega_dias_roja", 7)).thenReturn(7);
        when(configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO")).thenReturn("HUMEDO");

        Map<String, Object> result = reportService.getRetencionBodegaMetrics();

        assertNotNull(result);
        assertEquals(false, result.get("activo"));
        assertEquals(true, result.get("controlDesactivado"));
        assertEquals(0, result.get("totalLotesEnBodega"));
        assertEquals(Collections.emptyList(), result.get("lotes"));
        verify(reportRepository, never()).getRetencionBodegaMetrics(anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    void testRetencionBodega_Activada_DelegaEnRepositorio() {
        when(configService.getBoolean("retencion_bodega_activo", true)).thenReturn(true);
        when(configService.getInt("retencion_bodega_dias_amarilla", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_naranja", 5)).thenReturn(5);
        when(configService.getInt("retencion_bodega_dias_roja", 7)).thenReturn(7);
        when(configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO")).thenReturn("HUMEDO");

        Map<String, Object> mockRepoResult = new HashMap<>();
        mockRepoResult.put("totalLotesEnBodega", 5);
        mockRepoResult.put("totalKgEnBodega", 12500.0);
        when(reportRepository.getRetencionBodegaMetrics(3, 5, 7, "HUMEDO")).thenReturn(mockRepoResult);

        Map<String, Object> result = reportService.getRetencionBodegaMetrics();

        assertNotNull(result);
        assertEquals(true, result.get("activo"));
        assertEquals(false, result.get("controlDesactivado"));
        assertEquals(5, result.get("totalLotesEnBodega"));
        verify(reportRepository, times(1)).getRetencionBodegaMetrics(3, 5, 7, "HUMEDO");
    }

    @Test
    void testBioPerdida_Desactivada_PropagaFlagAlRepositorio() {
        when(configService.getBoolean("bio_perdida_activo", true)).thenReturn(false);
        when(configService.getDouble("variacion_peso_umbral_general_pct", 5.0)).thenReturn(5.0);
        when(configService.getDouble("bio_humedo_merma_minima_pct", 5.0)).thenReturn(5.0);
        when(configService.getDouble("bio_seco_merma_maxima_pct", 3.0)).thenReturn(3.0);
        when(configService.getInt("bio_humedo_dias_minimos_transito", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_amarilla", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_naranja", 5)).thenReturn(5);
        when(configService.getInt("retencion_bodega_dias_roja", 7)).thenReturn(7);
        when(configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO")).thenReturn("HUMEDO");

        Map<String, Object> mockRepoResult = new HashMap<>();
        mockRepoResult.put("totalLotes", 10L);
        mockRepoResult.put("alertasMerma", 0L);
        when(reportRepository.getTrazabilidadLoteMetrics(any(), any(), anyDouble(), anyDouble(), anyDouble(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyString(), eq(false))).thenReturn(mockRepoResult);

        Map<String, Object> result = reportService.getTrazabilidadLoteMetrics(new Date(), new Date(), null);

        assertNotNull(result);
        assertEquals(false, result.get("bioPerdidaActivo"));
        verify(reportRepository, times(1)).getTrazabilidadLoteMetrics(any(), any(), anyDouble(), anyDouble(), anyDouble(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyString(), eq(false));
    }
}
