package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.models.AmerbModel;
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
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
public class CuotaAreaLibreConsumoTest {

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

    private CuotaExtraccionModel cuotaComunalBarreteado;
    private EspecieModel huiroPalo;
    private ComunaModel comunaCaldera;
    private ExtraccionTipoModel metodoBarreteado;
    private ExtraccionTipoModel metodoVarado;

    @BeforeEach
    void setUp() {
        cuotaService.invalidarCacheConsumo();

        huiroPalo = new EspecieModel();
        huiroPalo.setId(2L);
        huiroPalo.setNombre("Huiro Palo");

        RegionModel atacama = new RegionModel();
        atacama.setId(3L);
        atacama.setNombre("Atacama");

        ProvinciaModel copiapo = new ProvinciaModel();
        copiapo.setId(31L);
        copiapo.setNombre("Copiapó");
        copiapo.setRegion(atacama);

        comunaCaldera = new ComunaModel();
        comunaCaldera.setId(3102L);
        comunaCaldera.setNombre("Caldera");
        comunaCaldera.setProvincia(copiapo);
        comunaCaldera.setRegion(atacama);

        metodoBarreteado = new ExtraccionTipoModel();
        metodoBarreteado.setId(2L);
        metodoBarreteado.setNombre("Barreteado");

        metodoVarado = new ExtraccionTipoModel();
        metodoVarado.setId(1L);
        metodoVarado.setNombre("Varado");

        cuotaComunalBarreteado = new CuotaExtraccionModel();
        cuotaComunalBarreteado.setId(10L);
        cuotaComunalBarreteado.setAmbito("AREA_LIBRE");
        cuotaComunalBarreteado.setPerfil("RECOLECTOR"); // Deprecado pero presente
        cuotaComunalBarreteado.setEspecie(huiroPalo);
        cuotaComunalBarreteado.setComuna(comunaCaldera);
        cuotaComunalBarreteado.setExtraccionTipo(metodoBarreteado);
        cuotaComunalBarreteado.setNivelAgregacion("COMUNA");
        cuotaComunalBarreteado.setMetrica("DESEMBARQUE"); // Métrica en desembarque para test directo de kg
        cuotaComunalBarreteado.setPeriodo("DIARIO");
        cuotaComunalBarreteado.setLimiteKg(10000.0); // 10.000 kg límite
        cuotaComunalBarreteado.setModoAccion("SOLO_ALERTA");
        cuotaComunalBarreteado.setEstado("ABIERTA");
        cuotaComunalBarreteado.setActivo(true);
    }

    @Test
    @DisplayName("T3.2: Consumo combinado suma recolectores inscritos y armadores que desembarcan en la comuna (4.000 + 7.000 = 11.000 kg)")
    void testConsumoCombinadoRecolectorArmador() {
        Query queryRecolector = mock(Query.class);
        Query queryArmador = mock(Query.class);

        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(queryRecolector);
        when(entityManager.createNativeQuery(contains("declaracion_armador"))).thenReturn(queryArmador);

        when(queryRecolector.getSingleResult()).thenReturn(new BigDecimal("4000.00"));
        when(queryArmador.getSingleResult()).thenReturn(new BigDecimal("7000.00"));

        Date fecha = new Date();
        BigDecimal consumo = cuotaService.ejecutarConsultaConsumo(cuotaComunalBarreteado, fecha, null, null);

        // Assert: 4.000 kg recolector + 7.000 kg armador = 11.000 kg (110% de la cuota de 10.000 kg)
        assertEquals(new BigDecimal("11000.00"), consumo);
        verify(queryRecolector, times(1)).getSingleResult();
        verify(queryArmador, times(1)).getSingleResult();
    }

    @Test
    @DisplayName("T3.2: Armador desembarcando en otra comuna no suma a la cuota comunal (sólo suma recolector = 4.000 kg)")
    void testArmadorOtraComunaNoSuma() {
        Query queryRecolector = mock(Query.class);
        Query queryArmador = mock(Query.class);

        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(queryRecolector);
        when(entityManager.createNativeQuery(contains("declaracion_armador"))).thenReturn(queryArmador);

        // Recolector inscrito en Caldera: 4.000 kg
        when(queryRecolector.getSingleResult()).thenReturn(new BigDecimal("4000.00"));
        // Armador en otra comuna (la query con filtro territorial d.comuna_id = 3102 retorna 0)
        when(queryArmador.getSingleResult()).thenReturn(BigDecimal.ZERO);

        Date fecha = new Date();
        BigDecimal consumo = cuotaService.ejecutarConsultaConsumo(cuotaComunalBarreteado, fecha, null, null);

        assertEquals(new BigDecimal("4000.00"), consumo);
    }

    @Test
    @DisplayName("T3.5: Cuota con ámbito AMERB conserva comportamiento original de consulta a una sola tabla (declaracion_area)")
    void testAmerbConservaComportamientoOriginal() {
        AmerbModel amerb = new AmerbModel();
        amerb.setId(88L);
        amerb.setNombre("AMERB Punta Caldera");

        CuotaExtraccionModel cuotaAmerb = new CuotaExtraccionModel();
        cuotaAmerb.setId(20L);
        cuotaAmerb.setAmbito("AMERB");
        cuotaAmerb.setPerfil("AREA");
        cuotaAmerb.setAmerb(amerb);
        cuotaAmerb.setEspecie(huiroPalo);
        cuotaAmerb.setNivelAgregacion("AREA");
        cuotaAmerb.setMetrica("DESEMBARQUE");
        cuotaAmerb.setPeriodo("DIARIO");
        cuotaAmerb.setLimiteKg(15000.0);
        cuotaAmerb.setEstado("ABIERTA");
        cuotaAmerb.setActivo(true);

        Query queryArea = mock(Query.class);
        when(entityManager.createNativeQuery(contains("declaracion_area"))).thenReturn(queryArea);
        when(queryArea.getSingleResult()).thenReturn(new BigDecimal("12500.00"));

        Date fecha = new Date();
        BigDecimal consumo = cuotaService.ejecutarConsultaConsumo(cuotaAmerb, fecha, "AREA", null);

        assertEquals(new BigDecimal("12500.00"), consumo);
        // Debe consultar SOLO declaracion_area, NUNCA declaracion_recolector ni declaracion_armador
        verify(entityManager, times(1)).createNativeQuery(contains("declaracion_area"));
        verify(entityManager, never()).createNativeQuery(contains("declaracion_recolector"));
        verify(entityManager, never()).createNativeQuery(contains("declaracion_armador"));
    }

