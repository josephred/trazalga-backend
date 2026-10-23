package com.trazalga.api.services.indicadores;

import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.FichaComercializadorDTO;
import com.trazalga.api.dto.FichaTrazabilidadDTO;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.*;
import com.trazalga.api.services.hallazgos.HallazgoFactory;
import com.trazalga.api.services.hallazgos.HallazgoService;
import com.trazalga.api.services.indicadores.CoherenciaHumedad.ParametrosCoherencia;
import com.trazalga.api.services.parametros.ParametrosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TH.6 · Pruebas de integración de coherencia de humedad (TH.2, TH.3, TH.4, TH.5).
 * Cubre:
 * 1. Origen (caso C): crea marca HUMEDAD_INCOHERENTE cuando el interruptor está activo;
 *    editar sin cambios no la duplica; con interruptor apagado no hay marca.
 * 2. Planta: recepción el 06-10 a las 09:00 con humedad verificada HÚMEDO y orígenes
 *    del 01-10 y 02-10 -> h_min = 81,0 h -> marca HUMEDAD_INCOHERENTE sobre la declaración de planta.
 * 3. Despacho: comercializador despacha el 04-10 a las 10:00 heredando HÚMEDO de un origen
 *    del 01-10 (h_min = 58,0 h) -> la ficha muestra estado esperado SEMI HÚMEDO y no hay marca.
 */
class ReglaCoherenciaHumedadIT {

    private ValidacionDeclaracionService validacionService;
    private ParametrosService parametrosService;
    private IDeclaracionMarcaRepository marcaRepository;
    private HallazgoFactory hallazgoFactory;
    private HallazgoService hallazgoService;
    private AlertaTriggerService alertaTriggerService;

    private final Map<String, DeclaracionMarcaModel> marcasPorClave = new LinkedHashMap<>();
    private long nextMarcaId = 1L;

