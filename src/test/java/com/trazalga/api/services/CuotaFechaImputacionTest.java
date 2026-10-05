package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IMacrozonaRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * T1.6: Pruebas unitarias para la imputación temporal de cuotas de extracción
 * según el parámetro configurable cuota_fecha_imputacion (EXTRACCION vs DECLARACION).
 */
@ExtendWith(MockitoExtension.class)
public class CuotaFechaImputacionTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private IRegionRepository regionRepository;

    @Mock
    private IMacrozonaRepository macrozonaRepository;

    @Mock
    private MacrozonaService macrozonaService;

    @Mock
    private IProvinciaRepository provinciaRepository;

    @Mock
    private IComunaRepository comunaRepository;

    @Mock
    private IEspecieRepository especieRepository;

    @Mock
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Mock
    private IHumedadEstadoRepository humedadEstadoRepository;

    @Mock
    private IAmerbRepository amerbRepository;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @Mock
    private AmerbEspecieHabilitadaService amerbEspecieHabilitadaService;

    @Mock
    private FactorConversionService factorConversionService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CuotaExtraccionService cuotaService;

    private RegionModel regionCoquimbo;
    private ComunaModel comunaLaSerena;
    private EspecieModel especieHuiro;
    private ExtraccionTipoModel metodoVarado;

    private CuotaExtraccionModel cuotaSeptiembre;
    private CuotaExtraccionModel cuotaOctubre;

    @BeforeEach
    void setUp() {
        regionCoquimbo = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        comunaLaSerena = ComunaModel.builder().id(4101L).nombre("La Serena").region(regionCoquimbo).build();
        especieHuiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        metodoVarado = ExtraccionTipoModel.builder().id(10L).nombre("Varado").build();

        cuotaSeptiembre = CuotaExtraccionModel.builder()
                .id(101L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .region(regionCoquimbo)
                .comunas(Set.of(comunaLaSerena))
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .fechaInicio(Date.valueOf("2026-09-01"))
                .fechaFin(Date.valueOf("2026-09-30"))
                .limiteKg(10_000.0)
                .metrica("DESEMBARQUE")
                .activo(true)
                .estado("ABIERTA")
                .build();

        cuotaOctubre = CuotaExtraccionModel.builder()
                .id(102L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .region(regionCoquimbo)
                .comunas(Set.of(comunaLaSerena))
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .fechaInicio(Date.valueOf("2026-10-01"))
                .fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(10_000.0)
                .metrica("DESEMBARQUE")
                .activo(true)
                .estado("ABIERTA")
                .build();
    }

    @Test
    @DisplayName("T1.6: Con modo EXTRACCION (defecto), una faena del 30-09 declarada el 01-10 evalúa cuota de septiembre")
    void testModoExtraccion_ImputaSeptiembre() {
        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION"))
                .thenReturn("EXTRACCION");
        when(cuotaRepository.findByActivoTrue())
                .thenReturn(List.of(cuotaSeptiembre, cuotaOctubre));

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn("250");
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        java.util.Date fechaFaena = Date.valueOf("2026-09-30");
        java.util.Date fechaDecl = Date.valueOf("2026-10-01");

        CuotaExtraccionService.EvaluacionCuotaResult res = cuotaService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 50L, null, 1L, 10L, 4101L,
                fechaFaena, fechaDecl, new BigDecimal("100"), new BigDecimal("100"));

        assertTrue(res.isPermite());
        assertNotNull(res.getCuotaAplicada());
        assertEquals(101L, res.getCuotaAplicada().getId(),
                "Debe evaluar contra la cuota de septiembre (#101), no la de octubre");
    }

    @Test
    @DisplayName("T1.6: Con modo DECLARACION, una faena del 30-09 declarada el 01-10 evalúa cuota de octubre")
    void testModoDeclaracion_ImputaOctubre() {
        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION"))
                .thenReturn("DECLARACION");
        when(cuotaRepository.findByActivoTrue())
                .thenReturn(List.of(cuotaSeptiembre, cuotaOctubre));

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn("300");
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        java.util.Date fechaFaena = Date.valueOf("2026-09-30");
        java.util.Date fechaDecl = Date.valueOf("2026-10-01");

        CuotaExtraccionService.EvaluacionCuotaResult res = cuotaService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 50L, null, 1L, 10L, 4101L,
                fechaFaena, fechaDecl, new BigDecimal("100"), new BigDecimal("100"));

        assertTrue(res.isPermite());
        assertNotNull(res.getCuotaAplicada());
        assertEquals(102L, res.getCuotaAplicada().getId(),
                "Debe evaluar contra la cuota de octubre (#102), según fecha de declaración");
    }

    @Test
    @DisplayName("T1.6: ejecutarQueryConsumo filtra por d.fecha_extraccion cuando el modo es EXTRACCION")
    void testQueryConsumo_ModoExtraccion_FiltraPorFechaExtraccion() {
        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION"))
                .thenReturn("EXTRACCION");

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn("500");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(entityManager.createNativeQuery(sqlCaptor.capture())).thenReturn(mockQuery);

        BigDecimal consumo = cuotaService.ejecutarConsultaConsumo(
                cuotaSeptiembre, Date.valueOf("2026-09-15"), "RECOLECTOR", null);

        assertNotNull(consumo);
        List<String> executedQueries = sqlCaptor.getAllValues();
        assertFalse(executedQueries.isEmpty());
        for (String q : executedQueries) {
            assertTrue(q.contains("d.fecha_extraccion BETWEEN :startDate AND :endDate"),
                    "La consulta debe filtrar por d.fecha_extraccion. SQL generado: " + q);
            assertFalse(q.contains("d.fecha_declaracion BETWEEN"),
                    "No debe filtrar por d.fecha_declaracion en modo EXTRACCION");
        }
    }

    @Test
    @DisplayName("T1.6: ejecutarQueryConsumo filtra por d.fecha_declaracion cuando el modo es DECLARACION")
    void testQueryConsumo_ModoDeclaracion_FiltraPorFechaDeclaracion() {
        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION"))
                .thenReturn("DECLARACION");

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn("750");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(entityManager.createNativeQuery(sqlCaptor.capture())).thenReturn(mockQuery);

        BigDecimal consumo = cuotaService.ejecutarConsultaConsumo(
                cuotaOctubre, Date.valueOf("2026-10-15"), "RECOLECTOR", null);

        assertNotNull(consumo);
        List<String> executedQueries = sqlCaptor.getAllValues();
        assertFalse(executedQueries.isEmpty());
        for (String q : executedQueries) {
            assertTrue(q.contains("d.fecha_declaracion BETWEEN :startDate AND :endDate"),
                    "La consulta debe filtrar por d.fecha_declaracion. SQL generado: " + q);
            assertFalse(q.contains("d.fecha_extraccion BETWEEN"),
                    "No debe filtrar por d.fecha_extraccion en modo DECLARACION");
        }
    }

    @Test
    @DisplayName("T1.6: El cambio de parámetro invalida o segrega la clave de caché permitiendo actualizar consumos sin reiniciar")
    void testSegregacionCache_CambioParametro() {
        // En modo EXTRACCION
        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION"))
                .thenReturn("EXTRACCION");

        Query mockQueryExtr = mock(Query.class);
        when(mockQueryExtr.getSingleResult()).thenReturn("1000");

        ArgumentCaptor<String> sqlCaptorExtr = ArgumentCaptor.forClass(String.class);
        when(entityManager.createNativeQuery(sqlCaptorExtr.capture())).thenReturn(mockQueryExtr);

        BigDecimal consumoExtr = cuotaService.ejecutarConsultaConsumo(
                cuotaSeptiembre, Date.valueOf("2026-09-15"), "RECOLECTOR", null);
        assertNotNull(consumoExtr);

        // Cambiar dinámicamente a DECLARACION
        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION"))
                .thenReturn("DECLARACION");

        Query mockQueryDecl = mock(Query.class);
        when(mockQueryDecl.getSingleResult()).thenReturn("1200");

        ArgumentCaptor<String> sqlCaptorDecl = ArgumentCaptor.forClass(String.class);
        when(entityManager.createNativeQuery(sqlCaptorDecl.capture())).thenReturn(mockQueryDecl);

        BigDecimal consumoDecl = cuotaService.ejecutarConsultaConsumo(
                cuotaSeptiembre, Date.valueOf("2026-09-15"), "RECOLECTOR", null);
        assertNotNull(consumoDecl);

        // Se verifica que la consulta con modo DECLARACION se ejecutó efectivamente (sin quedar atrapada en caché anterior)
        List<String> declQueries = sqlCaptorDecl.getAllValues();
        assertTrue(declQueries.stream().anyMatch(q -> q.contains("d.fecha_declaracion BETWEEN")),
                "Al cambiar el parámetro a DECLARACION, debe generarse y ejecutarse la consulta con fecha_declaracion");
    }
}
