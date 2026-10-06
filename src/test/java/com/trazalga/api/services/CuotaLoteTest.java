package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.CuotaLoteDTO;
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
import com.trazalga.api.services.AmerbEspecieHabilitadaService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.FactorConversionService;
import com.trazalga.api.services.MacrozonaService;
import jakarta.persistence.EntityManager;

/**
 * T1.8: Pruebas unitarias para Alta Anual y Lote Transaccional de Cuotas.
 */
@ExtendWith(MockitoExtension.class)
public class CuotaLoteTest {

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
    private ExtraccionTipoModel metodoVarado;

    @BeforeEach
    void setUp() {
        regionCoquimbo = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        comunaLaSerena = ComunaModel.builder().id(4101L).nombre("La Serena").region(regionCoquimbo).build();
        comunaCoquimbo = ComunaModel.builder().id(4102L).nombre("Coquimbo").region(regionCoquimbo).build();
        especieHuiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        metodoVarado = ExtraccionTipoModel.builder().id(10L).nombre("Varado").build();

        lenient().when(regionRepository.findById(4L)).thenReturn(Optional.of(regionCoquimbo));
        lenient().when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        lenient().when(comunaRepository.findById(4102L)).thenReturn(Optional.of(comunaCoquimbo));
        lenient().when(especieRepository.findById(1L)).thenReturn(Optional.of(especieHuiro));
        lenient().when(extraccionTipoRepository.findById(10L)).thenReturn(Optional.of(metodoVarado));
    }

    private CuotaLoteDTO buildLoteAnual2026() {
        List<CuotaLoteDTO.CuotaMesItemDTO> meses = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            meses.add(CuotaLoteDTO.CuotaMesItemDTO.builder()
                    .mes(m)
                    .limiteKg(100_000.0)
                    .activo(true)
                    .build());
        }

        return CuotaLoteDTO.builder()
                .anio(2026)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .regionId(4L)
                .comunaIds(Set.of(4101L, 4102L))
                .especieId(1L)
                .extraccionTipoId(10L)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .resolucion("Res. Ex. 100/2026")
                .meses(meses)
                .build();
    }

    @Test
    @DisplayName("T1.8: Alta anual genera 12 cuotas consecutivas sin solapes para Coquimbo y La Serena")
    void testAltaAnual_Genera12CuotasConsecutivas_Exitosamente() {
        when(cuotaRepository.findByActivoTrue()).thenReturn(Collections.emptyList());
        when(cuotaRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CuotaLoteDTO req = buildLoteAnual2026();
        List<CuotaExtraccionModel> resultado = cuotaService.saveLote(req);

        assertNotNull(resultado);
        assertEquals(12, resultado.size(), "Deben haberse generado exactamente 12 cuotas");

        for (int m = 1; m <= 12; m++) {
            CuotaExtraccionModel c = resultado.get(m - 1);
            assertEquals("AREA_LIBRE", c.getAmbito());
            assertEquals("RECOLECTOR", c.getPerfil());
            assertEquals("MENSUAL", c.getPeriodo());
            assertEquals(100_000.0, c.getLimiteKg());
            assertEquals(2, c.getComunas().size());

            LocalDate esperadoInicio = LocalDate.of(2026, m, 1);
            LocalDate esperadoFin = esperadoInicio.withDayOfMonth(esperadoInicio.lengthOfMonth());

            assertEquals(Date.valueOf(esperadoInicio), c.getFechaInicio());
            assertEquals(Date.valueOf(esperadoFin), c.getFechaFin());
        }

        verify(cuotaRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("T1.8: Si el mes de marzo choca con una cuota existente en la BD, no se guarda ninguno y se indica marzo")
    void testAltaAnual_CuandoMarzoChocaConCuotaExistente_FallaYNoGuardaNinguno() {
        // Cuota existente activa en marzo para La Serena, Huiro negro, Varado
        CuotaExtraccionModel cuotaMarzoExistente = CuotaExtraccionModel.builder()
                .id(999L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comunas(Set.of(comunaLaSerena))
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)))
                .fechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)))
                .limiteKg(50_000.0)
                .activo(true)
                .build();

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaMarzoExistente));

        CuotaLoteDTO req = buildLoteAnual2026();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaService.saveLote(req);
        });

        // Debe señalar el mes que falló
        assertTrue(ex.getMessage().toLowerCase().contains("marzo"),
                "El mensaje debe indicar explícitamente que el error ocurrió en marzo: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("La Serena"),
                "El mensaje debe detallar la comuna en conflicto: " + ex.getMessage());

        // Verificación transaccional: NINGUNA cuota fue persistida
        verify(cuotaRepository, never()).saveAll(anyList());
        verify(cuotaRepository, never()).save(any(CuotaExtraccionModel.class));
    }

    @Test
    @DisplayName("T1.8: Conflicto interno dentro del lote lanza excepción antes de tocar la BD")
    void testLote_ConflictoInternoEnLote_LanzaExcepcion() {
        // Dos cuotas de marzo en el mismo lote para la misma comuna y especie
        CuotaExtraccionModel c1 = CuotaExtraccionModel.builder()
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comunas(Set.of(comunaLaSerena))
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .fechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)))
                .fechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)))
                .limiteKg(10_000.0)
                .activo(true)
                .build();

        CuotaExtraccionModel c2 = CuotaExtraccionModel.builder()
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comunas(Set.of(comunaLaSerena))
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .fechaInicio(Date.valueOf(LocalDate.of(2026, 3, 15)))
                .fechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)))
                .limiteKg(20_000.0)
                .activo(true)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaService.saveLote(List.of(c1, c2));
        });

        assertTrue(ex.getMessage().contains("Conflicto interno en el lote"),
                "Debe detectar conflicto interno: " + ex.getMessage());
        verify(cuotaRepository, never()).saveAll(anyList());
    }
}
