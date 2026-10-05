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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.dto.CuotaListadoDTO;
import com.trazalga.api.models.AmerbModel;
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

@ExtendWith(MockitoExtension.class)
public class CuotaListadoTest {

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
    private ComunaModel comunaCoquimbo;
    private EspecieModel especieHuiro;
    private ExtraccionTipoModel extraccionBuceo;
    private ExtraccionTipoModel extraccionVarado;

    @BeforeEach
    void setUp() {
        cuotaService.invalidarCacheConsumo();

        regionCoquimbo = new RegionModel();
        regionCoquimbo.setId(4L);
        regionCoquimbo.setNombre("Coquimbo");

        comunaLaSerena = new ComunaModel();
        comunaLaSerena.setId(4101L);
        comunaLaSerena.setNombre("La Serena");
        comunaLaSerena.setRegion(regionCoquimbo);

        comunaCoquimbo = new ComunaModel();
        comunaCoquimbo.setId(4102L);
        comunaCoquimbo.setNombre("Coquimbo");
        comunaCoquimbo.setRegion(regionCoquimbo);

        especieHuiro = new EspecieModel();
        especieHuiro.setId(1L);
        especieHuiro.setNombre("Huiro Negro");

        extraccionBuceo = new ExtraccionTipoModel();
        extraccionBuceo.setId(1L);
        extraccionBuceo.setNombre("Buceo");

        extraccionVarado = new ExtraccionTipoModel();
        extraccionVarado.setId(2L);
        extraccionVarado.setNombre("Varado");

        lenient().when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        lenient().when(comunaRepository.findById(4102L)).thenReturn(Optional.of(comunaCoquimbo));
        lenient().when(regionRepository.findById(4L)).thenReturn(Optional.of(regionCoquimbo));
        lenient().when(especieRepository.findById(1L)).thenReturn(Optional.of(especieHuiro));
        lenient().when(extraccionTipoRepository.findById(1L)).thenReturn(Optional.of(extraccionBuceo));
        lenient().when(extraccionTipoRepository.findById(2L)).thenReturn(Optional.of(extraccionVarado));
    }

    @Test
    @DisplayName("T1.2: Para cuota AREA_LIBRE el perfil se fija en RECOLECTOR como columna heredada")
    void testValidarDatosBasicos_AreaLibre_FijaPerfilRecolector() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setAmbito("AREA_LIBRE");
        c.setPerfil("ARMADOR"); // Intenta poner ARMADOR
        c.setNivelAgregacion("COMUNA");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(50000.0);
        c.setComuna(comunaLaSerena);
        c.setActivo(false);

        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(i -> i.getArgument(0));

