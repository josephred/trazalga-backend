package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.CuotaExtraccionService.EvaluacionCuotaResult;
import com.trazalga.api.services.LimiteExtraccionDiarioService.EvaluacionLedResult;
import com.trazalga.api.services.VedaEvaluadorService.EvaluacionVedaResult;

@ExtendWith(MockitoExtension.class)
public class DeclaracionMarcaValidationTest {

    @Mock
    private CapturaService capturaService;

    @Mock
    private VedaEvaluadorService vedaEvaluadorService;

    @Mock
    private CuotaExtraccionService cuotaExtraccionService;

    @Mock
    private LimiteExtraccionDiarioService limiteExtraccionDiarioService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @InjectMocks
    private ValidacionDeclaracionService validacionService;

    @Mock
    private IDeclaracionMarcaRepository marcaRepository;

    @InjectMocks
    private DeclaracionMarcaService declaracionMarcaService;

    @Test
    void testDesembarqueAtipicoGeneraMarcaCuandoSuperaUmbral() {
        when(configuracionGeneralService.getDouble(eq("desembarque_umbral_atipico_kg"), anyDouble()))
                .thenReturn(5000.0);

        when(capturaService.calcular(any(), any(), any(), any()))
                .thenReturn(CalculoCapturaResult.builder()
                        .exitoso(true)
                        .captura(new BigDecimal("6000.00"))
                        .factorAplicado(new BigDecimal("1.00"))
                        .build());

        when(vedaEvaluadorService.evaluar(any(), any(), any(), any()))
                .thenReturn(new EvaluacionVedaResult(false, false, "OK", null));

        when(cuotaExtraccionService.evaluarCuotaDeclaracion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvaluacionCuotaResult(true, false, false, false, null, BigDecimal.ZERO, BigDecimal.ZERO, "OK", null));

        when(limiteExtraccionDiarioService.evaluar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvaluacionLedResult(false, false, BigDecimal.ZERO, BigDecimal.ZERO, "OK", null));

        // Desembarque 6000 kg > 5000 kg umbral
        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR")
                .usuarioId(1L)
                .especieId(1L)
                .fechaDeclaracion(new Date())
                .fechaExtraccion(new Date())
                .desembarqueKg(new BigDecimal("6000.00"))
                .build();

        ResultadoValidacion res = validacionService.validar(ctx);

        assertFalse(res.esRechazado(), "Desembarque atípico no bloquea la faena");
        assertTrue(res.getMarcas().stream().anyMatch(m -> "DESEMBARQUE_ATIPICO".equals(m.getMarca())),
                "Debe generar marca DESEMBARQUE_ATIPICO");
        assertTrue(res.getMarcas().stream().anyMatch(m -> m.getDetalle().contains("6000.00 kg")),
                "El detalle debe incluir los kg declarados");
    }

    @Test
    void testDesembarqueNormalNoGeneraMarcaAtipico() {
        when(configuracionGeneralService.getDouble(eq("desembarque_umbral_atipico_kg"), anyDouble()))
                .thenReturn(5000.0);

        when(capturaService.calcular(any(), any(), any(), any()))
                .thenReturn(CalculoCapturaResult.builder()
                        .exitoso(true)
                        .captura(new BigDecimal("4000.00"))
                        .factorAplicado(new BigDecimal("1.00"))
                        .build());

        when(vedaEvaluadorService.evaluar(any(), any(), any(), any()))
                .thenReturn(new EvaluacionVedaResult(false, false, "OK", null));

        when(cuotaExtraccionService.evaluarCuotaDeclaracion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvaluacionCuotaResult(true, false, false, false, null, BigDecimal.ZERO, BigDecimal.ZERO, "OK", null));

        when(limiteExtraccionDiarioService.evaluar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvaluacionLedResult(false, false, BigDecimal.ZERO, BigDecimal.ZERO, "OK", null));

        // Desembarque 4000 kg <= 5000 kg umbral
        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR")
                .usuarioId(1L)
                .especieId(1L)
                .fechaDeclaracion(new Date())
                .fechaExtraccion(new Date())
                .desembarqueKg(new BigDecimal("4000.00"))
                .build();

        ResultadoValidacion res = validacionService.validar(ctx);

        assertFalse(res.esRechazado());
        assertFalse(res.getMarcas().stream().anyMatch(m -> "DESEMBARQUE_ATIPICO".equals(m.getMarca())),
                "No debe generar marca DESEMBARQUE_ATIPICO para 4000 kg");
    }