    @Test
    @DisplayName("T3.2: En SOLO_ALERTA la marca CUOTA_EXCEDIDA se genera en la segunda declaración que supera el límite combinado")
    void testEvaluacionDeclaracionGeneraCuotaExcedidaEnSegundaDeclaracion() {
        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaComunalBarreteado));
        lenient().when(configuracionGeneralService.getValor(eq("cuota_accion_exceso_limite"), anyString())).thenReturn("SOLO_ALERTA");
        lenient().when(comunaRepository.findById(3102L)).thenReturn(Optional.of(comunaCaldera));

        // Declaración 1: Recolector declara 4.000 kg. Consumo previo en base de datos es 0.
        Query qRec0 = mock(Query.class);
        Query qArm0 = mock(Query.class);
        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec0);
        when(entityManager.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm0);
        when(qRec0.getSingleResult()).thenReturn(BigDecimal.ZERO);
        when(qArm0.getSingleResult()).thenReturn(BigDecimal.ZERO);

        Date fecha = new Date();
        CuotaExtraccionService.EvaluacionCuotaResult res1 = cuotaService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 101L, null, 2L, 2L, 3102L, fecha, fecha, new BigDecimal("4000.00"), new BigDecimal("4000.00"));

        assertTrue(res1.isPermite());
        assertFalse(res1.isExcedeLimite());
        assertNull(res1.getMarca()); // No excede (4.000 <= 10.000)

        // Declaración 2: Armador declara 7.000 kg. Consumo acumulado ya registra los 4.000 kg del recolector.
        cuotaService.invalidarCacheConsumo();
        Query qRec1 = mock(Query.class);
        Query qArm1 = mock(Query.class);
        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec1);
        when(entityManager.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm1);
        when(qRec1.getSingleResult()).thenReturn(new BigDecimal("4000.00"));
        when(qArm1.getSingleResult()).thenReturn(BigDecimal.ZERO);

        CuotaExtraccionService.EvaluacionCuotaResult res2 = cuotaService.evaluarCuotaDeclaracion(
                "ARMADOR", 202L, null, 2L, 2L, 3102L, fecha, fecha, new BigDecimal("7000.00"), new BigDecimal("7000.00"));

        assertTrue(res2.isPermite(), "En SOLO_ALERTA la declaración se permite");
        assertTrue(res2.isExcedeLimite(), "La cuota combinada ha sido superada");
        assertEquals("CUOTA_EXCEDIDA", res2.getMarca(), "Se genera la marca CUOTA_EXCEDIDA en la segunda declaración");
        assertEquals(new BigDecimal("11000.00"), res2.getTotalAcumulado());
    }

    @Test
    @DisplayName("T3.3 & T3.4: getControlCuotasDiarioGlobal filtra por comunaId y extraccionTipoId, y retorna ámbito y método")
    void testDashboardDiarioFiltroComunaYMetodo() {
        CuotaExtraccionModel cuotaVarado = new CuotaExtraccionModel();
        cuotaVarado.setId(11L);
        cuotaVarado.setAmbito("AREA_LIBRE");
        cuotaVarado.setEspecie(huiroPalo);
        cuotaVarado.setComuna(comunaCaldera);
        cuotaVarado.setExtraccionTipo(metodoVarado);
        cuotaVarado.setNivelAgregacion("COMUNA");
        cuotaVarado.setPeriodo("DIARIO");
        cuotaVarado.setLimiteKg(5000.0);
        cuotaVarado.setActivo(true);

        when(cuotaRepository.findByAmbitoAndActivoTrue("AREA_LIBRE"))
                .thenReturn(List.of(cuotaComunalBarreteado, cuotaVarado));

        Query qRec = mock(Query.class);
        Query qArm = mock(Query.class);
        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);
        when(entityManager.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm);
        when(qRec.getSingleResult()).thenReturn(new BigDecimal("1000.00"));
        when(qArm.getSingleResult()).thenReturn(new BigDecimal("2000.00"));

        Date fecha = new Date();
        // Filtrar sólo por Barreteado (ID 2L)
        List<ControlCuotaDiariaDTO> dtos = cuotaService.getControlCuotasDiarioGlobal(
                fecha, fecha, null, 3102L, 2L);

        assertNotNull(dtos);
        assertEquals(1, dtos.size(), "Sólo debe retornar la cuota de Barreteado");
        ControlCuotaDiariaDTO dto = dtos.get(0);
        assertEquals(10L, dto.getCuotaId());
        assertEquals("AREA_LIBRE", dto.getAmbito());
        assertEquals("Barreteado", dto.getExtraccionTipoNombre());
        assertEquals("Caldera", dto.getComunaNombre());
        assertEquals(new BigDecimal("3000.00"), dto.getVolumenExtraido());
    }
}
