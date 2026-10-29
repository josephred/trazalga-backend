package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService.ContextoImputacion;
import com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService.Estado;
import com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService.Resultado;
import com.trazalga.api.services.parametros.ParametrosImputacionVeda;
import com.trazalga.api.services.parametros.ParametrosService;

/**
 * Refinamiento 23-10 · punto 1: imputación cruzada por veda (unitarias).
 * Barreteado en veda sin cuota de barreteado -> cuota de varado de la misma especie, comuna y período.
 */
class ImputacionCruzadaVedaServiceTest {

    private static final long VARADO = 1L;
    private static final long BARRETEADO = 2L;
    private static final long ESPECIE = 7L;
    private static final long COMUNA = 4101L;
    private static final Date FECHA = Date.valueOf("2026-10-15");

    private ImputacionCruzadaVedaService service;
    private CuotaExtraccionService cuotaService;
    private ParametrosService parametrosService;

    private CuotaExtraccionModel cuotaVarado;

    @BeforeEach
    void setUp() {
        service = new ImputacionCruzadaVedaService();
        cuotaService = mock(CuotaExtraccionService.class);
        parametrosService = mock(ParametrosService.class);
        IExtraccionTipoRepository extraccionTipoRepository = mock(IExtraccionTipoRepository.class);

        when(extraccionTipoRepository.findAll()).thenReturn(List.of(
                ExtraccionTipoModel.builder().id(VARADO).nombre("Varado").build(),
                ExtraccionTipoModel.builder().id(BARRETEADO).nombre("Barreteado").build()));
        when(parametrosService.imputacionVeda()).thenReturn(new ParametrosImputacionVeda(true, "BARRETEADO", "VARADO"));

        cuotaVarado = new CuotaExtraccionModel();
        cuotaVarado.setId(12L);
        when(cuotaService.describirAlcance(cuotaVarado)).thenReturn("Comuna Coquimbo");
        when(cuotaService.calcularEspecificidadCuota(any())).thenReturn(16);

        ReflectionTestUtils.setField(service, "cuotaExtraccionService", cuotaService);
        ReflectionTestUtils.setField(service, "extraccionTipoRepository", extraccionTipoRepository);
        ReflectionTestUtils.setField(service, "parametrosService", parametrosService);
    }

    private ContextoImputacion ctx(Long metodoId, boolean enVeda) {
        return ContextoImputacion.builder()
                .perfil("RECOLECTOR")
                .usuarioId(55L)
                .especieId(ESPECIE)
                .extraccionTipoId(metodoId)
                .comunaImputacionId(COMUNA)
                .fechaExtraccion(FECHA)
                .fechaDeclaracion(FECHA)
                .enVeda(enVeda)
                .build();
    }

    private void cuotasPorMetodo(List<CuotaExtraccionModel> barreteado, List<CuotaExtraccionModel> varado) {
        when(cuotaService.cuotasAplicables(eq("RECOLECTOR"), eq(55L), isNull(), eq(ESPECIE), eq(BARRETEADO), eq(COMUNA), any(), any()))
                .thenReturn(barreteado);
        when(cuotaService.cuotasAplicables(eq("RECOLECTOR"), eq(55L), isNull(), eq(ESPECIE), eq(VARADO), eq(COMUNA), any(), any()))
                .thenReturn(varado);
    }

    @Test
    @DisplayName("Barreteado en veda sin cuota de barreteado: se imputa a la cuota de varado de la comuna y el mes")
    void barreteadoEnVeda_sinCuotaPropia_imputaAVarado() {
        cuotasPorMetodo(Collections.emptyList(), List.of(cuotaVarado));

        Resultado r = service.resolver(ctx(BARRETEADO, true));

        assertEquals(Estado.CRUZADA, r.getEstado());
        assertTrue(r.isCruzada());
        assertEquals(VARADO, r.getMetodoImputacionId());
        assertEquals(12L, r.getCuotaDestinoId());
        assertEquals("Sin cuota de barreteado para la especie, la comuna y el período: la captura corregida (1.130,00 kg) "
                        + "se imputa a la cuota de varado Comuna Coquimbo (ID 12).",
                r.texto(new BigDecimal("1130.00")));
    }