        CuotaExtraccionModel guardada = cuotaService.save(c);
        assertEquals("RECOLECTOR", guardada.getPerfil(), "En AREA_LIBRE el perfil debe ser el marcador RECOLECTOR");
    }

    @Test
    @DisplayName("T1.3: getListado filtra correctamente por año, mes, comuna y ámbito")
    void testGetListado_FiltraCorrectamente() {
        // Cuota 1: Marzo 2026, La Serena + Coquimbo, Buceo
        CuotaExtraccionModel c1 = new CuotaExtraccionModel();
        c1.setId(101L);
        c1.setAmbito("AREA_LIBRE");
        c1.setNivelAgregacion("COMUNA");
        c1.setComunas(Set.of(comunaLaSerena, comunaCoquimbo));
        c1.setComuna(comunaLaSerena);
        c1.setEspecie(especieHuiro);
        c1.setExtraccionTipo(extraccionBuceo);
        c1.setPeriodo("MENSUAL");
        c1.setFechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)));
        c1.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)));
        c1.setLimiteKg(100000.0);
        c1.setActivo(true);
        c1.setEstado("ABIERTA");

        // Cuota 2: Abril 2026, La Serena, Varado
        CuotaExtraccionModel c2 = new CuotaExtraccionModel();
        c2.setId(102L);
        c2.setAmbito("AREA_LIBRE");
        c2.setNivelAgregacion("COMUNA");
        c2.setComuna(comunaLaSerena);
        c2.setEspecie(especieHuiro);
        c2.setExtraccionTipo(extraccionVarado);
        c2.setPeriodo("MENSUAL");
        c2.setFechaInicio(Date.valueOf(LocalDate.of(2026, 4, 1)));
        c2.setFechaFin(Date.valueOf(LocalDate.of(2026, 4, 30)));
        c2.setLimiteKg(80000.0);
        c2.setActivo(true);
        c2.setEstado("ABIERTA");

        // Cuota 3: AMERB (debe ocultarse con ámbito AREA_LIBRE)
        CuotaExtraccionModel cAmerb = new CuotaExtraccionModel();
        cAmerb.setId(103L);
        cAmerb.setAmbito("AMERB");
        cAmerb.setAmerb(new AmerbModel());
        cAmerb.setLimiteKg(30000.0);
        cAmerb.setPeriodo("ANUAL");

        // Cuota 4: Formato anterior (sin fechas, anual)
        CuotaExtraccionModel cAntigua = new CuotaExtraccionModel();
        cAntigua.setId(104L);
        cAntigua.setAmbito("AREA_LIBRE");
        cAntigua.setNivelAgregacion("COMUNA");
        cAntigua.setComuna(comunaLaSerena);
        cAntigua.setPeriodo("ANUAL"); // No mensual
        cAntigua.setLimiteKg(50000.0);

        when(cuotaRepository.findAll()).thenReturn(List.of(c1, c2, cAmerb, cAntigua));

        // Mock para consultas de consumo nativo
        Query mockQuery = mock(Query.class);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        lenient().when(mockQuery.getSingleResult()).thenReturn(new BigDecimal("15000.00"));

        // 1. Filtrar año 2026, mes 3 (Marzo), ámbito AREA_LIBRE
        List<CuotaListadoDTO> resMarzo = cuotaService.getListado(2026, 3, null, null, "AREA_LIBRE");
        assertNotNull(resMarzo);
        // Debe traer c1 (Marzo) y cAntigua (formato anterior sin fechas fijas), pero no c2 (Abril) ni cAmerb
        assertEquals(2, resMarzo.size());

        CuotaListadoDTO dtoC1 = resMarzo.stream().filter(d -> d.getId().equals(101L)).findFirst().orElseThrow();
        assertEquals("AREA_LIBRE", dtoC1.getAmbito());
        assertEquals("Huiro Negro", dtoC1.getEspecieNombre());
        assertEquals("Buceo", dtoC1.getExtraccionTipoNombre());
        assertEquals("Marzo 2026", dtoC1.getVigenciaDescripcion());
        assertFalse(dtoC1.getEsFormatoAnterior());
        assertEquals(100000.0, dtoC1.getLimiteKg());
        assertTrue(dtoC1.getComunaIds().contains(4101L));
        assertTrue(dtoC1.getComunaIds().contains(4102L));
        assertTrue(dtoC1.getComunasNombre().contains("La Serena"));
        assertTrue(dtoC1.getComunasNombre().contains("Coquimbo"));

        CuotaListadoDTO dtoAnt = resMarzo.stream().filter(d -> d.getId().equals(104L)).findFirst().orElseThrow();
        assertTrue(dtoAnt.getEsFormatoAnterior(), "Cuota anual sin fechas debe marcarse como formato anterior");

        // 2. Filtrar por ámbito AMERB
        List<CuotaListadoDTO> resAmerb = cuotaService.getListado(null, null, null, null, "AMERB");
        assertEquals(1, resAmerb.size());
        assertEquals(103L, resAmerb.get(0).getId());

        // 3. Filtrar por método Buceo (id=1)
        List<CuotaListadoDTO> resBuceo = cuotaService.getListado(2026, null, null, 1L, "AREA_LIBRE");
        assertEquals(1, resBuceo.size());
        assertEquals(101L, resBuceo.get(0).getId());
    }

    @Test
    @DisplayName("T1.3: Formateo de vigencia descriptiva")
    void testFormatearVigencia() {
        CuotaExtraccionModel cMesCompleto = new CuotaExtraccionModel();
        cMesCompleto.setPeriodo("MENSUAL");
        cMesCompleto.setFechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)));
        cMesCompleto.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)));

        when(cuotaRepository.findAll()).thenReturn(List.of(cMesCompleto));
        List<CuotaListadoDTO> res1 = cuotaService.getListado(null, null, null, null, "TODOS");
        assertEquals("Marzo 2026", res1.get(0).getVigenciaDescripcion());

        CuotaExtraccionModel cMesAcotado = new CuotaExtraccionModel();
        cMesAcotado.setPeriodo("MENSUAL");
        cMesAcotado.setFechaInicio(Date.valueOf(LocalDate.of(2026, 3, 10)));
        cMesAcotado.setFechaFin(Date.valueOf(LocalDate.of(2026, 3, 25)));

        when(cuotaRepository.findAll()).thenReturn(List.of(cMesAcotado));
        List<CuotaListadoDTO> res2 = cuotaService.getListado(null, null, null, null, "TODOS");
        assertEquals("Marzo 2026 (10 al 25)", res2.get(0).getVigenciaDescripcion());
    }

    @Test
    @DisplayName("T1.7: Con enero a diciembre de 2026 cargados, el tablero del 15-10 muestra sólo las cuotas de octubre")
    void testTablero_MuestraSoloCuotasDeOctubre_CuandoSeFiltraAl15DeOctubre() {
        List<CuotaExtraccionModel> doceMeses = new java.util.ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate ini = LocalDate.of(2026, m, 1);
            LocalDate fin = ini.withDayOfMonth(ini.lengthOfMonth());
            doceMeses.add(CuotaExtraccionModel.builder()
                    .id((long) m)
                    .ambito("AREA_LIBRE")
                    .nivelAgregacion("COMUNA")
                    .comunas(Set.of(comunaLaSerena))
                    .especie(especieHuiro)
                    .extraccionTipo(extraccionVarado)
                    .periodo("MENSUAL")
                    .fechaInicio(Date.valueOf(ini))
                    .fechaFin(Date.valueOf(fin))
                    .limiteKg(10_000.0)
                    .metrica("DESEMBARQUE")
                    .activo(true)
                    .build());
        }

        when(cuotaRepository.findByAmbitoAndActivoTrue("AREA_LIBRE")).thenReturn(doceMeses);

        Query mockQuery = mock(Query.class);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        lenient().when(mockQuery.getSingleResult()).thenReturn(new BigDecimal("1000.00"));

        Date fechaTablero = Date.valueOf("2026-10-15");
        List<ControlCuotaDiariaDTO> dtos = cuotaService.getControlCuotasDiarioGlobal(
                fechaTablero, fechaTablero, null, null, null, null);

        assertNotNull(dtos);
        assertEquals(1, dtos.size(), "El tablero del 15-10 debe mostrar exactamente una cuota");
        ControlCuotaDiariaDTO dtoOct = dtos.get(0);
        assertEquals(10L, dtoOct.getCuotaId(), "La cuota mostrada debe ser la de octubre (#10)");
        assertEquals("Octubre 2026", dtoOct.getVigenciaFormateada());
        assertTrue(dtoOct.getComunasNombre().contains("La Serena"));
    }
}
