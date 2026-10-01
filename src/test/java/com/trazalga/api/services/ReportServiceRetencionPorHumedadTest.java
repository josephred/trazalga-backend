package com.trazalga.api.services;

import com.trazalga.api.repositories.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceRetencionPorHumedadTest {

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getBoolean("retencion_bodega_activo", true)).thenReturn(true);
        lenient().when(configService.getInt("retencion_humedo_max_horas", 24)).thenReturn(24);
        lenient().when(configService.getInt("retencion_semihumedo_max_horas", 72)).thenReturn(72);
        lenient().when(configService.getInt("retencion_semiseco_max_horas", 216)).thenReturn(216);
        lenient().when(configService.getInt("retencion_preaviso_pct", 80)).thenReturn(80);
    }

    @Test
    void testMatrizYClasificacionPorHumedad_Res3602() {
        Map<String, Object> mockRepoResult = new LinkedHashMap<>();
        mockRepoResult.put("totalLotes", 10);
        mockRepoResult.put("totalLotesEnBodega", 6);
        mockRepoResult.put("totalKgEnBodega", 15000.0);
        mockRepoResult.put("semaforoVerde", 4L);
        mockRepoResult.put("semaforoAmarillo", 3L);
        mockRepoResult.put("semaforoRojo", 3L);

        Map<String, Map<String, Object>> matriz = new LinkedHashMap<>();
        matriz.put("HUMEDO", Map.of("VERDE", 1L, "AMARILLO", 1L, "ROJO", 1L, "totalLotes", 3L, "totalKg", 4500.0));
        matriz.put("SEMI_HUMEDO", Map.of("VERDE", 1L, "AMARILLO", 1L, "ROJO", 1L, "totalLotes", 3L, "totalKg", 4000.0));
        matriz.put("SEMI_SECO", Map.of("VERDE", 1L, "AMARILLO", 1L, "ROJO", 1L, "totalLotes", 3L, "totalKg", 3500.0));
        matriz.put("SECO", Map.of("VERDE", 1L, "AMARILLO", 0L, "ROJO", 0L, "totalLotes", 1L, "totalKg", 3000.0));
        mockRepoResult.put("matriz", matriz);

        List<Map<String, Object>> lotes = new ArrayList<>();
        lotes.add(Map.of("folio", "DC-01", "estadoDeclarado", "HÚMEDO", "tramoReal", "HÚMEDO", "horasTranscurridas", 18L, "plazoMaxHoras", 24, "semaforo", "VERDE"));
        lotes.add(Map.of("folio", "DC-02", "estadoDeclarado", "HÚMEDO", "tramoReal", "HÚMEDO", "horasTranscurridas", 22L, "plazoMaxHoras", 24, "semaforo", "AMARILLO"));
        lotes.add(Map.of("folio", "DC-03", "estadoDeclarado", "HÚMEDO", "tramoReal", "SEMI HÚMEDO", "horasTranscurridas", 30L, "plazoMaxHoras", 24, "semaforo", "ROJO"));
        lotes.add(Map.of("folio", "DC-04", "estadoDeclarado", "SEMI HÚMEDO", "tramoReal", "SEMI HÚMEDO", "horasTranscurridas", 50L, "plazoMaxHoras", 72, "semaforo", "VERDE"));
        lotes.add(Map.of("folio", "DC-05", "estadoDeclarado", "SEMI HÚMEDO", "tramoReal", "SEMI HÚMEDO", "horasTranscurridas", 60L, "plazoMaxHoras", 72, "semaforo", "AMARILLO"));
        lotes.add(Map.of("folio", "DC-06", "estadoDeclarado", "SEMI HÚMEDO", "tramoReal", "SEMI SECO", "horasTranscurridas", 80L, "plazoMaxHoras", 72, "semaforo", "ROJO"));
        lotes.add(Map.of("folio", "DC-07", "estadoDeclarado", "SEMI SECO", "tramoReal", "SEMI SECO", "horasTranscurridas", 150L, "plazoMaxHoras", 216, "semaforo", "VERDE"));
        lotes.add(Map.of("folio", "DC-08", "estadoDeclarado", "SEMI SECO", "tramoReal", "SEMI SECO", "horasTranscurridas", 192L, "plazoMaxHoras", 216, "semaforo", "AMARILLO"));
        lotes.add(Map.of("folio", "DC-09", "estadoDeclarado", "SEMI SECO", "tramoReal", "SECO", "horasTranscurridas", 240L, "plazoMaxHoras", 216, "semaforo", "ROJO"));
        lotes.add(Map.of("folio", "DC-10", "estadoDeclarado", "SECO", "tramoReal", "SECO", "horasTranscurridas", 360L, "semaforo", "VERDE"));

        mockRepoResult.put("lotes", lotes);

        when(reportRepository.getRetencionPorHumedad(null, null, 24, 72, 216, 80)).thenReturn(mockRepoResult);

        Map<String, Object> result = reportService.getRetencionBodegaMetrics();

        assertNotNull(result);
        assertEquals(true, result.get("activo"));
        assertEquals(false, result.get("controlDesactivado"));
        assertEquals(10, result.get("totalLotes"));
        assertEquals(4L, result.get("semaforoVerde"));
        assertEquals(3L, result.get("semaforoAmarillo"));
        assertEquals(3L, result.get("semaforoRojo"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> resMatriz = (Map<String, Map<String, Object>>) result.get("matriz");
        assertNotNull(resMatriz);
        assertTrue(resMatriz.containsKey("HUMEDO"));
        assertTrue(resMatriz.containsKey("SEMI_HUMEDO"));
        assertTrue(resMatriz.containsKey("SEMI_SECO"));
        assertTrue(resMatriz.containsKey("SECO"));

        // Seco nunca tiene rojo ni amarillo
        assertEquals(0L, resMatriz.get("SECO").get("AMARILLO"));
        assertEquals(0L, resMatriz.get("SECO").get("ROJO"));
    }

    @Test
    void testReconfiguracionParametroHumedoMaxHoras_SinReinicio() {
        // Al elevar plazo de húmedo a 36 h, un lote de 30 h deja de ser rojo
        when(configService.getInt("retencion_humedo_max_horas", 24)).thenReturn(36);

        Map<String, Object> mock36h = new HashMap<>();
        mock36h.put("totalLotes", 1);
        mock36h.put("semaforoVerde", 0L);
        mock36h.put("semaforoAmarillo", 1L);
        mock36h.put("semaforoRojo", 0L);

        when(reportRepository.getRetencionPorHumedad(null, null, 36, 72, 216, 80)).thenReturn(mock36h);

        Map<String, Object> result = reportService.getRetencionBodegaMetrics();

        assertNotNull(result);
        assertEquals(0L, result.get("semaforoRojo"));
        verify(reportRepository).getRetencionPorHumedad(null, null, 36, 72, 216, 80);
    }
}
