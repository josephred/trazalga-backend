package com.trazalga.api.revision09oct;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

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
 * Revisión 12-10 · Aceptación de TC.4 tal como la escribe el plan del 09-10:
 * «Cuota de marzo, vencida: una extracción del 01-04 cae en la cuota de abril y no recibe marca de cierre.»
 * Parámetros del plan: cuota_dias_gracia_declaracion = 0 y cuota_cierre_automatico_vencimiento = true.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevisionTC4CierreEntreMesesTest {

    @Mock private ICuotaExtraccionRepository cuotaRepository;
    @Mock private IComunaRepository comunaRepository;
    @Mock private IAvisoEnviadoRepository avisoEnviadoRepository;
    @Mock private ConfiguracionGeneralService configuracionGeneralService;
    @Mock private AmerbEspecieHabilitadaService amerbEspecieHabilitadaService;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CuotaExtraccionService cuotaExtraccionService;

    private EspecieModel huiro;
    private ExtraccionTipoModel varado;
    private ComunaModel laSerena;

    @BeforeEach
    void setUp() {
        huiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        varado = ExtraccionTipoModel.builder().id(10L).nombre("Varado").build();
        RegionModel coquimbo = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        laSerena = ComunaModel.builder().id(4101L).nombre("La Serena").region(coquimbo).build();

        when(configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION")).thenReturn("EXTRACCION");
        when(configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false)).thenReturn(true);
        when(configuracionGeneralService.getInt("cuota_dias_gracia_declaracion", 0)).thenReturn(0);
        when(configuracionGeneralService.getDouble("cuota_umbral_restante_pct", 10.0)).thenReturn(10.0);
        when(configuracionGeneralService.getValor("cuota_accion_post_cierre", "ALERTA_CRITICA")).thenReturn("ALERTA_CRITICA");
        when(configuracionGeneralService.getValor("cuota_accion_extemporanea", "ALERTA_CRITICA")).thenReturn("ALERTA_CRITICA");
        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(laSerena));

        Query q = mock(Query.class);
        when(q.getSingleResult()).thenReturn("1000.00");
        when(entityManager.createNativeQuery(anyString())).thenReturn(q);
    }

    private CuotaExtraccionModel cuotaMensual(long id, int mes) {
        java.time.LocalDate ini = java.time.LocalDate.of(2026, mes, 1);
        return CuotaExtraccionModel.builder()
                .id(id).ambito("AREA_LIBRE").nivelAgregacion("COMUNA").comuna(laSerena)
                .perfil("RECOLECTOR").especie(huiro).extraccionTipo(varado)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(ini)).fechaFin(Date.valueOf(ini.withDayOfMonth(ini.lengthOfMonth())))
                .limiteKg(50_000.0).metrica("CAPTURA").modoAccion("SOLO_ALERTA").activo(true)
                .build();
    }

    private long marcasDeCierre(EvaluacionCuotaResult r) {
        return r.getMarcas().stream()
                .filter(m -> "POSTERIOR_CIERRE".equals(m.getMarca()) || "DECLARACION_EXTEMPORANEA".equals(m.getMarca()))
                .count();
    }

    @Test
    @DisplayName("Plan TC.4: extracción del 01-04 declarada el 01-04 no recibe marca de cierre por la cuota de marzo")
    void extraccionDeAbrilNoRecibeMarcaDeCierrePorMarzo() {
        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaMensual(301L, 3), cuotaMensual(304L, 4)));

        EvaluacionCuotaResult r = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                Date.valueOf("2026-04-01"), Date.valueOf("2026-04-01"),
                new BigDecimal("100"), new BigDecimal("113"));

        r.getMarcas().forEach(m -> System.out.println("REVISION_TC4 abril -> " + m.getMarca() + " regla=" + m.getReglaId() + " | " + m.getDetalle()));
        assertEquals(0, marcasDeCierre(r), "El plan exige 0 marcas de cierre para una extracción del 01-04");
    }

    @Test
    @DisplayName("Plan TC.4: en julio, las cuotas de enero a junio vencidas no deben marcar una extracción de julio")
    void extraccionDeJulioNoRecibeMarcasPorMesesAnteriores() {
        List<CuotaExtraccionModel> cuotas = new ArrayList<>();
        for (int mes = 1; mes <= 7; mes++) cuotas.add(cuotaMensual(400L + mes, mes));
        when(cuotaRepository.findByActivoTrue()).thenReturn(cuotas);

        EvaluacionCuotaResult r = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                Date.valueOf("2026-07-15"), Date.valueOf("2026-07-15"),
                new BigDecimal("100"), new BigDecimal("113"));

        System.out.println("REVISION_TC4 julio -> marcas de cierre: " + marcasDeCierre(r) + " de " + r.getMarcas().size() + " marcas");
        assertEquals(0, marcasDeCierre(r), "Una extracción de julio no debe recibir marcas por cuotas de meses anteriores");
    }

    @Test
    @DisplayName("Sin cierre por vencimiento: una cuota de marzo cerrada por agotamiento no marca una extracción de abril")
    void cuotaCerradaPorAgotamientoNoMarcaElMesSiguiente() {
        when(configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false)).thenReturn(false);
        CuotaExtraccionModel marzo = cuotaMensual(501L, 3);
        marzo.cerrar("AGOTAMIENTO", Date.valueOf("2026-03-25"), null);
        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(marzo, cuotaMensual(504L, 4)));

        EvaluacionCuotaResult r = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 1L, null, 1L, 10L, 4101L,
                Date.valueOf("2026-04-10"), Date.valueOf("2026-04-10"),
                new BigDecimal("100"), new BigDecimal("113"));

        System.out.println("REVISION_TC4 agotamiento -> marcas de cierre: " + marcasDeCierre(r));
        assertEquals(0, marcasDeCierre(r), "La cuota de marzo no aplica a una extracción del 10-04");
    }
}
