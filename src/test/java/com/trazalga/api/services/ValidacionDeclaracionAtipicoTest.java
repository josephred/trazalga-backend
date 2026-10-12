package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.services.CuotaExtraccionService.EvaluacionCuotaResult;
import com.trazalga.api.services.LimiteExtraccionDiarioService.EvaluacionLedResult;
import com.trazalga.api.services.VedaEvaluadorService.EvaluacionVedaResult;
import com.trazalga.api.services.indicadores.DesembarqueAtipicoService;
import com.trazalga.api.services.indicadores.ResultadoCriterioAtipico;

@ExtendWith(MockitoExtension.class)
public class ValidacionDeclaracionAtipicoTest {

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

    @Mock
    private DesembarqueAtipicoService desembarqueAtipicoService;

    @InjectMocks
    private ValidacionDeclaracionService validacionService;

    @BeforeEach
    void setUp() {
        when(capturaService.calcular(any(), any(), any(), any())).thenReturn(
                CalculoCapturaResult.builder()
                        .exitoso(true)
                        .captura(new BigDecimal("1150.00"))
                        .factorAplicado(BigDecimal.ONE)
                        .factorConversionId(1L)
                        .build()
        );

        when(vedaEvaluadorService.evaluar(any(), any(), any(), any())).thenReturn(
                new EvaluacionVedaResult(false, false, "Sin veda", null)
        );

        when(cuotaExtraccionService.evaluarCuotaDeclaracion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvaluacionCuotaResult(true, false, false, false, false, null, List.of(), BigDecimal.ZERO, BigDecimal.TEN, "OK", null));

        when(limiteExtraccionDiarioService.evaluar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvaluacionLedResult(false, false, BigDecimal.ZERO, BigDecimal.TEN, "OK", null));
    }

    @Test
    @DisplayName("TD.2 / Defecto 7: Validacion conecta evaluador estadístico y genera marca DESEMBARQUE_ATIPICO con sigma")
    void testValidacion_ConectaEvaluadorEstadistico_GeneraMarcaSigma() {
        when(configuracionGeneralService.getBoolean("desembarque_atipico_estadistico_activo", false)).thenReturn(true);

        when(desembarqueAtipicoService.obtenerHistoricoKilos(eq("RECOLECTOR"), eq(10L), any(), eq(1L), eq(2L), any(), any()))
                .thenReturn(List.of(700.0, 900.0, 700.0, 900.0, 700.0, 900.0, 700.0, 900.0, 700.0, 900.0, 700.0, 900.0));

        ResultadoCriterioAtipico resSigma = ResultadoCriterioAtipico.builder()
                .atipico(true)
                .criterioClave("DESVIACION_ESTADISTICA")
                .parametro("desembarque_atipico_sigma")
                .umbral("2.5")
                .valor("3.50")
                .unidad("sigma")
                .texto("z = 3,5 > 2,5 (media 800 kg, desv. 100 kg, n = 12)")
                .build();

        when(desembarqueAtipicoService.evaluarAtipicos(argThat(ctx ->
                Double.valueOf(1150.0).equals(ctx.getKilos()) &&
                ctx.getHistoricoKilos() != null && ctx.getHistoricoKilos().size() == 12
        ))).thenReturn(List.of(resSigma));

        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR")
                .usuarioId(5L)
                .especieId(1L)
                .humedadEstadoId(2L)
                .caletaId(10L)
                .desembarqueKg(new BigDecimal("1150.00"))
                .fechaExtraccion(new Date())
                .fechaDeclaracion(new Date())
                .build();

        ResultadoValidacion res = validacionService.validar(ctx);

        assertNotNull(res);
        assertEquals(ResultadoValidacion.DecisionValidacion.MARCAR, res.getDecision());
        assertFalse(res.getMarcas().isEmpty());

        MarcaItem marca = res.getMarcas().stream()
                .filter(m -> "DESEMBARQUE_ATIPICO".equals(m.getMarca()))
                .findFirst()
                .orElse(null);

        assertNotNull(marca, "Debe generar la marca DESEMBARQUE_ATIPICO");
        assertNotNull(marca.getCriterio(), "La marca debe contener CriterioHallazgo estructurado");
        assertEquals("desembarque_atipico_sigma", marca.getCriterio().parametro());
        assertEquals("sigma", marca.getCriterio().unidad());
        assertTrue(marca.getDetalle().contains("z = 3,5 > 2,5"));
    }
}
