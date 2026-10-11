package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.events.CuotaUmbralAlcanzado;
import com.trazalga.api.models.AvisoEnviadoModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.services.AmerbEspecieHabilitadaService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.CuotaExtraccionService.EvaluacionCuotaResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * TC.4: Pruebas unitarias para marcas por cierre de cuota (K13 / D4).
 * Verifica separación estricta de POSTERIOR_CIERRE vs DECLARACION_EXTEMPORANEA,
 * marcas individuales con reglaId por cuota, criterios estructurados y alerta en tiempo real.
 */
@ExtendWith(MockitoExtension.class)
public class CuotaCierreMarcasTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private IComunaRepository comunaRepository;

    @Mock
    private IAvisoEnviadoRepository avisoEnviadoRepository;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private AmerbEspecieHabilitadaService amerbEspecieHabilitadaService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CuotaExtraccionService cuotaExtraccionService;

    private EspecieModel especieHuiro;
    private ExtraccionTipoModel metodoBarreteado;
    private RegionModel regionCoquimbo;
    private ComunaModel comunaLaSerena;

    @BeforeEach
    void setUp() {
        especieHuiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        metodoBarreteado = ExtraccionTipoModel.builder().id(10L).nombre("Barreteado").build();
        regionCoquimbo = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        comunaLaSerena = ComunaModel.builder().id(4101L).nombre("La Serena").region(regionCoquimbo).build();

        lenient().when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION")).thenReturn("EXTRACCION");
        lenient().when(configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false)).thenReturn(true);
        lenient().when(configuracionGeneralService.getInt("cuota_dias_gracia_declaracion", 0)).thenReturn(5);
        lenient().when(configuracionGeneralService.getDouble("cuota_umbral_restante_pct", 10.0)).thenReturn(10.0);
        lenient().when(configuracionGeneralService.getValor("cuota_accion_post_cierre", "ALERTA_CRITICA")).thenReturn("ALERTA_CRITICA");
        lenient().when(configuracionGeneralService.getValor("cuota_accion_extemporanea", "ALERTA_CRITICA")).thenReturn("ALERTA_CRITICA");
        lenient().when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
    }

    @Test
    @DisplayName("TC.4 Escenario A: Extracción posterior al cierre de cuota genera POSTERIOR_CIERRE con criterio estructurado")
    void testExtraccionPosteriorAlCierre_GeneraPosteriorCierre() {
        // Cuota de Marzo 2026 cerrada el 31-03-2026
        CuotaExtraccionModel cuotaMarzo = CuotaExtraccionModel.builder()
                .id(100L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comuna(comunaLaSerena)
                .perfil("RECOLECTOR")
                .especie(especieHuiro)
                .extraccionTipo(metodoBarreteado)
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(50_000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .activo(true)
                .build();
        cuotaMarzo.cerrar("VENCIMIENTO", Date.valueOf("2026-03-31"), null);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaMarzo));

        Query qConsumo = mock(Query.class);
        when(qConsumo.getSingleResult()).thenReturn("10000.00");
        when(entityManager.createNativeQuery(anyString())).thenReturn(qConsumo);

        // Extracción el 10 de abril (posterior al cierre del 31 de marzo)
        java.util.Date fechaExt = Date.valueOf("2026-04-10");
        java.util.Date fechaDec = Date.valueOf("2026-04-10");

        EvaluacionCuotaResult res = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                fechaExt, fechaDec, new BigDecimal("1000"), new BigDecimal("1000"));

        assertTrue(res.isPosteriorCierre());
        assertFalse(res.isDeclaracionExtemporanea());

        List<MarcaItem> marcas = res.getMarcas();
        assertFalse(marcas.isEmpty());
        MarcaItem marcaPostCierre = marcas.stream().filter(m -> "POSTERIOR_CIERRE".equals(m.getMarca())).findFirst().orElse(null);
        assertNotNull(marcaPostCierre);
        assertEquals(100L, marcaPostCierre.getReglaId());
        assertNotNull(marcaPostCierre.getCriterio());
        assertEquals("fecha", marcaPostCierre.getCriterio().unidad());
        assertEquals("fecha_cierre", marcaPostCierre.getCriterio().parametro());
        assertEquals("2026-03-31", marcaPostCierre.getCriterio().umbral());
        assertEquals("2026-04-10", marcaPostCierre.getCriterio().valorObservado());
    }

    @Test
    @DisplayName("TC.4 Escenario B: Extracción dentro de cuota pero declarada fuera de gracia genera DECLARACION_EXTEMPORANEA")
    void testExtraccionEnPlazo_DeclaracionFueraDeGracia_GeneraExtemporanea() {
        // Cuota cerrada administrativamente el 20 de marzo con gracia de 5 días (plazo hasta 25 de marzo)
        CuotaExtraccionModel cuotaCerrada = CuotaExtraccionModel.builder()
                .id(200L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comuna(comunaLaSerena)
                .perfil("RECOLECTOR")
                .especie(especieHuiro)
                .extraccionTipo(metodoBarreteado)
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(50_000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .activo(true)
                .build();
        cuotaCerrada.cerrar("ADMINISTRATIVO", Date.valueOf("2026-03-20"), 1L);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaCerrada));

        Query qConsumo = mock(Query.class);
        when(qConsumo.getSingleResult()).thenReturn("5000.00");
        when(entityManager.createNativeQuery(anyString())).thenReturn(qConsumo);

        // Extracción el 15 de marzo (en plazo, antes del 20), pero ingresada el 28 de marzo (después del 25)
        java.util.Date fechaExt = Date.valueOf("2026-03-15");
        java.util.Date fechaDec = Date.valueOf("2026-03-28");

        EvaluacionCuotaResult res = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                fechaExt, fechaDec, new BigDecimal("1000"), new BigDecimal("1000"));

        assertFalse(res.isPosteriorCierre(), "No debe marcar posterior al cierre si la extracción fue el 15-marzo <= 20-marzo");
        assertTrue(res.isDeclaracionExtemporanea(), "Debe marcar declaración extemporánea");

        List<MarcaItem> marcas = res.getMarcas();
        MarcaItem marcaExtemp = marcas.stream().filter(m -> "DECLARACION_EXTEMPORANEA".equals(m.getMarca())).findFirst().orElse(null);
        assertNotNull(marcaExtemp);
        assertEquals(200L, marcaExtemp.getReglaId());
        assertNotNull(marcaExtemp.getCriterio());
        assertEquals("fecha", marcaExtemp.getCriterio().unidad());
        assertEquals("fecha_cierre", marcaExtemp.getCriterio().parametro());
        assertEquals("2026-03-25", marcaExtemp.getCriterio().umbral()); // 20-marzo + 5 días gracia
        assertEquals("2026-03-28", marcaExtemp.getCriterio().valorObservado());
    }

    @Test
    @DisplayName("TC.4 Escenario C: Dos cuotas excedidas generan dos marcas independientes con sus respectivos reglaId")
    void testDosCuotasExcedidas_GeneraDosMarcasConReglaIdDistintos() {
        CuotaExtraccionModel cComunal = CuotaExtraccionModel.builder()
                .id(301L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comuna(comunaLaSerena)
                .perfil("RECOLECTOR")
                .especie(especieHuiro)
                .extraccionTipo(metodoBarreteado)
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(10_000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .activo(true)
                .build();

        CuotaExtraccionModel cRegional = CuotaExtraccionModel.builder()
                .id(302L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("REGION")
                .region(regionCoquimbo)
                .perfil("RECOLECTOR")
                .especie(especieHuiro)
                .extraccionTipo(metodoBarreteado)
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(40_000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .activo(true)
                .build();

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cComunal, cRegional));

        Query qConsumo = mock(Query.class);
        // Retornar 9.500 kg (9500 + 0) para comunal y 39.500 kg (39500 + 0) para regional
        when(qConsumo.getSingleResult()).thenReturn("9500.00").thenReturn("0.00").thenReturn("39500.00").thenReturn("0.00");
        when(entityManager.createNativeQuery(anyString())).thenReturn(qConsumo);

        // Nueva declaración de 1.000 kg -> Comunal llega a 10.500 kg (> 10.000) y Regional a 40.500 kg (> 40.000)
        java.util.Date fecha = Date.valueOf("2026-03-15");
        EvaluacionCuotaResult res = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                fecha, fecha, new BigDecimal("1000"), new BigDecimal("1000"));

        assertTrue(res.isExcedeLimite());
        List<MarcaItem> marcas = res.getMarcas();
        assertEquals(2, marcas.size(), "Debe generar exactamente dos marcas CUOTA_EXCEDIDA");

        assertTrue(marcas.stream().anyMatch(m -> Long.valueOf(301L).equals(m.getReglaId())));
        assertTrue(marcas.stream().anyMatch(m -> Long.valueOf(302L).equals(m.getReglaId())));
    }

    @Test
    @DisplayName("TC.4 / TC.6 Escenario D: Cruce de umbral en tiempo real dispara CuotaUmbralAlcanzado y deduplica con aviso_enviado")
    void testCruceUmbralTiempoReal_DisparaEventoSinDuplicar() {
        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .id(400L)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .comuna(comunaLaSerena)
                .perfil("RECOLECTOR")
                .especie(especieHuiro)
                .extraccionTipo(metodoBarreteado)
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(100_000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .activo(true)
                .build();

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuota));

        Query qConsumo = mock(Query.class);
        when(qConsumo.getSingleResult()).thenReturn("91000.00").thenReturn("0.00"); // 91% de consumo (umbral restante = 10% -> disparo en >= 90%)
        when(entityManager.createNativeQuery(anyString())).thenReturn(qConsumo);

        when(avisoEnviadoRepository.existsById("CUOTA:400:UMBRAL:1")).thenReturn(false);

        java.util.Date fecha = Date.valueOf("2026-03-15");
        cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                fecha, fecha, new BigDecimal("1000"), new BigDecimal("1000"));

        verify(avisoEnviadoRepository).save(any(AvisoEnviadoModel.class));
        verify(eventPublisher).publishEvent(any(CuotaUmbralAlcanzado.class));
    }
}
