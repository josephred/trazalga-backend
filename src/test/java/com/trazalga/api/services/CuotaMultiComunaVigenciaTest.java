package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.ProvinciaModel;
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

@ExtendWith(MockitoExtension.class)
public class CuotaMultiComunaVigenciaTest {

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
    private CuotaExtraccionService cuotaExtraccionService;

    private RegionModel regionCoquimbo;
    private RegionModel regionValparaiso;
    private ProvinciaModel provinciaElqui;
    private ComunaModel comunaLaSerena;
    private ComunaModel comunaCoquimbo;
    private ComunaModel comunaValparaiso;
    private EspecieModel especieHuiro;
    private ExtraccionTipoModel extraccionBuceo;

    @BeforeEach
    void setUp() {
        cuotaExtraccionService.invalidarCacheConsumo();

        regionCoquimbo = new RegionModel();
        regionCoquimbo.setId(4L);
        regionCoquimbo.setNombre("Coquimbo");

        regionValparaiso = new RegionModel();
        regionValparaiso.setId(5L);
        regionValparaiso.setNombre("Valparaíso");

        provinciaElqui = new ProvinciaModel();
        provinciaElqui.setId(41L);
        provinciaElqui.setNombre("Elqui");
        provinciaElqui.setRegion(regionCoquimbo);

        comunaLaSerena = new ComunaModel();
        comunaLaSerena.setId(4101L);
        comunaLaSerena.setNombre("La Serena");
        comunaLaSerena.setRegion(regionCoquimbo);
        comunaLaSerena.setProvincia(provinciaElqui);

        comunaCoquimbo = new ComunaModel();
        comunaCoquimbo.setId(4102L);
        comunaCoquimbo.setNombre("Coquimbo");
        comunaCoquimbo.setRegion(regionCoquimbo);
        comunaCoquimbo.setProvincia(provinciaElqui);

        comunaValparaiso = new ComunaModel();
        comunaValparaiso.setId(5101L);
        comunaValparaiso.setNombre("Valparaíso");
        comunaValparaiso.setRegion(regionValparaiso);

        especieHuiro = new EspecieModel();
        especieHuiro.setId(1L);
        especieHuiro.setNombre("Huiro Negro");

        extraccionBuceo = new ExtraccionTipoModel();
        extraccionBuceo.setId(1L);
        extraccionBuceo.setNombre("Buceo");

        lenient().when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        lenient().when(comunaRepository.findById(4102L)).thenReturn(Optional.of(comunaCoquimbo));
        lenient().when(comunaRepository.findById(5101L)).thenReturn(Optional.of(comunaValparaiso));
        lenient().when(regionRepository.findById(4L)).thenReturn(Optional.of(regionCoquimbo));
        lenient().when(regionRepository.findById(5L)).thenReturn(Optional.of(regionValparaiso));
        lenient().when(especieRepository.findById(1L)).thenReturn(Optional.of(especieHuiro));
        lenient().when(extraccionTipoRepository.findById(1L)).thenReturn(Optional.of(extraccionBuceo));
    }

    // =========================================================================
    // T1.4: Validación de vigencia mensual en un mismo mes calendario
    // =========================================================================

