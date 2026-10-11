package com.trazalga.api.services.retencion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.ReportRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import com.trazalga.api.services.hallazgos.HallazgoService;
import com.trazalga.api.services.parametros.ParametrosRetencion;
import com.trazalga.api.services.parametros.ParametrosService;

/**
 * Pruebas unitarias de RetencionCustodiaService (TR.1, TR.2, TR.3, TR.5).
 */
@ExtendWith(MockitoExtension.class)
public class RetencionCustodiaServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ParametrosService parametrosService;

    @Mock
    private PoliticaRetencion politicaRetencion;

    @Mock
    private HallazgoService hallazgoService;

    @Mock
    private IDeclaracionMarcaRepository marcaRepository;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private Query mockQuery;

    @InjectMocks
    private RetencionCustodiaService service;

    private ParametrosRetencion paramsActivo;
    private ParametrosRetencion paramsDesactivado;

    @BeforeEach
    void setUp() {
        paramsActivo = new ParametrosRetencion(
                true,   // retencionActivo
                24,     // humedoMaxHoras
                72,     // semihumedoMaxHoras
                216,    // semisecoMaxHoras
                80.0,   // semaforoPreavisoPct
                true,   // bloqueoActivo
                120,    // bloqueoHumedoHoras
                0,
                0,
                "TODAS",
                80.0
        );

        paramsDesactivado = new ParametrosRetencion(
                false,  // retencionActivo = false
                24, 72, 216, 80.0,
                false, 120, 0, 0, "TODAS", 80.0
        );
    }

    @Test
    @DisplayName("TR.2: Cuando control de retención está inactivo, retorna métricas con controlDesactivado = true")
    void testObtenerMetricasRetencion_Inactivo() {
        when(parametrosService.retencion()).thenReturn(paramsDesactivado);

        Map<String, Object> result = service.obtenerMetricasRetencion(null, null);

        assertNotNull(result);
        assertEquals(false, result.get("activo"));
        assertEquals(true, result.get("controlDesactivado"));
        assertEquals(0, result.get("totalLotesEnBodega"));
        verifyNoInteractions(reportRepository);
    }

    @Test
    @DisplayName("TR.2: Cuando control de retención está activo, delega en reportRepository y enriquece contadores")
    void testObtenerMetricasRetencion_Activo() {
        when(parametrosService.retencion()).thenReturn(paramsActivo);

        Map<String, Object> repoMock = new HashMap<>();
        repoMock.put("totalLotes", 5);
        repoMock.put("lotesDestinados", 2L);
        repoMock.put("lotes", new ArrayList<Map<String, Object>>());

        when(reportRepository.getRetencionPorHumedad(null, null, 24, 72, 216, 80))
                .thenReturn(repoMock);

        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.obtenerMetricasRetencion(null, null);

        assertNotNull(result);
        assertEquals(true, result.get("activo"));
        assertEquals(false, result.get("controlDesactivado"));
        assertEquals(2L, result.get("totalLotesEnTransitoAPlanta"));
        assertEquals(0L, result.get("totalLotesEnBodega"));
        assertEquals(0L, result.get("cargasRetenidas"));
        assertNotNull(result.get("parametros"));
    }

    @Test
    @DisplayName("TR.3: No re-bloquea una carga si ya tiene resolución LIBERADA por fiscalizador")
    void testRegistrarMarcaRetencionExcedida_NoRebloqueaSiLiberada() {
        DeclaracionMarcaModel marcaLiberada = DeclaracionMarcaModel.builder()
                .id(99L)
                .declaracionTipo("RECOLECTOR")
                .declaracionId(10L)
                .marca("RETENCION_EXCEDIDA")
                .resolucionTipo("LIBERADA")
                .resuelta(true)
                .build();

        when(marcaRepository.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", 10L))
                .thenReturn(List.of(marcaLiberada));

        DeclaracionMarcaModel res = service.registrarMarcaRetencionExcedida(
                "RECOLECTOR", 10L, "Retención superada", 1L, null);

        assertNotNull(res);
        assertEquals(99L, res.getId());
        assertEquals("LIBERADA", res.getResolucionTipo());
        verify(hallazgoService, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TR.3: Registra nueva marca RETENCION_EXCEDIDA con regla D3 si no está liberada")
    void testRegistrarMarcaRetencionExcedida_RegistraNuevaMarca() {
        when(marcaRepository.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", 10L))
                .thenReturn(Collections.emptyList());

        DeclaracionMarcaModel guardada = DeclaracionMarcaModel.builder()
                .id(101L)
                .declaracionTipo("RECOLECTOR")
                .declaracionId(10L)
                .marca("RETENCION_EXCEDIDA")
                .claveIdempotencia("RECOLECTOR:10:RETENCION_EXCEDIDA")
                .build();

        when(hallazgoService.registrar(
                eq("RECOLECTOR"), eq(10L), eq("RETENCION_EXCEDIDA"), eq("D3"),
                any(CriterioHallazgo.class), anyString(), eq("BODEGA_VIRTUAL_AUTOMA"), eq(1L), isNull()))
                .thenReturn(guardada);

        DeclaracionMarcaModel res = service.registrarMarcaRetencionExcedida(
                "RECOLECTOR", 10L, "Excedió 120 horas", 1L, null);

        assertNotNull(res);
        assertEquals(101L, res.getId());
        assertEquals("RETENCION_EXCEDIDA", res.getMarca());
        verify(hallazgoService).registrar(
                eq("RECOLECTOR"), eq(10L), eq("RETENCION_EXCEDIDA"), eq("D3"),
                any(CriterioHallazgo.class), anyString(), eq("BODEGA_VIRTUAL_AUTOMA"), eq(1L), isNull());
    }

    @Test
    @DisplayName("TR.3: Barrido periódico evalúa lotes y aplica bloqueo automático D3")
    void testBarrido_AplicaBloqueo() {
        when(parametrosService.retencion()).thenReturn(paramsActivo);

        // Simular lote en bodega devuelto por la consulta SQL
        Object[] rowLote = new Object[] {
                15L, "RECOLECTOR", "RO-15", new Date(), "10:00", 500.0,
                1L, "Huiro Negro", "HÚMEDO", 20L, "Comercializador Test", "11111111-1",
                30L, "Recolector Test", "22222222-2", null, null, null, null
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(Collections.singletonList(rowLote));

        ResultadoEvaluacionRetencion evalBloqueo = ResultadoEvaluacionRetencion.builder()
                .estadoDeclarado("HÚMEDO")
                .claveMatriz("HUMEDO")
                .tramoReal("SEMI HÚMEDO")
                .plazoMaxHoras(24)
                .semaforo("ROJO")
                .vencido(true)
                .bloqueoRequerido(true)
                .motivoDetalle("Retención excedida a 125 h")
                .build();

        when(politicaRetencion.evaluar(any(), any(), any(), anyDouble(), any(ParametrosRetencion.class)))
                .thenReturn(evalBloqueo);

        when(marcaRepository.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", 15L))
                .thenReturn(Collections.emptyList());

        RetencionCustodiaService.ResumenBarridoRetencion resumen = service.barrer(LocalDateTime.now());

        assertNotNull(resumen);
        assertEquals(1, resumen.lotesEvaluados());
        assertEquals(1, resumen.bloqueosAplicados());
        verify(hallazgoService).registrar(
                eq("RECOLECTOR"), eq(15L), eq("RETENCION_EXCEDIDA"), eq("D3"),
                any(CriterioHallazgo.class), anyString(), anyString(), any(), isNull());
    }
}