    @BeforeEach
    void setUp() {
        marcasPorClave.clear();
        nextMarcaId = 1L;

        parametrosService = mock(ParametrosService.class);
        marcaRepository = mock(IDeclaracionMarcaRepository.class);
        hallazgoFactory = new HallazgoFactory();
        hallazgoService = new HallazgoService();

        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(hallazgoService, "declaracionMarcaRepository", marcaRepository);
        ReflectionTestUtils.setField(hallazgoService, "hallazgoFactory", hallazgoFactory);
        ReflectionTestUtils.setField(hallazgoService, "eventPublisher", eventPublisher);

        when(marcaRepository.findByClaveIdempotencia(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(marcasPorClave.get(inv.getArgument(0, String.class))));
        when(marcaRepository.save(any(DeclaracionMarcaModel.class)))
                .thenAnswer(inv -> {
                    DeclaracionMarcaModel m = inv.getArgument(0);
                    if (m.getId() == null) {
                        m.setId(nextMarcaId++);
                    }
                    marcasPorClave.put(m.getClaveIdempotencia(), m);
                    return m;
                });

        alertaTriggerService = new AlertaTriggerService();
        ReflectionTestUtils.setField(alertaTriggerService, "hallazgoService", hallazgoService);

        validacionService = new ValidacionDeclaracionService();
        ConfiguracionGeneralService cfgGen = mock(ConfiguracionGeneralService.class);
        when(cfgGen.getDouble(eq("desembarque_umbral_atipico_kg"), anyDouble())).thenReturn(5000.0);

        CapturaService capturaService = mock(CapturaService.class);
        when(capturaService.calcular(any(), any(), any(), any())).thenReturn(
                com.trazalga.api.dto.CalculoCapturaResult.builder()
                        .exitoso(true)
                        .captura(BigDecimal.valueOf(500))
                        .factorAplicado(BigDecimal.ONE)
                        .build()
        );

        VedaEvaluadorService vedaEvaluadorService = mock(VedaEvaluadorService.class);
        VedaEvaluadorService.EvaluacionVedaResult vedaOk = mock(VedaEvaluadorService.EvaluacionVedaResult.class);
        when(vedaOk.isEnVeda()).thenReturn(false);
        when(vedaOk.isBloquear()).thenReturn(false);
        when(vedaEvaluadorService.evaluar(any(), any(), any(), any())).thenReturn(vedaOk);

        CuotaExtraccionService cuotaExtraccionService = mock(CuotaExtraccionService.class);
        CuotaExtraccionService.EvaluacionCuotaResult cuotaOk = mock(CuotaExtraccionService.EvaluacionCuotaResult.class);
        when(cuotaOk.isBloquear()).thenReturn(false);
        when(cuotaExtraccionService.evaluarCuotaDeclaracion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(cuotaOk);

        LimiteExtraccionDiarioService limiteExtraccionDiarioService = mock(LimiteExtraccionDiarioService.class);
        LimiteExtraccionDiarioService.EvaluacionLedResult ledOk = mock(LimiteExtraccionDiarioService.EvaluacionLedResult.class);
        when(ledOk.isExcede()).thenReturn(false);
        when(ledOk.isBloquear()).thenReturn(false);
        when(limiteExtraccionDiarioService.evaluar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(ledOk);

        ReflectionTestUtils.setField(validacionService, "capturaService", capturaService);
        ReflectionTestUtils.setField(validacionService, "vedaEvaluadorService", vedaEvaluadorService);
        ReflectionTestUtils.setField(validacionService, "cuotaExtraccionService", cuotaExtraccionService);
        ReflectionTestUtils.setField(validacionService, "limiteExtraccionDiarioService", limiteExtraccionDiarioService);
        ReflectionTestUtils.setField(validacionService, "configuracionGeneralService", cfgGen);
        ReflectionTestUtils.setField(validacionService, "parametrosService", parametrosService);
        ReflectionTestUtils.setField(validacionService, "hallazgoFactory", hallazgoFactory);
        ReflectionTestUtils.setField(validacionService, "hallazgoService", hallazgoService);
    }

    private static Date toDate(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void origen_casoC_creaMarcaIdempotenteCuandoActivo_yNoCreaCuandoApagado() {
        Date fExtraccion = toDate(2026, 10, 1);
        Date fDeclaracion = toDate(2026, 10, 3);

        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR")
                .usuarioId(10L)
                .especieId(1L)
                .humedadEstadoId(1L)
                .humedadNombre("HÚMEDO")
                .fechaExtraccion(fExtraccion)
                .fechaDeclaracion(fDeclaracion)
                .hora("10:00")
                .desembarqueKg(BigDecimal.valueOf(500))
                .build();

        // 1) Con el interruptor apagado: no hay marca
        when(parametrosService.coherenciaHumedad()).thenReturn(ParametrosCoherencia.builder()
                .activo(false)
                .modo(CoherenciaHumedad.MODO_DIRECCIONAL)
                .build());

        ResultadoValidacion resApagado = validacionService.validar(ctx);
        assertTrue(resApagado.getMarcas().stream().noneMatch(m -> "HUMEDAD_INCOHERENTE".equals(m.getMarca())));
        assertEquals(0, marcasPorClave.size());

        // 2) Con el interruptor encendido: se crea HUMEDAD_INCOHERENTE
        when(parametrosService.coherenciaHumedad()).thenReturn(ParametrosCoherencia.builder()
                .activo(true)
                .modo(CoherenciaHumedad.MODO_DIRECCIONAL)
                .build());

        ResultadoValidacion resEncendido = validacionService.validar(ctx);
        assertEquals(1, resEncendido.getMarcas().size());
        assertEquals("HUMEDAD_INCOHERENTE", resEncendido.getMarcas().get(0).getMarca());

        alertaTriggerService.procesarMarcas("RECOLECTOR", 101L, 10L, resEncendido.getMarcas());
        assertEquals(1, marcasPorClave.size());
        DeclaracionMarcaModel marca = marcasPorClave.get("RECOLECTOR:101:HUMEDAD_INCOHERENTE");
        assertNotNull(marca);
        assertEquals("HUMEDAD_INCOHERENTE", marca.getMarca());
        assertEquals("humedad_edad_max_humedo_horas", marca.getCriterioParametro());
        assertEquals("24.0", marca.getCriterioUmbral());
        assertEquals("34.0", marca.getCriterioValor());
        assertEquals("h", marca.getCriterioUnidad());
        assertTrue(marca.getDetalle().contains("34 h"));

        // 3) Editar sin cambios: no duplica la marca
        ResultadoValidacion resEdicion = validacionService.validar(ctx);
        alertaTriggerService.procesarMarcas("RECOLECTOR", 101L, 10L, resEdicion.getMarcas());
        assertEquals(1, marcasPorClave.size());
    }

    @Test
    void planta_recepcionConOrigenes01OctY02Oct_tomaFechaMasReciente81h_yCreaMarcaEnPlanta() {
        when(parametrosService.coherenciaHumedad()).thenReturn(ParametrosCoherencia.builder()
                .activo(true)
                .modo(CoherenciaHumedad.MODO_DIRECCIONAL)
                .build());

        IDeclaracionRecolectorRepository recRepo = mock(IDeclaracionRecolectorRepository.class);
        DeclaracionRecolectorModel orig1 = new DeclaracionRecolectorModel();
        orig1.setId(1L);
        orig1.setFechaExtraccion(toDate(2026, 10, 1));
        DeclaracionRecolectorModel orig2 = new DeclaracionRecolectorModel();
        orig2.setId(2L);
        orig2.setFechaExtraccion(toDate(2026, 10, 2));

        when(recRepo.findAllById(any())).thenReturn(List.of(orig1, orig2));

        DeclaracionPlantaAbastecimientoService plantaService = new DeclaracionPlantaAbastecimientoService();
        ReflectionTestUtils.setField(plantaService, "validacionDeclaracionService", validacionService);
        ReflectionTestUtils.setField(plantaService, "recolectorRepository", recRepo);

        HumedadEstadoModel humVerificada = new HumedadEstadoModel();
        humVerificada.setId(1L);
        humVerificada.setNombre("HÚMEDO");

        DeclaracionPlantaAbastecimientoModel recepcion = new DeclaracionPlantaAbastecimientoModel();
        recepcion.setId(500L);
        recepcion.setFechaIngresoPlanta(toDate(2026, 10, 6));
        recepcion.setHora("09:00");
        recepcion.setHumedadEstadoRecepcion(humVerificada);
        recepcion.setDeclaracionesSeleccionadas("RECOLECTOR:1,RECOLECTOR:2");

        ReflectionTestUtils.invokeMethod(plantaService, "evaluarCoherenciaHumedadRecepcion", recepcion);

        assertEquals(1, marcasPorClave.size());
        DeclaracionMarcaModel marcaPlanta = marcasPorClave.get("PLANTA_ABASTECIMIENTO:500:HUMEDAD_INCOHERENTE");
        assertNotNull(marcaPlanta);
        assertEquals("PLANTA_ABASTECIMIENTO", marcaPlanta.getDeclaracionTipo());
        assertEquals(500L, marcaPlanta.getDeclaracionId());
        assertEquals("81.0", marcaPlanta.getCriterioValor());
        assertTrue(marcaPlanta.getDetalle().contains("81 h"));
        assertTrue(marcaPlanta.getDetalle().contains("02-10-2026"));
    }

    @Test
    void despachoComercializador_heredaHumedoDe01Oct_muestraEstadoEsperadoSemiHumedoYNoCreaMarca() {
        when(parametrosService.coherenciaHumedad()).thenReturn(ParametrosCoherencia.builder()
                .activo(true)
                .modo(CoherenciaHumedad.MODO_DIRECCIONAL)
                .build());

        IDeclaracionRecolectorRepository recRepo = mock(IDeclaracionRecolectorRepository.class);
        IDeclaracionComercializadorRepository comRepo = mock(IDeclaracionComercializadorRepository.class);
        CadenaCustodiaService cadenaCustodiaService = mock(CadenaCustodiaService.class);

        HumedadEstadoModel hum = new HumedadEstadoModel();
        hum.setId(1L);
        hum.setNombre("HÚMEDO");

        DeclaracionRecolectorModel orig = new DeclaracionRecolectorModel();
        orig.setId(10L);
        orig.setFolioOrigen("OR-10");
        orig.setFechaExtraccion(toDate(2026, 10, 1));
        orig.setFechaDeclaracion(toDate(2026, 10, 1));
        orig.setHora("12:00");
        orig.setDesembarque(BigDecimal.valueOf(300));
        orig.setHumedadEstado(hum);
        when(recRepo.findById(10L)).thenReturn(Optional.of(orig));

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(20L);
        com.setFolioOrigen("COM-20");
        com.setFechaDeclaracion(toDate(2026, 10, 4));
        com.setHora("10:00");
        com.setCantidad(BigDecimal.valueOf(300));
        com.setHumedadEstado(hum);
        when(comRepo.findById(20L)).thenReturn(Optional.of(com));

        CadenaCustodiaService.OrigenRef oRef = CadenaCustodiaService.OrigenRef.builder()
                .tipo("RECOLECTOR")
                .id(10L)
                .folio("OR-10")
                .fechaExtraccion(toDate(2026, 10, 1))
                .fecha(toDate(2026, 10, 1))
                .hora("12:00")
                .cantidad(BigDecimal.valueOf(300))
                .humedadNombre("HÚMEDO")
                .build();

        CadenaCustodiaService.ComercializadorTramo tramo = CadenaCustodiaService.ComercializadorTramo.builder()
                .id(20L)
                .salto(1)
                .despachado(true)
                .fechaDeclaracion(toDate(2026, 10, 4))
                .hora("10:00")
                .cantidad(BigDecimal.valueOf(300))
                .horasEnBodega(22.0)
                .semaforo("VERDE")
                .build();

        CadenaCustodiaService.Cadena cadena = CadenaCustodiaService.Cadena.builder()
                .tipoConsulta("COMERCIALIZADOR")
                .idConsulta(20L)
                .origenes(List.of(oRef))
                .comercializadoresTramos(List.of(tramo))
                .estadoHumedadPredominante("HÚMEDO")
                .build();

        when(cadenaCustodiaService.resolver("COMERCIALIZADOR", 20L)).thenReturn(cadena);

        FichaTrazabilidadService fichaService = new FichaTrazabilidadService(
                cadenaCustodiaService, null, null, recRepo, null, null, comRepo, null, null, null, null);

        FichaTrazabilidadDTO ficha = fichaService.obtenerFicha("COMERCIALIZADOR", 20L);
        assertNotNull(ficha);
        assertEquals(1, ficha.getComercializadores().size());

        FichaComercializadorDTO comDto = ficha.getComercializadores().get(0);
        assertEquals(58.0, comDto.getEdadExtraccionAlDespachoHoras(), 0.01);
        assertEquals("SEMI HÚMEDO", comDto.getEstadoEsperadoAlDespacho());
        assertTrue(comDto.getMarcas().isEmpty());
        assertEquals(0, marcasPorClave.size());
    }
}
