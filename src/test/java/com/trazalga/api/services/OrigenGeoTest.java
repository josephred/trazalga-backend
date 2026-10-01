package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class OrigenGeoTest {

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private ICaletaRepository caletaRepository;

    @Mock
    private IAmerbRepository amerbRepository;

    @Mock
    private DeclaracionMarcaService marcaService;

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ValidacionDeclaracionService validacionService;

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getBoolean(eq("origen_geo_activo"), anyBoolean())).thenReturn(true);
        lenient().when(configService.getDouble(eq("origen_geo_distancia_max_km"), anyDouble())).thenReturn(30.0);
        lenient().when(configService.getDouble(eq("origen_geo_precision_max_m"), anyDouble())).thenReturn(500.0);
        lenient().when(configService.getDouble(eq("origen_geo_patron_pct"), anyDouble())).thenReturn(50.0);
        lenient().when(configService.getInt(eq("origen_geo_patron_min_decl"), anyInt())).thenReturn(3);
    }

    @Test
    void testSantiagoVsCoquimbo_GeneraMarcaOrigenGeoInconsistente() {
        // Coquimbo Caleta
        CaletaModel caleta = CaletaModel.builder()
                .id(10L)
                .nombre("Caleta Coquimbo")
                .latitud(-29.9533)
                .longitud(-71.3395)
                .build();
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caleta));

        DeclaracionMarcaModel mockMarca = DeclaracionMarcaModel.builder()
                .id(1L)
                .marca("ORIGEN_GEO_INCONSISTENTE")
                .detalle("GPS a 390.5 km de Caleta Coquimbo")
                .build();
        when(marcaService.marcar(anyString(), anyLong(), eq("ORIGEN_GEO_INCONSISTENTE"), anyString(), isNull()))
                .thenReturn(mockMarca);

        // GPS en Santiago (-33.4489, -70.6693), precisión 15m
        Optional<DeclaracionMarcaModel> res = validacionService.verificarOrigenGeo(
                "RECOLECTOR", 100L, "DR-100",
                -33.4489, -70.6693, 15.0, false, 10L, null);

        assertTrue(res.isPresent(), "Debe generar marca ORIGEN_GEO_INCONSISTENTE por distancia extrema");
        assertEquals("ORIGEN_GEO_INCONSISTENTE", res.get().getMarca());
        verify(marcaService, times(1)).marcar(
                eq("RECOLECTOR"), eq(100L), eq("ORIGEN_GEO_INCONSISTENTE"),
                contains("Caleta Coquimbo"), isNull());
    }

    @Test
    void testDeclaracionCercana_NoGeneraMarca() {
        CaletaModel caleta = CaletaModel.builder()
                .id(10L)
                .nombre("Caleta Coquimbo")
                .latitud(-29.9533)
                .longitud(-71.3395)
                .build();
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caleta));

        // GPS a 2.5 km de la caleta
        Optional<DeclaracionMarcaModel> res = validacionService.verificarOrigenGeo(
                "RECOLECTOR", 101L, "DR-101",
                -29.9650, -71.3500, 20.0, false, 10L, null);

        assertFalse(res.isPresent(), "No debe generar marca si está dentro de los 30 km");
        verify(marcaService, never()).marcar(any(), any(), any(), any(), any());
    }

    @Test
    void testPrecisionDeficiente_NoGeneraMarca() {
        CaletaModel caleta = CaletaModel.builder()
                .id(10L)
                .nombre("Caleta Coquimbo")
                .latitud(-29.9533)
                .longitud(-71.3395)
                .build();
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caleta));

        // GPS a 60 km pero con precisión mala de 800m (>500m)
        Optional<DeclaracionMarcaModel> res = validacionService.verificarOrigenGeo(
                "RECOLECTOR", 102L, "DR-102",
                -30.5000, -71.3000, 800.0, false, 10L, null);

        assertFalse(res.isPresent(), "No debe generar marca formal si la precisión del GPS es peor a 500 m");
        verify(marcaService, never()).marcar(any(), any(), any(), any(), any());
    }

    @Test
    void testCaletaSinCoordenadas_ModoDegradadoSinReferencia() {
        // Caleta sin coordenadas y sin varadero
        CaletaModel caleta = CaletaModel.builder()
                .id(20L)
                .nombre("Caleta Sin Coordenadas")
                .latitud(null)
                .longitud(null)
                .varadero(null)
                .build();
        when(caletaRepository.findById(20L)).thenReturn(Optional.of(caleta));

        Optional<DeclaracionMarcaModel> res = validacionService.verificarOrigenGeo(
                "RECOLECTOR", 103L, "DR-103",
                -33.4489, -70.6693, 15.0, false, 20L, null);

        assertFalse(res.isPresent(), "No debe generar marca si la caleta no tiene coordenadas");
        verify(marcaService, never()).marcar(any(), any(), any(), any(), any());
    }

    @Test
    void testFallbackVaradero_UsaCoordenadasVaradero() {
        VaraderoModel varadero = VaraderoModel.builder()
                .id(5L)
                .nombre("Varadero Puerto")
                .latitud(-29.9500)
                .longitud(-71.3400)
                .build();
        CaletaModel caleta = CaletaModel.builder()
                .id(30L)
                .nombre("Caleta con Varadero")
                .latitud(null)
                .longitud(null)
                .varadero(varadero)
                .build();
        when(caletaRepository.findById(30L)).thenReturn(Optional.of(caleta));

        when(marcaService.marcar(anyString(), anyLong(), eq("ORIGEN_GEO_INCONSISTENTE"), anyString(), isNull()))
                .thenReturn(DeclaracionMarcaModel.builder().marca("ORIGEN_GEO_INCONSISTENTE").build());

        // GPS a 400 km en Santiago
        Optional<DeclaracionMarcaModel> res = validacionService.verificarOrigenGeo(
                "RECOLECTOR", 104L, "DR-104",
                -33.4489, -70.6693, 10.0, false, 30L, null);

        assertTrue(res.isPresent(), "Debe usar las coordenadas del varadero de respaldo y marcar");
        verify(marcaService, times(1)).marcar(
                anyString(), anyLong(), eq("ORIGEN_GEO_INCONSISTENTE"),
                contains("Varadero Puerto"), isNull());
    }

    @Test
    void testAmerbCentroide_TienePrecedenciaSobreCaletaYMarca() {
        // AMERB en Punta de Choros (-29.2500, -71.4600)
        com.trazalga.api.models.AmerbModel amerb = com.trazalga.api.models.AmerbModel.builder()
                .id(50L)
                .nombre("AMERB Punta de Choros")
                .latitud(-29.2500)
                .longitud(-71.4600)
                .build();
        when(amerbRepository.findById(50L)).thenReturn(Optional.of(amerb));

        when(marcaService.marcar(anyString(), anyLong(), eq("ORIGEN_GEO_INCONSISTENTE"), anyString(), isNull()))
                .thenReturn(DeclaracionMarcaModel.builder().marca("ORIGEN_GEO_INCONSISTENTE").build());

        // GPS en Valparaíso (-33.0472, -71.6127), a ~420 km
        Optional<DeclaracionMarcaModel> res = validacionService.verificarOrigenGeo(
                "AREA", 105L, "DAM-105",
                -33.0472, -71.6127, 25.0, false, 10L, 50L);

        assertTrue(res.isPresent(), "Debe usar centroide de AMERB con precedencia y generar marca");
        verify(marcaService, times(1)).marcar(
                eq("AREA"), eq(105L), eq("ORIGEN_GEO_INCONSISTENTE"),
                contains("AMERB Punta de Choros"), isNull());
    }

    @Test
    void testVistaPatrones_UsuarioSospechosoYMediana() {
        // Simular 5 declaraciones del mismo usuario: 4 lejanas (>30 km) y 1 cercana (2 km)
        List<Map<String, Object>> filas = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> f = new HashMap<>();
            f.put("tipo", "RECOLECTOR");
            f.put("id", (long) i);
            f.put("folio", "DR-" + i);
            f.put("fechaDeclaracion", new Date());
            f.put("hora", "10:00:00");
            f.put("usuarioId", 99L);
            f.put("usuarioRut", "11.222.333-4");
            f.put("usuarioNombre", "Pescador Sospechoso");
            f.put("precisionGpsM", 20.0);
            f.put("envioOffline", false);
            f.put("refTipo", "CALETA");
            f.put("refNombre", "Caleta Coquimbo");
            f.put("refLat", -29.9533);
            f.put("refLon", -71.3395);

            if (i == 1) {
                // Cercana (~2 km)
                f.put("latitud", -29.9600);
                f.put("longitud", -71.3400);
            } else {
                // Lejana en Santiago (~390 km)
                f.put("latitud", -33.4489);
                f.put("longitud", -70.6693);
            }
            filas.add(f);
        }

        when(reportRepository.getDeclaracionesOrigenGeo(any(), any(), any())).thenReturn(filas);

        Map<String, Object> result = reportService.getOrigenGeoPatrones(null, null, null);

        assertNotNull(result);
        assertEquals(5, result.get("totalDeclaracionesEvaluadas"));
        assertEquals(4L, result.get("totalInconsistencias"));
        assertEquals(4L, result.get("totalMarcadas"));
        assertEquals(1, result.get("usuariosPatronSospechoso"));

        List<Map<String, Object>> ranking = (List<Map<String, Object>>) result.get("rankingUsuarios");
        assertEquals(1, ranking.size());

        Map<String, Object> userStat = ranking.get(0);
        assertEquals(99L, userStat.get("usuarioId"));
        assertEquals(5, userStat.get("totalDeclaraciones"));
        assertEquals(4, userStat.get("declaracionesLejos"));
        assertEquals(80.0, (Double) userStat.get("porcentajeLejos"), 0.1);
        assertTrue((Boolean) userStat.get("patronSospechoso"));
        assertTrue(((String) userStat.get("rotuloPatron")).contains("Patrón sospechoso"));
        assertTrue((Double) userStat.get("medianaDistanciaKm") > 100.0);
    }
}
