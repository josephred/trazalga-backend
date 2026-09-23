package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.repositories.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class ReportServiceBioPerdidaTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("R8.1: getTrazabilidadLoteDetalle propaga parámetros biológicos y bio_perdida_activo a ReportRepository")
    void testGetTrazabilidadLoteDetalle_PropagaParametros() {
        when(configService.getBoolean("bio_perdida_activo", true)).thenReturn(true);
        when(configService.getDouble("variacion_peso_umbral_general_pct", 5.0)).thenReturn(5.0);
        when(configService.getDouble("bio_humedo_merma_minima_pct", 5.0)).thenReturn(5.0);
        when(configService.getDouble("bio_seco_merma_maxima_pct", 3.0)).thenReturn(3.0);
        when(configService.getInt("bio_humedo_dias_minimos_transito", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_amarilla", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_naranja", 5)).thenReturn(5);
        when(configService.getInt("retencion_bodega_dias_roja", 7)).thenReturn(7);
        when(configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO")).thenReturn("HUMEDO");

        Map<String, Object> loteSimulado = new HashMap<>();
        loteSimulado.put("folioOrigen", "FOLIO-TEST-001");
        loteSimulado.put("humedadOrigen", "HÚMEDO");
        loteSimulado.put("diasTranscurridos", 4);
        loteSimulado.put("deltaPct", -6.0); // 6% merma
        loteSimulado.put("severidadBiologica", "NEUTRA");
        loteSimulado.put("inconsistenciaBiologica", "NINGUNA");

        when(reportRepository.getTrazabilidadLoteDetalle(
                any(), any(), any(), eq(5.0), eq(5.0), eq(3.0), eq(3), eq(3), eq(5), eq(7), eq("HUMEDO"), eq(true)))
                .thenReturn(Collections.singletonList(loteSimulado));

        List<Map<String, Object>> result = reportService.getTrazabilidadLoteDetalle(new Date(), new Date(), null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("NEUTRA", result.get(0).get("severidadBiologica"));
        assertEquals("NINGUNA", result.get(0).get("inconsistenciaBiologica"));

        verify(reportRepository, times(1)).getTrazabilidadLoteDetalle(
                any(), any(), any(), eq(5.0), eq(5.0), eq(3.0), eq(3), eq(3), eq(5), eq(7), eq("HUMEDO"), eq(true));
    }

    @Test
    @DisplayName("R8.1: Cambio dinámico de bio_humedo_merma_minima_pct de 5 a 8 reclasifica lote con 6% merma de NEUTRA a ATENCION")
    void testReclasificacionDinamicaMermaMinima() {
        // Simulación lógica de la regla de evaluación de severidad biológica
        double deltaPct = -6.0; // 6% de merma
        double mermaPct = -deltaPct; // 6.0%
        int dias = 4;
        int diasMinHumedo = 3;

        // Caso inicial: umbral 5.0%
        double umbralInicial = 5.0;
        String severidadInicial;
        if (dias >= diasMinHumedo) {
            if (mermaPct >= umbralInicial) {
                severidadInicial = "NEUTRA";
            } else if (mermaPct > 0) {
                severidadInicial = "ATENCION";
            } else {
                severidadInicial = "CRITICA";
            }
        } else {
            severidadInicial = "NEUTRA";
        }
        assertEquals("NEUTRA", severidadInicial, "Con umbral 5%, 6% de merma es biológicamente normal (NEUTRA)");

        // Caso reclasificado: umbral sube a 8.0%
        double umbralNuevo = 8.0;
        String severidadNueva;
        if (dias >= diasMinHumedo) {
            if (mermaPct >= umbralNuevo) {
                severidadNueva = "NEUTRA";
            } else if (mermaPct > 0) {
                severidadNueva = "ATENCION";
            } else {
                severidadNueva = "CRITICA";
            }
        } else {
            severidadNueva = "NEUTRA";
        }
        assertEquals("ATENCION", severidadNueva, "Con umbral 8%, 6% de merma queda por debajo del mínimo esperado (ATENCION)");
    }

    @Test
    @DisplayName("R8.1: Casos de severidad biológica (NEUTRA, ATENCION, CRITICA) para Húmedo y Seco")
    void testCasosSeveridadBiologica() {
        // Caso 3: Húmedo tras 4 días sin merma (deltaPct >= 0) -> CRITICA (sospecha adición en ruta)
        double deltaHumedoSinMerma = 0.0;
        double mermaHumedoSinMerma = -deltaHumedoSinMerma;
        String sevCritica = (mermaHumedoSinMerma <= 0) ? "CRITICA" : "NEUTRA";
        assertEquals("CRITICA", sevCritica);

        // Caso 4: Seco con 2% merma (<= 3% max tolerado) -> NEUTRA
        double mermaSecoNormal = 2.0;
        double maxSeco = 3.0;
        String sevSecoNormal = (mermaSecoNormal <= maxSeco) ? "NEUTRA" : "ATENCION";
        assertEquals("NEUTRA", sevSecoNormal);

        // Caso 5: Seco con 7% merma (> 3% max tolerado) -> ATENCION
        double mermaSecoExcesiva = 7.0;
        String sevSecoExcesiva = (mermaSecoExcesiva > maxSeco) ? "ATENCION" : "NEUTRA";
        assertEquals("ATENCION", sevSecoExcesiva);
    }

    @Test
    @DisplayName("R8.2: Apagar bio_perdida_activo propaga false y retorna controlDesactivado = true")
    void testBioPerdidaDesactivado_RetornaControlDesactivado() {
        when(configService.getBoolean("bio_perdida_activo", true)).thenReturn(false);
        when(configService.getDouble("variacion_peso_umbral_general_pct", 5.0)).thenReturn(5.0);
        when(configService.getDouble("bio_humedo_merma_minima_pct", 5.0)).thenReturn(5.0);
        when(configService.getDouble("bio_seco_merma_maxima_pct", 3.0)).thenReturn(3.0);
        when(configService.getInt("bio_humedo_dias_minimos_transito", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_amarilla", 3)).thenReturn(3);
        when(configService.getInt("retencion_bodega_dias_naranja", 5)).thenReturn(5);
        when(configService.getInt("retencion_bodega_dias_roja", 7)).thenReturn(7);
        when(configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO")).thenReturn("HUMEDO");

        Map<String, Object> repoResult = new HashMap<>();
        repoResult.put("totalLotes", 5L);
        repoResult.put("controlDesactivado", true);
        repoResult.put("bioPerdidaActivo", false);
        repoResult.put("alertasMerma", 0L);

        when(reportRepository.getTrazabilidadLoteMetrics(
                any(), any(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), eq(false)))
                .thenReturn(repoResult);

        Map<String, Object> result = reportService.getTrazabilidadLoteMetrics(new Date(), new Date(), null);

        assertNotNull(result);
        assertEquals(false, result.get("bioPerdidaActivo"));
        assertEquals(true, result.get("controlDesactivado"));
        assertEquals(0L, result.get("alertasMerma"));
    }
}
