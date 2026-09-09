package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.repositories.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class ReportServiceDesembarqueFisicoTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)).thenReturn(5000.0);
        lenient().when(configService.getBoolean("desembarque_fuente_recolector_activa", true)).thenReturn(true);
        lenient().when(configService.getBoolean("desembarque_fuente_armador_activa", true)).thenReturn(true);
        lenient().when(configService.getBoolean("desembarque_fuente_area_activa", true)).thenReturn(true);
    }

    @Test
    void testGetDesembarqueFisicoMetrics_ParametrosCompletos_DelegaEnRepositorio() {
        Date inicio = new Date();
        Date fin = new Date();
        Long especieId = 3L;
        Long comunaId = 12L;
        Long regionId = 2L;
        Long provinciaId = 5L;
        Long caletaId = 34L;
        Long usuarioId = 88L;
        Long macrozonaId = 1L;
        String agruparPor = "CALETA";
        String perfil = "RECOLECTOR";

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("totalDesembarqueKg", 15420.5);
        mockResult.put("totalDeclaraciones", 24L);
        mockResult.put("agrupadoPor", "CALETA");
        mockResult.put("datosAgrupados", Collections.emptyList());

        when(reportRepository.getDesembarqueFisicoMetrics(
                inicio, fin, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, agruparPor, perfil,
                5000.0, true, true, true
        )).thenReturn(mockResult);

        Map<String, Object> result = reportService.getDesembarqueFisicoMetrics(
                inicio, fin, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, agruparPor, perfil
        );

        assertNotNull(result);
        assertEquals(15420.5, result.get("totalDesembarqueKg"));
        assertEquals(24L, result.get("totalDeclaraciones"));
        assertEquals("CALETA", result.get("agrupadoPor"));
        verify(reportRepository, times(1)).getDesembarqueFisicoMetrics(
                inicio, fin, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, agruparPor, perfil,
                5000.0, true, true, true
        );
    }

    @Test
    void testGetDesembarqueFisicoMetrics_SobrecargaRetrocompatible_NormalizaValoresNulosYAgrupacionDefault() {
        Date inicio = new Date();
        Date fin = new Date();
        Long especieId = 3L;
        Long comunaId = 12L;
        Long regionId = 2L;
        String perfil = "ARMADOR";

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("totalDesembarqueKg", 8500.0);
        mockResult.put("agrupadoPor", "ESPECIE");

        when(reportRepository.getDesembarqueFisicoMetrics(
                inicio, fin, especieId, comunaId, regionId,
                null, null, null, null, "ESPECIE", perfil,
                5000.0, true, true, true
        )).thenReturn(mockResult);

        Map<String, Object> result = reportService.getDesembarqueFisicoMetrics(
                inicio, fin, especieId, comunaId, regionId, perfil
        );

        assertNotNull(result);
        assertEquals(8500.0, result.get("totalDesembarqueKg"));
        assertEquals("ESPECIE", result.get("agrupadoPor"));
        verify(reportRepository, times(1)).getDesembarqueFisicoMetrics(
                inicio, fin, especieId, comunaId, regionId,
                null, null, null, null, "ESPECIE", perfil,
                5000.0, true, true, true
        );
    }

    @Test
    void testGetDesembarqueFisicoDetalle_ParametrosCompletos_DelegaEnRepositorio() {
        Date inicio = new Date();
        Date fin = new Date();
        Long especieId = 7L;
        Long comunaId = 3L;
        Long regionId = 1L;
        Long provinciaId = 2L;
        Long caletaId = 4L;
        Long usuarioId = 5L;
        Long macrozonaId = 6L;
        String perfil = "RECOLECTOR";

        when(reportRepository.getDesembarqueFisicoDetalle(
                inicio, fin, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, perfil,
                5000.0, true, true, true
        )).thenReturn(Collections.emptyList());

        List<Map<String, Object>> detalle = reportService.getDesembarqueFisicoDetalle(
                inicio, fin, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, perfil
        );

        assertNotNull(detalle);
        assertTrue(detalle.isEmpty());
        verify(reportRepository, times(1)).getDesembarqueFisicoDetalle(
                inicio, fin, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, perfil,
                5000.0, true, true, true
        );
    }

    @Test
    void testGetDesembarqueFisicoDetalle_SobrecargaRetrocompatible_DelegaConNulos() {
        Date inicio = new Date();
        Date fin = new Date();
        Long especieId = 7L;
        Long comunaId = 3L;
        Long regionId = 1L;
        String perfil = "RECOLECTOR";

        when(reportRepository.getDesembarqueFisicoDetalle(
                inicio, fin, especieId, comunaId, regionId,
                null, null, null, null, perfil,
                5000.0, true, true, true
        )).thenReturn(Collections.emptyList());

        List<Map<String, Object>> detalle = reportService.getDesembarqueFisicoDetalle(
                inicio, fin, especieId, comunaId, regionId, perfil
        );

        assertNotNull(detalle);
        assertTrue(detalle.isEmpty());
        verify(reportRepository, times(1)).getDesembarqueFisicoDetalle(
                inicio, fin, especieId, comunaId, regionId,
                null, null, null, null, perfil,
                5000.0, true, true, true
        );
    }
}