    @Test
    @DisplayName("T1.4: Cuota mensual con fechas dentro del mismo mes calendario debe guardarse exitosamente")
    void testCuotaMensual_MismoMesCalendario_PermiteGuardar() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setMetrica("CAPTURA");
        c.setNivelAgregacion("COMUNA");
        c.setComuna(comunaLaSerena);
        c.setFechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)));
        c.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)));
        c.setActivo(false);

        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(i -> i.getArgument(0));

        CuotaExtraccionModel guardada = cuotaExtraccionService.save(c);
        assertNotNull(guardada);
        assertEquals("MENSUAL", guardada.getPeriodo());
    }

    @Test
    @DisplayName("T1.4: Cuota mensual con fechas que cruzan meses calendario debe ser rechazada")
    void testCuotaMensual_CruzaMesCalendario_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("COMUNA");
        c.setComuna(comunaLaSerena);
        // Inicio en febrero, fin en marzo (desfase típico de timezone)
        c.setFechaInicio(Date.valueOf(LocalDate.of(2026, 2, 28)));
        c.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("La vigencia mensual debe quedar dentro de un mismo mes"));
        assertTrue(ex.getMessage().contains("28-02-2026"));
        assertTrue(ex.getMessage().contains("31-03-2026"));
    }

    @Test
    @DisplayName("T1.4: Cuota mensual con fechas que cruzan años debe ser rechazada")
    void testCuotaMensual_CruzaAnioCalendario_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("COMUNA");
        c.setComuna(comunaLaSerena);
        c.setFechaInicio(Date.valueOf(LocalDate.of(2025, 12, 1)));
        c.setFechaFin(Date.valueOf(LocalDate.of(2026, 1, 31)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("La vigencia mensual debe quedar dentro de un mismo mes"));
    }

    @Test
    @DisplayName("T1.4: Cuota ANUAL puede cruzar meses sin lanzar excepción de mes calendario")
    void testCuotaAnual_CruzaMeses_PermiteGuardar() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("ANUAL");
        c.setLimiteKg(50000.0);
        c.setMetrica("CAPTURA");
        c.setNivelAgregacion("COMUNA");
        c.setComuna(comunaLaSerena);
        c.setFechaInicio(Date.valueOf(LocalDate.of(2026, 1, 1)));
        c.setFechaFin(Date.valueOf(LocalDate.of(2026, 12, 31)));
        c.setActivo(false);

        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(i -> i.getArgument(0));

        CuotaExtraccionModel guardada = cuotaExtraccionService.save(c);
        assertNotNull(guardada);
        assertEquals("ANUAL", guardada.getPeriodo());
    }

    // =========================================================================
    // T1.1: Multi-comuna y validaciones de alcance territorial
    // =========================================================================

    @Test
    @DisplayName("T1.1: Nivel COMUNA sin comunas debe ser rechazado (K6)")
    void testNivelComuna_SinComunas_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("COMUNA");
        c.setComuna(null);
        c.setComunas(Collections.emptySet());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("Las cuotas de nivel COMUNA deben tener al menos una comuna asociada"));
    }

    @Test
    @DisplayName("T1.1: Nivel COMUNA con comunas de distintas regiones debe ser rechazado")
    void testNivelComuna_ComunasDeDistintasRegiones_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("COMUNA");

        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comunaLaSerena); // Region Coquimbo (4)
        comunas.add(comunaValparaiso); // Region Valparaíso (5)
        c.setComunas(comunas);

        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        when(comunaRepository.findById(5101L)).thenReturn(Optional.of(comunaValparaiso));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("Todas las comunas seleccionadas deben pertenecer a la misma región"));
    }

    @Test
    @DisplayName("T1.1: Nivel COMUNA con múltiples comunas de la misma región (La Serena + Coquimbo) es válido y autoasigna región")
    void testNivelComuna_MultiplesComunasMismaRegion_GuardaYAutoasignaRegion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("COMUNA");
        c.setActivo(false);

        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comunaLaSerena);
        comunas.add(comunaCoquimbo);
        c.setComunas(comunas);

        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        when(comunaRepository.findById(4102L)).thenReturn(Optional.of(comunaCoquimbo));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(i -> i.getArgument(0));

        CuotaExtraccionModel guardada = cuotaExtraccionService.save(c);
        assertNotNull(guardada);
        assertNotNull(guardada.getRegion(), "Debe autoasignar la región común");
        assertEquals(4L, guardada.getRegion().getId());
        assertEquals(2, guardada.getComunas().size());
        assertEquals(comunaLaSerena, guardada.getComuna(), "La primera comuna debe asignarse como cabecera");
    }

    @Test
    @DisplayName("T1.1: Nivel REGION sin región debe ser rechazado")
    void testNivelRegion_SinRegion_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("REGION");
        c.setRegion(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("Las cuotas de nivel REGION deben especificar una región"));
    }

    @Test
    @DisplayName("T1.1: Nivel REGION con comunas asociadas debe ser rechazado")
    void testNivelRegion_ConComunasAsociadas_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(5000.0);
        c.setNivelAgregacion("REGION");
        c.setRegion(regionCoquimbo);
        c.setComuna(comunaLaSerena);

        when(regionRepository.findById(4L)).thenReturn(Optional.of(regionCoquimbo));
        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("Las cuotas de nivel REGION no deben tener comunas asociadas"));
    }

    @Test
    @DisplayName("T1.1: idsComunas extrae correctamente todos los IDs de cuota multi-comuna")
    void testIdsComunas_MultiComuna() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comunaLaSerena);
        comunas.add(comunaCoquimbo);
        c.setComunas(comunas);

        Set<Long> ids = cuotaExtraccionService.idsComunas(c);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(4101L));
        assertTrue(ids.contains(4102L));
    }

    @Test
    @DisplayName("T1.1: construirFiltroTerritorial para multi-comuna genera SQL con IN (:filtroComunaIds)")
    void testConstruirFiltroTerritorial_MultiComuna_GeneraClauseIn() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setId(10L);
        c.setNivelAgregacion("COMUNA");
        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comunaLaSerena);
        comunas.add(comunaCoquimbo);
        c.setComunas(comunas);

        CuotaExtraccionService.FiltroTerritorialCuota filtroRecolector =
                cuotaExtraccionService.construirFiltroTerritorial(c, "declaracion_recolector", null);

        assertTrue(filtroRecolector.getSqlFragment().contains("u.comuna_id IN (:filtroComunaIds)"));
        assertEquals(Set.of(4101L, 4102L), filtroRecolector.getParametros().get("filtroComunaIds"));

        CuotaExtraccionService.FiltroTerritorialCuota filtroArmador =
                cuotaExtraccionService.construirFiltroTerritorial(c, "declaracion_armador", null);

        assertTrue(filtroArmador.getSqlFragment().contains("d.comuna_id IN (:filtroComunaIds)"));
        assertEquals(Set.of(4101L, 4102L), filtroArmador.getParametros().get("filtroComunaIds"));
    }

    // =========================================================================
    // T1.5: Vigencias que se solapan y jerarquía por fechas
    // =========================================================================

    @Test
    @DisplayName("T1.5: Cuotas mensuales consecutivas (Enero y Febrero) NO se solapan y pueden coexistir activas")
    void testSolapamiento_MesesConsecutivos_NoSeSolapan() {
        CuotaExtraccionModel cuotaEnero = new CuotaExtraccionModel();
        cuotaEnero.setId(1L);
        cuotaEnero.setPeriodo("MENSUAL");
        cuotaEnero.setLimiteKg(1000.0);
        cuotaEnero.setEspecie(especieHuiro);
        cuotaEnero.setExtraccionTipo(extraccionBuceo);
        cuotaEnero.setComuna(comunaLaSerena);
        cuotaEnero.setFechaInicio(Date.valueOf(LocalDate.of(2026, 1, 1)));
        cuotaEnero.setFechaFin(Date.valueOf(LocalDate.of(2026, 1, 31)));
        cuotaEnero.setActivo(true);

        CuotaExtraccionModel cuotaFebrero = new CuotaExtraccionModel();
        cuotaFebrero.setId(2L);
        cuotaFebrero.setPeriodo("MENSUAL");
        cuotaFebrero.setLimiteKg(1200.0);
        cuotaFebrero.setEspecie(especieHuiro);
        cuotaFebrero.setExtraccionTipo(extraccionBuceo);
        cuotaFebrero.setComuna(comunaLaSerena);
        cuotaFebrero.setFechaInicio(Date.valueOf(LocalDate.of(2026, 2, 1)));
        cuotaFebrero.setFechaFin(Date.valueOf(LocalDate.of(2026, 2, 28)));
        cuotaFebrero.setActivo(true);

        assertFalse(cuotaExtraccionService.seSolapan(cuotaEnero, cuotaFebrero),
                "Enero y Febrero no deben solaparse");

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaEnero));

        // Validar que febrero pasa sin lanzar excepción
        assertDoesNotThrow(() -> cuotaExtraccionService.validarSolapamiento(cuotaFebrero));
    }

    @Test
    @DisplayName("T1.5: Dos cuotas activas que se solapan en fechas para la misma comuna y especie deben ser rechazadas")
    void testSolapamiento_FechasSolapadasMismaComuna_LanzaExcepcion() {
        CuotaExtraccionModel cuotaExistente = new CuotaExtraccionModel();
        cuotaExistente.setId(10L);
        cuotaExistente.setNivelAgregacion("COMUNA");
        cuotaExistente.setPeriodo("MENSUAL");
        cuotaExistente.setLimiteKg(2000.0);
        cuotaExistente.setEspecie(especieHuiro);
        cuotaExistente.setExtraccionTipo(extraccionBuceo);
        cuotaExistente.setComuna(comunaLaSerena);
        cuotaExistente.setFechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)));
        cuotaExistente.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)));
        cuotaExistente.setActivo(true);

        CuotaExtraccionModel cuotaNueva = new CuotaExtraccionModel();
        cuotaNueva.setId(20L);
        cuotaNueva.setNivelAgregacion("COMUNA");
        cuotaNueva.setPeriodo("MENSUAL");
        cuotaNueva.setLimiteKg(2500.0);
        cuotaNueva.setEspecie(especieHuiro);
        cuotaNueva.setExtraccionTipo(extraccionBuceo);
        cuotaNueva.setComuna(comunaLaSerena);
        cuotaNueva.setFechaInicio(Date.valueOf(LocalDate.of(2026, 3, 15)));
        cuotaNueva.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)));
        cuotaNueva.setActivo(true);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaExistente));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.validarSolapamiento(cuotaNueva);
        });

        assertTrue(ex.getMessage().contains("ya tiene una cuota activa de Huiro Negro"));
        assertTrue(ex.getMessage().contains("cuota #10"));
    }

    @Test
    @DisplayName("T1.5: Dos cuotas multi-comuna que comparten una comuna (ej. {La Serena, Coquimbo} y {Coquimbo, Vicuña}) y coinciden en fechas deben ser rechazadas")
    void testSolapamiento_MultiComunaConInterseccion_LanzaExcepcion() {
        CuotaExtraccionModel cuotaExistente = new CuotaExtraccionModel();
        cuotaExistente.setId(11L);
        cuotaExistente.setNivelAgregacion("COMUNA");
        cuotaExistente.setPeriodo("MENSUAL");
        cuotaExistente.setLimiteKg(3000.0);
        cuotaExistente.setEspecie(especieHuiro);
        cuotaExistente.setExtraccionTipo(extraccionBuceo);
        cuotaExistente.setComunas(Set.of(comunaLaSerena, comunaCoquimbo));
        cuotaExistente.setComuna(comunaLaSerena);
        cuotaExistente.setFechaInicio(Date.valueOf(LocalDate.of(2026, 4, 1)));
        cuotaExistente.setFechaFin(Date.valueOf(LocalDate.of(2026, 4, 30)));
        cuotaExistente.setActivo(true);

        CuotaExtraccionModel cuotaNueva = new CuotaExtraccionModel();
        cuotaNueva.setId(12L);
        cuotaNueva.setNivelAgregacion("COMUNA");
        cuotaNueva.setPeriodo("MENSUAL");
        cuotaNueva.setLimiteKg(1500.0);
        cuotaNueva.setEspecie(especieHuiro);
        cuotaNueva.setExtraccionTipo(extraccionBuceo);
        cuotaNueva.setComunas(Set.of(comunaCoquimbo)); // Comparte Coquimbo
        cuotaNueva.setComuna(comunaCoquimbo);
        cuotaNueva.setFechaInicio(Date.valueOf(LocalDate.of(2026, 4, 1)));
        cuotaNueva.setFechaFin(Date.valueOf(LocalDate.of(2026, 4, 30)));
        cuotaNueva.setActivo(true);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaExistente));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.validarSolapamiento(cuotaNueva);
        });

        assertTrue(ex.getMessage().contains("ya tiene una cuota activa de Huiro Negro"));
        assertTrue(ex.getMessage().contains("cuota #11"));
    }

    @Test
    @DisplayName("T1.5: Dos cuotas en comunas distintas ({La Serena} y {Coquimbo}) en las mismas fechas NO tienen conflicto")
    void testSolapamiento_ComunasDisjuntas_MismasFechas_Permitido() {
        CuotaExtraccionModel cuotaLaSerena = new CuotaExtraccionModel();
        cuotaLaSerena.setId(13L);
        cuotaLaSerena.setNivelAgregacion("COMUNA");
        cuotaLaSerena.setPeriodo("MENSUAL");
        cuotaLaSerena.setLimiteKg(3000.0);
        cuotaLaSerena.setEspecie(especieHuiro);
        cuotaLaSerena.setExtraccionTipo(extraccionBuceo);
        cuotaLaSerena.setComuna(comunaLaSerena);
        cuotaLaSerena.setFechaInicio(Date.valueOf(LocalDate.of(2026, 4, 1)));
        cuotaLaSerena.setFechaFin(Date.valueOf(LocalDate.of(2026, 4, 30)));
        cuotaLaSerena.setActivo(true);

        CuotaExtraccionModel cuotaCoquimbo = new CuotaExtraccionModel();
        cuotaCoquimbo.setId(14L);
        cuotaCoquimbo.setNivelAgregacion("COMUNA");
        cuotaCoquimbo.setPeriodo("MENSUAL");
        cuotaCoquimbo.setLimiteKg(4000.0);
        cuotaCoquimbo.setEspecie(especieHuiro);
        cuotaCoquimbo.setExtraccionTipo(extraccionBuceo);
        cuotaCoquimbo.setComuna(comunaCoquimbo);
        cuotaCoquimbo.setFechaInicio(Date.valueOf(LocalDate.of(2026, 4, 1)));
        cuotaCoquimbo.setFechaFin(Date.valueOf(LocalDate.of(2026, 4, 30)));
        cuotaCoquimbo.setActivo(true);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaLaSerena));

        assertDoesNotThrow(() -> cuotaExtraccionService.validarSolapamiento(cuotaCoquimbo));
    }
}