    @Test
    @DisplayName("Barreteado en veda con cuota de barreteado: consume su propia cuota, sin imputación cruzada")
    void barreteadoEnVeda_conCuotaPropia_noAplica() {
        CuotaExtraccionModel cuotaBarreteado = new CuotaExtraccionModel();
        cuotaBarreteado.setId(13L);
        cuotasPorMetodo(List.of(cuotaBarreteado), List.of(cuotaVarado));

        Resultado r = service.resolver(ctx(BARRETEADO, true));

        assertEquals(Estado.NO_APLICA, r.getEstado());
        assertNull(r.getMetodoImputacionId());
        assertNull(r.texto(BigDecimal.TEN));
    }

    @Test
    @DisplayName("Barreteado en veda sin cuota de barreteado ni de varado: se informa y no se imputa")
    void barreteadoEnVeda_sinCuotaDestino() {
        cuotasPorMetodo(Collections.emptyList(), Collections.emptyList());

        Resultado r = service.resolver(ctx(BARRETEADO, true));

        assertEquals(Estado.SIN_CUOTA_DESTINO, r.getEstado());
        assertNull(r.getMetodoImputacionId());
        assertEquals("Sin cuota de barreteado ni de varado para la especie, la comuna y el período: "
                + "la captura no se descuenta de ninguna cuota.", r.texto(BigDecimal.ONE));
    }

    @Test
    @DisplayName("Fuera de veda, o con el método de destino, no hay imputación cruzada ni consultas de cuota")
    void fueraDeVeda_oVarado_noAplica() {
        assertEquals(Estado.NO_APLICA, service.resolver(ctx(BARRETEADO, false)).getEstado());
        assertEquals(Estado.NO_APLICA, service.resolver(ctx(VARADO, true)).getEstado());
        assertEquals(Estado.NO_APLICA, service.resolver(ctx(null, true)).getEstado());
        verify(cuotaService, never()).cuotasAplicables(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Con cuota_imputacion_veda_activo = false la regla no actúa")
    void reglaApagada_noAplica() {
        when(parametrosService.imputacionVeda()).thenReturn(new ParametrosImputacionVeda(false, "BARRETEADO", "VARADO"));
        cuotasPorMetodo(Collections.emptyList(), List.of(cuotaVarado));

        assertEquals(Estado.NO_APLICA, service.resolver(ctx(BARRETEADO, true)).getEstado());
        assertNull(service.resolverMetodos());
    }

    @Test
    @DisplayName("Los métodos se reconocen por nombre sin tildes ni mayúsculas; un nombre ambiguo no se usa")
    void buscarPorNombre() {
        List<ExtraccionTipoModel> metodos = List.of(
                ExtraccionTipoModel.builder().id(1L).nombre("Varado").build(),
                ExtraccionTipoModel.builder().id(2L).nombre("BARRETEADO").build(),
                ExtraccionTipoModel.builder().id(3L).nombre("Recolección de varado en playa").build());

        assertEquals(2L, ImputacionCruzadaVedaService.buscarPorNombre(metodos, "barreteado").getId());
        assertEquals(1L, ImputacionCruzadaVedaService.buscarPorNombre(metodos, "VARADO").getId(),
                "La coincidencia exacta gana a la parcial");
        assertEquals(3L, ImputacionCruzadaVedaService.buscarPorNombre(metodos, "recoleccion de varado").getId(),
                "Sin tildes");
        assertNull(ImputacionCruzadaVedaService.buscarPorNombre(
                List.of(ExtraccionTipoModel.builder().id(8L).nombre("Varado norte").build(),
                        ExtraccionTipoModel.builder().id(9L).nombre("Varado sur").build()), "VARADO"),
                "Dos coincidencias parciales: ambiguo");
        assertNull(ImputacionCruzadaVedaService.buscarPorNombre(metodos, "  "));
    }
}
