package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DobleOperacionGeoTest {

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private DeclaracionMarcaService declaracionMarcaService;

    @InjectMocks
    private ReportService reportService;

    @InjectMocks
    private ValidacionDeclaracionService validacionDeclaracionService;

    private SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");

    // Caleta A (Coquimbo): -29.9533, -71.3436
    private static final double LAT_CALETA_A = -29.9533;
    private static final double LON_CALETA_A = -71.3436;

    // Punto B (aprox 60 km al norte): -29.4133, -71.3436 (d ≈ 60.05 km)
    private static final double LAT_PUNTO_60KM = -29.4133;
    private static final double LON_PUNTO_60KM = -71.3436;

    // Punto C (aprox 2 km al sur): -29.9713, -71.3436 (d ≈ 2.0 km)
    private static final double LAT_PUNTO_2KM = -29.9713;
    private static final double LON_PUNTO_2KM = -71.3436;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getBoolean("doble_op_activo", true)).thenReturn(true);
        lenient().when(configService.getDouble("doble_op_distancia_min_km", 5.0)).thenReturn(5.0);
        lenient().when(configService.getDouble("doble_op_velocidad_max_kmh", 80.0)).thenReturn(80.0);
        lenient().when(configService.getInt("doble_op_ventana_min_minutos", 30)).thenReturn(30);
    }

    @Test
    @DisplayName("Caso 1 Aceptación: Usuario declara a 10:00 en Caleta A y a 10:20 a 60 km -> Hallazgo (180 km/h)")
    void testCaso1_HallazgoVelocidadYVentana() throws Exception {
        Date fecha = sdfDate.parse("2026-10-01");

        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("usuarioId", 101L);
        d1.put("fechaDeclaracion", fecha);
        d1.put("hora", "10:00:00");
        d1.put("latitud", LAT_CALETA_A);
        d1.put("longitud", LON_CALETA_A);
        d1.put("tipo", "RECOLECTOR");
        d1.put("id", 1L);
        d1.put("folio", "DR-1001");
        d1.put("usuarioNombre", "Juan Pérez");
        d1.put("usuarioRut", "11.111.111-1");

        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("usuarioId", 101L);
        d2.put("fechaDeclaracion", fecha);
        d2.put("hora", "10:20:00");
        d2.put("latitud", LAT_PUNTO_60KM);
        d2.put("longitud", LON_PUNTO_60KM);
        d2.put("tipo", "ARMADOR");
        d2.put("id", 2L);
        d2.put("folio", "DA-2002");
        d2.put("usuarioNombre", "Juan Pérez");
        d2.put("usuarioRut", "11.111.111-1");

        when(reportRepository.getDeclaracionesConGeo(any(), any(), any())).thenReturn(List.of(d1, d2));
        when(reportRepository.contarDeclaracionesSinGeo(any(), any(), any())).thenReturn(0L);

        Map<String, Object> res = reportService.getDobleOperacionGeo(fecha, fecha, null);

        assertTrue((Boolean) res.get("activo"));
        assertEquals(1, ((Number) res.get("totalHallazgos")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hallazgos = (List<Map<String, Object>>) res.get("hallazgos");
        assertFalse(hallazgos.isEmpty());

        Map<String, Object> h = hallazgos.get(0);
        assertEquals(101L, h.get("usuarioId"));
        double dist = ((Number) h.get("distanciaKm")).doubleValue();
        double vel = ((Number) h.get("velocidadKmh")).doubleValue();
        long minutos = ((Number) h.get("tiempoMinutos")).longValue();

        assertTrue(dist >= 55.0 && dist <= 65.0, "La distancia debe rondar los 60 km (real: " + dist + ")");
        assertEquals(20, minutos, "El tiempo transcurrido debe ser 20 min");
        assertTrue(vel >= 170.0 && vel <= 190.0, "La velocidad debe ser ~180 km/h (real: " + vel + ")");
        assertTrue(((String) h.get("motivo")).contains("km/h"));
    }

    @Test
    @DisplayName("Caso 2 Aceptación: Segunda declaración a las 12:00 (2h después) a 60 km -> No hay hallazgo (30 km/h)")
    void testCaso2_VelocidadPlausible_NoHallazgo() throws Exception {
        Date fecha = sdfDate.parse("2026-10-01");

        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("usuarioId", 102L);
        d1.put("fechaDeclaracion", fecha);
        d1.put("hora", "10:00:00");
        d1.put("latitud", LAT_CALETA_A);
        d1.put("longitud", LON_CALETA_A);
        d1.put("tipo", "RECOLECTOR");
        d1.put("id", 10L);
        d1.put("folio", "DR-1010");

        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("usuarioId", 102L);
        d2.put("fechaDeclaracion", fecha);
        d2.put("hora", "12:00:00"); // 2 horas después (120 minutos >= 30 min, v = 60/2 = 30 km/h <= 80 km/h)
        d2.put("latitud", LAT_PUNTO_60KM);
        d2.put("longitud", LON_PUNTO_60KM);
        d2.put("tipo", "ARMADOR");
        d2.put("id", 11L);
        d2.put("folio", "DA-2020");

        when(reportRepository.getDeclaracionesConGeo(any(), any(), any())).thenReturn(List.of(d1, d2));
        when(reportRepository.contarDeclaracionesSinGeo(any(), any(), any())).thenReturn(0L);

        Map<String, Object> res = reportService.getDobleOperacionGeo(fecha, fecha, null);

        assertEquals(0, ((Number) res.get("totalHallazgos")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hallazgos = (List<Map<String, Object>>) res.get("hallazgos");
        assertTrue(hallazgos.isEmpty(), "No debe haber hallazgo con 30 km/h en 2 horas");
    }

    @Test
    @DisplayName("Caso 3 Aceptación: Segunda declaración a 2 km a las 10:05 -> No hay hallazgo (bajo distancia mínima)")
    void testCaso3_BajoDistanciaMinima_NoHallazgo() throws Exception {
        Date fecha = sdfDate.parse("2026-10-01");

        Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("usuarioId", 103L);
        d1.put("fechaDeclaracion", fecha);
        d1.put("hora", "10:00:00");
        d1.put("latitud", LAT_CALETA_A);
        d1.put("longitud", LON_CALETA_A);
        d1.put("tipo", "RECOLECTOR");
        d1.put("id", 20L);
        d1.put("folio", "DR-1020");

        Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("usuarioId", 103L);
        d2.put("fechaDeclaracion", fecha);
        d2.put("hora", "10:05:00"); // 5 min después pero sólo 2 km (< 5 km)
        d2.put("latitud", LAT_PUNTO_2KM);
        d2.put("longitud", LON_PUNTO_2KM);
        d2.put("tipo", "AREA");
        d2.put("id", 21L);
        d2.put("folio", "DAM-3030");

        when(reportRepository.getDeclaracionesConGeo(any(), any(), any())).thenReturn(List.of(d1, d2));
        when(reportRepository.contarDeclaracionesSinGeo(any(), any(), any())).thenReturn(0L);

        Map<String, Object> res = reportService.getDobleOperacionGeo(fecha, fecha, null);

        assertEquals(0, ((Number) res.get("totalHallazgos")).intValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hallazgos = (List<Map<String, Object>>) res.get("hallazgos");
        assertTrue(hallazgos.isEmpty(), "No debe haber hallazgo por estar bajo la distancia mínima");
    }

    @Test
    @DisplayName("Caso 4 Aceptación: Declaraciones sin coordenadas se cuentan en sinGeolocalizacion")
    void testCaso4_SinGeolocalizacionContabilizadasAparte() throws Exception {
        Date fecha = sdfDate.parse("2026-10-01");

        when(reportRepository.getDeclaracionesConGeo(any(), any(), any())).thenReturn(Collections.emptyList());
        when(reportRepository.contarDeclaracionesSinGeo(any(), any(), any())).thenReturn(14L);

        Map<String, Object> res = reportService.getDobleOperacionGeo(fecha, fecha, null);

        assertEquals(14L, res.get("sinGeolocalizacion"));
        assertEquals(0, res.get("totalDeclaraciones"));
        assertEquals(0, res.get("totalHallazgos"));
    }

    @Test
    @DisplayName("Caso 5 Reconfigurabilidad: doble_op_activo = false desactiva evaluación sin reiniciar")
    void testCaso5_MasterSwitchDesactivado() throws Exception {
        when(configService.getBoolean("doble_op_activo", true)).thenReturn(false);

        Map<String, Object> res = reportService.getDobleOperacionGeo(null, null, null);

        assertFalse((Boolean) res.get("activo"));
        assertEquals(0, res.get("totalHallazgos"));
        verify(reportRepository, never()).getDeclaracionesConGeo(any(), any(), any());
    }

    @Test
    @DisplayName("T8.2: verificarDobleOperacion al guardar genera marca DOBLE_OPERACION")
    void testVerificarDobleOperacion_CreaMarca() throws Exception {
        Date fecha = sdfDate.parse("2026-10-01");

        // Declaración existente previa a las 10:00 en Caleta A
        Map<String, Object> previa = new LinkedHashMap<>();
        previa.put("usuarioId", 101L);
        previa.put("fechaDeclaracion", fecha);
        previa.put("hora", "10:00:00");
        previa.put("latitud", LAT_CALETA_A);
        previa.put("longitud", LON_CALETA_A);
        previa.put("tipo", "RECOLECTOR");
        previa.put("id", 1L);
        previa.put("folio", "DR-1001");

        when(reportRepository.buscarAdyacentesConGeo(eq(101L), eq(2L), eq("ARMADOR")))
                .thenReturn(List.of(previa));

        DeclaracionMarcaModel savedMarca = DeclaracionMarcaModel.builder()
                .id(99L)
                .declaracionTipo("ARMADOR")
                .declaracionId(2L)
                .marca("DOBLE_OPERACION")
                .detalle("Declaración DA-2002 en ...")
                .resuelta(false)
                .build();

        when(declaracionMarcaService.marcar(eq("ARMADOR"), eq(2L), eq("DOBLE_OPERACION"), anyString(), isNull()))
                .thenReturn(savedMarca);

        // Guardando nueva declaración a las 10:20 a 60 km
        Optional<DeclaracionMarcaModel> res = validacionDeclaracionService.verificarDobleOperacion(
                101L,
                LAT_PUNTO_60KM,
                LON_PUNTO_60KM,
                fecha,
                "10:20:00",
                "ARMADOR",
                2L,
                "DA-2002"
        );

        assertTrue(res.isPresent());
        assertEquals("DOBLE_OPERACION", res.get().getMarca());
        verify(declaracionMarcaService, times(1)).marcar(
                eq("ARMADOR"),
                eq(2L),
                eq("DOBLE_OPERACION"),
                argThat(detalle -> detalle.contains("DA-2002") && detalle.contains("DR-1001") && detalle.contains("velocidad implícita")),
                isNull()
        );
    }
}
