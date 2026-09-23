package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PerfiladorRiesgoServiceTest {

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private PerfiladorRiesgoService perfiladorService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getBoolean("riesgo_activo", true)).thenReturn(true);
        lenient().when(configService.getDouble("riesgo_variacion_amarillo_pct", 5.0)).thenReturn(5.0);
        lenient().when(configService.getDouble("riesgo_variacion_rojo_pct", 10.0)).thenReturn(10.0);
        lenient().when(configService.getInt("riesgo_dias_amarillo", 3)).thenReturn(3);
        lenient().when(configService.getInt("riesgo_dias_rojo", 7)).thenReturn(7);
        lenient().when(configService.getValor("riesgo_escala_humedo_sin_merma", "ROJO")).thenReturn("ROJO");
        lenient().when(configService.getInt("riesgo_agravante_veda_niveles", 1)).thenReturn(1);
        lenient().when(configService.getInt("riesgo_agravante_led_niveles", 1)).thenReturn(1);
        lenient().when(configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO")).thenReturn("HUMEDO");
    }

    @Test
    @DisplayName("R9.2: Lote verde con marca activa EN_VEDA sube 1 nivel a AMARILLO")
    void testAgravanteVedaSubeUnNivel() {
        Map<String, Object> lote = new HashMap<>();
        lote.put("folioOrigen", "FOLIO-TEST-VEDA");
        lote.put("humedadOrigen", "HÚMEDO");
        lote.put("deltaPct", 2.0); // Variación verde (< 5%)
        lote.put("diasEnBodega", 1); // Retención verde (< 3d)
        lote.put("kgDestino", 1000.0);
        lote.put("severidadBiologica", "NEUTRA");
        lote.put("marcasActivas", Collections.singletonList("EN_VEDA"));

        when(reportService.getTrazabilidadLoteDetalle(any(), any(), any()))
                .thenReturn(Collections.singletonList(lote));

        Map<String, Object> res = perfiladorService.evaluarRiesgo(null, null, null, null, null);
        assertNotNull(res);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) res.get("lotes");
        assertEquals(1, lotes.size());
        assertEquals("AMARILLO", lotes.get(0).get("nivelRiesgo"), "Un lote verde con marca EN_VEDA debe subir a AMARILLO");
        assertTrue(lotes.get(0).get("motivoRiesgo").toString().contains("EN_VEDA"));
    }

    @Test
    @DisplayName("R9.2: Lote amarillo con LED_EXCEDIDO y EN_VEDA sube a ROJO y no más allá")
    void testDobleAgravanteTopaEnRojo() {
        Map<String, Object> lote = new HashMap<>();
        lote.put("folioOrigen", "FOLIO-TEST-DOBLE");
        lote.put("humedadOrigen", "HÚMEDO");
        lote.put("deltaPct", 6.0); // Variación amarilla (5% - 10%)
        lote.put("diasEnBodega", 1);
        lote.put("kgDestino", 2000.0);
        lote.put("severidadBiologica", "NEUTRA");
        lote.put("marcasActivas", Arrays.asList("LED_EXCEDIDO", "EN_VEDA"));

        when(reportService.getTrazabilidadLoteDetalle(any(), any(), any()))
                .thenReturn(Collections.singletonList(lote));

        Map<String, Object> res = perfiladorService.evaluarRiesgo(null, null, null, null, null);
        assertNotNull(res);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) res.get("lotes");
        assertEquals(1, lotes.size());
        assertEquals("ROJO", lotes.get(0).get("nivelRiesgo"), "Lote amarillo con 2 agravantes (+2 niveles) debe topar en ROJO");
    }

    @Test
    @DisplayName("R9.2: Bajar riesgo_variacion_rojo_pct de 10 a 7 reclasifica un lote con 8% de variación a ROJO")
    void testReclasificacionDinamicaVariacion() {
        Map<String, Object> lote = new HashMap<>();
        lote.put("folioOrigen", "FOLIO-TEST-RECLASIF");
        lote.put("humedadOrigen", "HÚMEDO");
        lote.put("deltaPct", 8.0); // 8% variación
        lote.put("diasEnBodega", 1);
        lote.put("kgDestino", 1500.0);
        lote.put("severidadBiologica", "NEUTRA");
        lote.put("marcasActivas", Collections.emptyList());

        when(reportService.getTrazabilidadLoteDetalle(any(), any(), any()))
                .thenReturn(Collections.singletonList(lote));

        // Inicialmente con umbral 10.0%: 8% cae en AMARILLO
        Map<String, Object> res1 = perfiladorService.evaluarRiesgo(null, null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes1 = (List<Map<String, Object>>) res1.get("lotes");
        assertEquals("AMARILLO", lotes1.get(0).get("nivelRiesgo"));

        // Modificando umbral a 7.0%: 8% reclasifica dinámicamente a ROJO
        when(configService.getDouble("riesgo_variacion_rojo_pct", 10.0)).thenReturn(7.0);
        Map<String, Object> res2 = perfiladorService.evaluarRiesgo(null, null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes2 = (List<Map<String, Object>>) res2.get("lotes");
        assertEquals("ROJO", lotes2.get(0).get("nivelRiesgo"));
    }

    @Test
    @DisplayName("R9.2: Lote con severidadBiologica CRITICA escala directamente a ROJO")
    void testEscalaSeveridadBiologicaCritica() {
        Map<String, Object> lote = new HashMap<>();
        lote.put("folioOrigen", "FOLIO-TEST-BIO");
        lote.put("humedadOrigen", "HÚMEDO");
        lote.put("deltaPct", 0.0); // Variación 0% (verde)
        lote.put("diasEnBodega", 1); // Retención 1d (verde)
        lote.put("kgDestino", 3000.0);
        lote.put("severidadBiologica", "CRITICA"); // Modificador biológico crítico
        lote.put("marcasActivas", Collections.emptyList());

        when(reportService.getTrazabilidadLoteDetalle(any(), any(), any()))
                .thenReturn(Collections.singletonList(lote));

        Map<String, Object> res = perfiladorService.evaluarRiesgo(null, null, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) res.get("lotes");
        assertEquals("ROJO", lotes.get(0).get("nivelRiesgo"));
        assertTrue(lotes.get(0).get("motivoRiesgo").toString().contains("Inconsistencia biológica grave"));
    }

    @Test
    @DisplayName("R9.3: riesgo_activo = false retorna controlDesactivado = true y listas vacías")
    void testRiesgoDesactivado_RetornaControlDesactivado() {
        when(configService.getBoolean("riesgo_activo", true)).thenReturn(false);

        Map<String, Object> res = perfiladorService.evaluarRiesgo(null, null, null, null, null);

        assertNotNull(res);
        assertEquals(false, res.get("activo"));
        assertEquals(true, res.get("controlDesactivado"));
        assertTrue(res.get("mensaje").toString().contains("desactivado"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) res.get("lotes");
        assertTrue(lotes.isEmpty());
        verify(reportService, never()).getTrazabilidadLoteDetalle(any(), any(), any());
    }

    @Test
    @DisplayName("R9.2: Matriz 3x3 y jerarquización de actores calculada correctamente")
    void testMatrizYJerarquizacionActores() {
        Map<String, Object> lote1 = new HashMap<>();
        lote1.put("folioOrigen", "F1");
        lote1.put("humedadOrigen", "HÚMEDO");
        lote1.put("deltaPct", 12.0); // Rojo (>10%)
        lote1.put("diasEnBodega", 8); // Rojo (>7d)
        lote1.put("kgDestino", 1000.0);
        lote1.put("actorComercializador", "COMERCIALIZADORA DEL SUR");
        lote1.put("severidadBiologica", "NEUTRA");

        Map<String, Object> lote2 = new HashMap<>();
        lote2.put("folioOrigen", "F2");
        lote2.put("humedadOrigen", "HÚMEDO");
        lote2.put("deltaPct", 2.0); // Verde (<5%)
        lote2.put("diasEnBodega", 1); // Verde (<3d)
        lote2.put("kgDestino", 500.0);
        lote2.put("actorComercializador", "ALGAS LIMPIAS LTDA");
        lote2.put("severidadBiologica", "NEUTRA");

        when(reportService.getTrazabilidadLoteDetalle(any(), any(), any()))
                .thenReturn(Arrays.asList(lote1, lote2));

        Map<String, Object> res = perfiladorService.evaluarRiesgo(null, null, null, null, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actores = (List<Map<String, Object>>) res.get("actores");
        assertFalse(actores.isEmpty());
        // El primer actor debe ser el que tiene lote ROJO
        assertEquals("COMERCIALIZADORA DEL SUR", actores.get(0).get("actor"));
        assertEquals("ROJO", actores.get(0).get("peorNivel"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matriz = (List<Map<String, Object>>) res.get("matriz");
        assertEquals(9, matriz.size(), "La matriz debe tener 9 celdas (3x3)");
    }
}