    @Test
    void testVedaBloqueoEstrictoPreservaMarcasEnResultado() {
        when(capturaService.calcular(any(), any(), any(), any()))
                .thenReturn(CalculoCapturaResult.builder().exitoso(true).captura(BigDecimal.TEN).build());

        VedaEspecieModel vedaModel = new VedaEspecieModel();
        vedaModel.setId(99L);
        when(vedaEvaluadorService.evaluar(any(), any(), any(), any()))
                .thenReturn(new EvaluacionVedaResult(true, true, "Extracción de Huiro Negro durante veda vigente (resolución 123)", vedaModel));

        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("ARMADOR")
                .usuarioId(1L)
                .especieId(1L)
                .fechaDeclaracion(new Date())
                .fechaExtraccion(new Date())
                .desembarqueKg(BigDecimal.TEN)
                .build();

        ResultadoValidacion res = validacionService.validar(ctx);

        assertTrue(res.esRechazado(), "Debe ser rechazado por veda estricta");
        assertFalse(res.getMarcas().isEmpty(), "Las marcas no deben perderse al rechazar");
        assertEquals("EN_VEDA", res.getMarcas().get(0).getMarca());
        assertEquals(99L, res.getMarcas().get(0).getReglaId());
    }

    @Test
    void testPersistirMarcaConDeclaracionIdNulo() {
        DeclaracionMarcaModel savedModel = DeclaracionMarcaModel.builder()
                .id(1L)
                .declaracionTipo("ARMADOR")
                .declaracionId(null)
                .marca("LED_EXCEDIDO")
                .detalle("Intento bloqueado")
                .reglaId(5L)
                .resuelta(false)
                .build();

        when(marcaRepository.save(any(DeclaracionMarcaModel.class))).thenReturn(savedModel);

        DeclaracionMarcaModel res = declaracionMarcaService.marcar("ARMADOR", null, "LED_EXCEDIDO", "Intento bloqueado", 5L);

        assertNotNull(res);
        assertNull(res.getDeclaracionId(), "declaracionId puede ser null en intentos rechazados");
        assertEquals("LED_EXCEDIDO", res.getMarca());
        verify(marcaRepository, times(1)).save(any(DeclaracionMarcaModel.class));
    }

    @Test
    void testResumenMarcasCalculaTotalesCorrectamente() {
        List<DeclaracionMarcaModel> marcas = List.of(
                DeclaracionMarcaModel.builder().id(1L).marca("EN_VEDA").resuelta(false).build(),
                DeclaracionMarcaModel.builder().id(2L).marca("EN_VEDA").resuelta(true).build(),
                DeclaracionMarcaModel.builder().id(3L).marca("LED_EXCEDIDO").resuelta(false).build(),
                DeclaracionMarcaModel.builder().id(4L).marca("DESEMBARQUE_ATIPICO").resuelta(false).build(),
                DeclaracionMarcaModel.builder().id(5L).marca("CUOTA_EXCEDIDA").resuelta(true).build()
        );

        when(marcaRepository.findAll()).thenReturn(marcas);

        Map<String, Object> resumen = declaracionMarcaService.getResumen();

        assertEquals(5L, resumen.get("total"));
        assertEquals(3L, resumen.get("pendientes"));
        assertEquals(2L, resumen.get("resueltas"));

        @SuppressWarnings("unchecked")
        Map<String, Long> porMarca = (Map<String, Long>) resumen.get("porMarca");
        assertEquals(2L, porMarca.get("EN_VEDA"));
        assertEquals(1L, porMarca.get("LED_EXCEDIDO"));
        assertEquals(1L, porMarca.get("DESEMBARQUE_ATIPICO"));
        assertEquals(1L, porMarca.get("CUOTA_EXCEDIDA"));
        assertEquals(0L, porMarca.get("POSTERIOR_CIERRE"));
    }
}
