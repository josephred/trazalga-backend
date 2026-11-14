package com.trazalga.api.services.indicadores;

import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.RendimientoRangoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IRendimientoRangoRepository;
import com.trazalga.api.services.CadenaCustodiaService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.FactorConversionService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import com.trazalga.api.services.hallazgos.HallazgoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RendimientoPlantaServiceTest {

    @Mock private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Mock private IRendimientoRangoRepository rendimientoRangoRepository;
    @Mock private FactorConversionService factorConversionService;
    @Mock private ConfiguracionGeneralService configService;
    @Mock private IProductoRepository productoRepository;
    @Mock private HallazgoService hallazgoService;

    private RendimientoPlantaService service;

    private EspecieModel pelillo;
    private HumedadEstadoModel humHumedo;
    private HumedadEstadoModel humSemiSeco;
    private HumedadEstadoModel humSeco;
    private ProductoModel prodSeco;
    private UsuarioModel planta;

    @BeforeEach
    void setup() {
        service = new RendimientoPlantaService(
                abastecimientoRepository,
                rendimientoRangoRepository,
                factorConversionService,
                configService,
                productoRepository,
                hallazgoService
        );

        pelillo = EspecieModel.builder().id(1L).nombre("PELILLO").build();
        humHumedo = HumedadEstadoModel.builder().id(1L).nombre("Humedo").build();
        humSemiSeco = HumedadEstadoModel.builder().id(2L).nombre("Semi Seco").build();
        humSeco = HumedadEstadoModel.builder().id(3L).nombre("Seco").build();
        prodSeco = ProductoModel.builder().id(10L).codigo("PF-SECO").nombre("Pelillo Seco").humedadEstado(humSeco).especie(pelillo).activo(true).build();
        planta = UsuarioModel.builder().id(99L).rut("76.111.222-3").nombres("Planta").apellidop("Test").build();

        lenient().when(configService.getBoolean("rendimiento_activo", true)).thenReturn(true);
        lenient().when(configService.getDouble("rendimiento_tolerancia_pct", 5.0)).thenReturn(5.0);
        lenient().when(configService.getValor("rendimiento_min_pct", "")).thenReturn("");
        lenient().when(configService.getValor(eq("rendimiento_base"), anyString())).thenReturn("MASA_RECIBIDA");
        lenient().when(rendimientoRangoRepository.findVigente(anyLong(), anyLong(), anyLong(), any())).thenReturn(Optional.empty());
        lenient().when(rendimientoRangoRepository.findVigentePorBase(anyLong(), anyLong(), anyLong(), anyString(), any())).thenReturn(Optional.empty());
    }

    private FactorConversionModel factor(BigDecimal f) {
        return FactorConversionModel.builder().factor(f).build();
    }

    @Test
    @DisplayName("NP.4 caso 1: Pelillo Húmedo 10000 kg -> Seco (factor 3.16815) normal al 31% y anómalo al 40%")
    void factoresSemilla_normalYAnomalo() {
        when(factorConversionService.findFactorVigente(eq(1L), eq(1L), any())).thenReturn(Optional.of(factor(new BigDecimal("1.00"))));
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any())).thenReturn(Optional.of(factor(new BigDecimal("3.16815"))));

        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(101L).especie(pelillo).humedadEstado(humSemiSeco).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10500")).pesoRomanaKg(new BigDecimal("10000"))
                .build();

        DeclaracionPlantaProduccionModel normal = DeclaracionPlantaProduccionModel.builder()
                .id(1L).folioDeclaracionPpla("PPLA-1").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadMateriaPrima(new BigDecimal("999")) // cliente envía cifra errónea: debe sobrescribirse
                .cantidadProducto(new BigDecimal("3100"))
                .build();

        RendimientoPlantaService.ResultadoRendimiento resNormal = service.evaluarYActualizar(normal, List.of(lote), new java.util.Date());
        assertFalse(resNormal.sinLotes());
        assertEquals(new BigDecimal("10000.00"), normal.getCantidadMateriaPrima());
        assertEquals(RendimientoPlantaService.FUENTE_FACTORES, resNormal.fuente());
        assertEquals(new BigDecimal("31.00"), resNormal.rendimientoPct());
        assertEquals(new BigDecimal("31.56"), resNormal.rendimientoMaxTeoricoPct());
        assertFalse(resNormal.anomalo());
        service.registrarHallazgosPostGuardado(normal, resNormal);
        verify(hallazgoService, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any(), any());

        DeclaracionPlantaProduccionModel anomala = DeclaracionPlantaProduccionModel.builder()
                .id(2L).folioDeclaracionPpla("PPLA-2").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("4000"))
                .build();

        RendimientoPlantaService.ResultadoRendimiento resAnomalo = service.evaluarYActualizar(anomala, List.of(lote), new java.util.Date());
        assertTrue(resAnomalo.anomalo());
        assertEquals("EXCESO", resAnomalo.tipoAnomalia());
        assertEquals(new BigDecimal("40.00"), resAnomalo.rendimientoPct());
        assertTrue(resAnomalo.detalleAnomalia().contains("40,0 %"));
        assertTrue(resAnomalo.detalleAnomalia().contains("31,6 %"));
        assertTrue(resAnomalo.detalleAnomalia().contains("factores"));
        assertTrue(resAnomalo.detalleAnomalia().contains("tolerancia 5 %"));

        service.registrarHallazgosPostGuardado(anomala, resAnomalo);
        verify(hallazgoService).registrar(
                eq("PLANTA_PRODUCCION"), eq(2L), eq("RENDIMIENTO_ANOMALO"), eq("EXCESO"),
                any(CriterioHallazgo.class), eq(resAnomalo.detalleAnomalia()),
                eq("VALIDACION"), eq(99L), isNull());
    }

    @Test
    @DisplayName("NP.4 caso 2: Lotes mezclados (Húmedo + Semi-Seco) ponderan masa equivalente de entrada")
    void lotesMezclados_humedadesDistintas() {
        when(factorConversionService.findFactorVigente(eq(1L), eq(1L), any())).thenReturn(Optional.of(factor(new BigDecimal("1.00"))));
        when(factorConversionService.findFactorVigente(eq(1L), eq(2L), any())).thenReturn(Optional.of(factor(new BigDecimal("2.50"))));
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any())).thenReturn(Optional.of(factor(new BigDecimal("4.00"))));

        // 6000 húmedo (eq 6000) + 4000 semi-seco sin romana -> usa cantidad (eq 10000) => eq total 16000 / 10000 kg -> max seco = 16000/4 = 4000 kg (40%)
        DeclaracionPlantaAbastecimientoModel l1 = DeclaracionPlantaAbastecimientoModel.builder()
                .id(1L).especie(pelillo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("6200")).pesoRomanaKg(new BigDecimal("6000"))
                .build();
        DeclaracionPlantaAbastecimientoModel l2 = DeclaracionPlantaAbastecimientoModel.builder()
                .id(2L).especie(pelillo).humedadEstado(humSemiSeco)
                .cantidad(new BigDecimal("4000")).pesoRomanaKg(null)
                .build();

        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(3L).folioDeclaracionPpla("PPLA-3").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("3900"))
                .build();

        RendimientoPlantaService.ResultadoRendimiento res = service.evaluarYActualizar(decl, List.of(l1, l2), new java.util.Date());
        assertEquals(new BigDecimal("10000.00"), decl.getCantidadMateriaPrima());
        assertEquals(new BigDecimal("16000.00"), decl.getMasaEntradaEquivalenteKg());
        assertEquals(new BigDecimal("15600.00"), decl.getMasaProductoEquivalenteKg());
        assertEquals(new BigDecimal("40.00"), res.rendimientoMaxTeoricoPct());
        assertEquals(new BigDecimal("39.00"), res.rendimientoPct());
        assertFalse(res.anomalo());
    }

    @Test
    @DisplayName("NP.4 caso 3: rendimiento_rango (TABLA) tiene prioridad sobre FACTORES")
    void tablaTienePrioridadSobreFactores() {
        RendimientoRangoModel rangoTabla = RendimientoRangoModel.builder()
                .id(55L).especie(pelillo).humedadEntrada(humHumedo).producto(prodSeco)
                .minPct(new BigDecimal("20.00")).maxPct(new BigDecimal("28.00")).activo(true)
                .vigenciaInicio(Date.valueOf("2026-01-01"))
                .build();
        when(rendimientoRangoRepository.findVigentePorBase(eq(1L), eq(1L), eq(10L), eq("MASA_RECIBIDA"), any())).thenReturn(Optional.of(rangoTabla));
        when(factorConversionService.findFactorVigente(eq(1L), eq(1L), any())).thenReturn(Optional.of(factor(new BigDecimal("1.00"))));
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any())).thenReturn(Optional.of(factor(new BigDecimal("2.50")))); // daría 40% en factores

        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(10L).especie(pelillo).humedadEstadoRecepcion(humHumedo)
                .pesoRomanaKg(new BigDecimal("10000")).build();

        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(4L).folioDeclaracionPpla("PPLA-4").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("3100")) // 31% <= 40% (factores), pero > 28 * 1.05 = 29.4% (TABLA)
                .build();

        RendimientoPlantaService.ResultadoRendimiento res = service.evaluarYActualizar(decl, List.of(lote), new java.util.Date());
        assertEquals(RendimientoPlantaService.FUENTE_TABLA, res.fuente());
        assertEquals(new BigDecimal("28.00"), res.rendimientoMaxTeoricoPct());
        assertTrue(res.anomalo());
        assertTrue(res.detalleAnomalia().contains("tabla"));
    }

    @Test
    @DisplayName("NP.4 caso 4: Producto sin humedad ni rango -> SIN_REFERENCIA y no emite marca")
    void productoSinHumedad_sinReferencia() {
        ProductoModel sinHum = ProductoModel.builder().id(11L).nombre("Agar crudo").humedadEstado(null).build();
        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(12L).especie(pelillo).humedadEstado(humHumedo).cantidad(new BigDecimal("5000")).build();

        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(5L).folioDeclaracionPpla("PPLA-5").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).productoResultante(sinHum)
                .cantidadProducto(new BigDecimal("4900")) // 98%, no debería marcar SIN_REFERENCIA
                .build();

        RendimientoPlantaService.ResultadoRendimiento res = service.evaluarYActualizar(decl, List.of(lote), new java.util.Date());
        assertEquals(RendimientoPlantaService.FUENTE_SIN_REFERENCIA, res.fuente());
        assertNull(res.rendimientoMaxTeoricoPct());
        assertFalse(res.anomalo());
    }

    @Test
    @DisplayName("NP.4 caso 5: Límite exacto de tolerancia (30% max + 5% tol = 31.50%), switch apagado y sin lotes")
    void limiteToleranciaYSwitchYValidaciones() {
        when(factorConversionService.findFactorVigente(eq(1L), eq(1L), any())).thenReturn(Optional.of(factor(new BigDecimal("1.00"))));
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any())).thenReturn(Optional.of(factor(new BigDecimal("3.333333"))));
        // Con max_pct en tabla = 30.00 exacto probamos borde exacto
        RendimientoRangoModel rango = RendimientoRangoModel.builder()
                .id(60L).especie(pelillo).humedadEntrada(humHumedo).producto(prodSeco)
                .maxPct(new BigDecimal("30.00")).activo(true).vigenciaInicio(Date.valueOf("2026-01-01"))
                .build();
        when(rendimientoRangoRepository.findVigentePorBase(eq(1L), eq(1L), eq(10L), eq("MASA_RECIBIDA"), any())).thenReturn(Optional.of(rango));

        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(20L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("10000")).build();

        // Exacto en el borde: 30.00 * 1.05 = 31.50% -> 3150 kg NO es anómalo
        DeclaracionPlantaProduccionModel borde = DeclaracionPlantaProduccionModel.builder()
                .id(6L).usuario(planta).fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("3150")).build();
        assertFalse(service.evaluarYActualizar(borde, List.of(lote), new java.util.Date()).anomalo());

        // 3151 kg = 31.51% > 31.50% -> SÍ es anómalo
        borde.setCantidadProducto(new BigDecimal("3151"));
        assertTrue(service.evaluarYActualizar(borde, List.of(lote), new java.util.Date()).anomalo());

        // Con switch apagado, 3151 kg calcula igual pero anomalo=false
        when(configService.getBoolean("rendimiento_activo", true)).thenReturn(false);
        RendimientoPlantaService.ResultadoRendimiento resApagado = service.evaluarYActualizar(borde, List.of(lote), new java.util.Date());
        assertFalse(resApagado.anomalo());
        assertEquals(new BigDecimal("31.51"), resApagado.rendimientoPct());

        // Sin lotes ni cantidad de materia prima > 0 -> 422
        DeclaracionPlantaProduccionModel invalida = DeclaracionPlantaProduccionModel.builder()
                .usuario(planta).cantidadMateriaPrima(BigDecimal.ZERO).cantidadProducto(new BigDecimal("100")).build();
        assertThrows(ValidacionPlantaException.class,
                () -> service.evaluarYActualizar(invalida, List.of(), new java.util.Date()));

        // Sin lotes pero con masa manual -> emite marca PRODUCCION_SIN_LOTES
        DeclaracionPlantaProduccionModel sinLotes = DeclaracionPlantaProduccionModel.builder()
                .id(7L).usuario(planta).fechaProduccion(Date.valueOf("2026-11-10"))
                .materiaPrimaEspecie(pelillo).humedadEstado(humHumedo).productoResultante(prodSeco)
                .cantidadMateriaPrima(new BigDecimal("1000")).cantidadProducto(new BigDecimal("200")).build();
        when(configService.getBoolean("rendimiento_activo", true)).thenReturn(true);
        RendimientoPlantaService.ResultadoRendimiento resSin = service.evaluarYActualizar(sinLotes, List.of(), new java.util.Date());
        assertTrue(resSin.sinLotes());
        service.registrarHallazgosPostGuardado(sinLotes, resSin);
        verify(hallazgoService).registrar(
                eq("PLANTA_PRODUCCION"), eq(7L), eq("PRODUCCION_SIN_LOTES"), eq("SIN_LOTES"),
                any(CriterioHallazgo.class), anyString(), eq("VALIDACION"), eq(99L), isNull());
    }

    @Test
    @DisplayName("QR.6: bajo CAPTURA_ORIGEN dos lotes calculan rendimiento final sobre captura y preservan rendimiento de proceso")
    void qr6_dosLotesSobreCapturaOrigen_calculaRendimientoFinalYPreservaProceso() {
        when(configService.getValor(eq("rendimiento_base"), anyString())).thenReturn("CAPTURA_ORIGEN");
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any()))
                .thenReturn(Optional.of(factor(new BigDecimal("3.5800"))));

        DeclaracionPlantaAbastecimientoModel lote1 = DeclaracionPlantaAbastecimientoModel.builder()
                .id(11L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("4500")).build();
        DeclaracionPlantaAbastecimientoModel lote2 = DeclaracionPlantaAbastecimientoModel.builder()
                .id(12L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("5500")).build();

        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(100L).folioDeclaracionPpla("PPLA-QR6-1").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-14"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("2500")).build();

        CapturaOrigenProduccion.ResultadoCapturaOrigen cap = new CapturaOrigenProduccion.ResultadoCapturaOrigen(
                new BigDecimal("10000.00"), true, 2, List.of("RECOLECTOR:1", "ARMADOR:2"), null);

        RendimientoPlantaService.ResultadoRendimiento res = service.evaluarYActualizar(
                decl, List.of(lote1, lote2), cap, new java.util.Date());

        assertEquals(RendimientoPlantaService.BASE_CAPTURA_ORIGEN, res.baseAplicada());
        assertEquals(RendimientoPlantaService.FUENTE_FACTORES_ORIGEN, res.fuente());
        assertEquals(new BigDecimal("10000.00"), decl.getCapturaOrigenKg());
        assertTrue(Boolean.TRUE.equals(decl.getCapturaOrigenCompleta()));
        assertEquals(new BigDecimal("25.00"), decl.getRendimientoPct());
        assertEquals(new BigDecimal("25.00"), decl.getRendimientoFinalPct());
        assertEquals(new BigDecimal("27.93"), res.rendimientoMaxTeoricoPct());
        assertFalse(res.anomalo());
    }

    @Test
    @DisplayName("QR.6: CapturaOrigenProduccion real colaborando con RendimientoPlantaService deduplica origen compartido")
    void qr6_origenCompartidoViaCapturaOrigenReal_seCuentaUnaSolaVez() {
        when(configService.getValor(eq("rendimiento_base"), anyString())).thenReturn("CAPTURA_ORIGEN");
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any()))
                .thenReturn(Optional.of(factor(new BigDecimal("3.5800"))));

        CadenaCustodiaService cadenaMock = mock(CadenaCustodiaService.class);
        IDeclaracionRecolectorRepository recRepo = mock(IDeclaracionRecolectorRepository.class);
        IDeclaracionArmadorRepository armRepo = mock(IDeclaracionArmadorRepository.class);
        IDeclaracionAreaRepository areaRepo = mock(IDeclaracionAreaRepository.class);
        IDeclaracionComercializadorRepository comRepo = mock(IDeclaracionComercializadorRepository.class);

        CapturaOrigenProduccion capturaOrigenReal = new CapturaOrigenProduccion(
                cadenaMock, recRepo, armRepo, areaRepo, comRepo);
        RendimientoPlantaService serviceConOrigen = new RendimientoPlantaService(
                abastecimientoRepository, rendimientoRangoRepository, factorConversionService,
                configService, productoRepository, hallazgoService, capturaOrigenReal);

        DeclaracionPlantaAbastecimientoModel loteDirecto = DeclaracionPlantaAbastecimientoModel.builder()
                .id(201L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("5000"))
                .declaracionesSeleccionadas("RECOLECTOR:501,ARMADOR:502").build();
        DeclaracionPlantaAbastecimientoModel loteCom = DeclaracionPlantaAbastecimientoModel.builder()
                .id(202L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("5000"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:601").build();

        when(cadenaMock.resolver("PLANTA_ABASTECIMIENTO", 201L)).thenReturn(
                CadenaCustodiaService.Cadena.builder()
                        .origenes(List.of(
                                CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(501L).build(),
                                CadenaCustodiaService.OrigenRef.builder().tipo("ARMADOR").id(502L).build()))
                        .build());
        when(cadenaMock.resolver("PLANTA_ABASTECIMIENTO", 202L)).thenReturn(
                CadenaCustodiaService.Cadena.builder()
                        .origenes(List.of(
                                CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(501L).build()))
                        .build());
        when(recRepo.findAllById(java.util.Set.of(501L))).thenReturn(List.of(
                DeclaracionRecolectorModel.builder().id(501L).captura(new BigDecimal("6000.00")).build()));
        when(armRepo.findAllById(java.util.Set.of(502L))).thenReturn(List.of(
                DeclaracionArmadorModel.builder().id(502L).captura(4000.0).build()));

        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(101L).folioDeclaracionPpla("PPLA-QR6-2").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-14"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("2500")).build();

        RendimientoPlantaService.ResultadoRendimiento res = serviceConOrigen.evaluarYActualizar(
                decl, List.of(loteDirecto, loteCom), new java.util.Date());

        assertEquals(new BigDecimal("10000.00"), decl.getCapturaOrigenKg());
        assertTrue(Boolean.TRUE.equals(decl.getCapturaOrigenCompleta()));
        assertEquals(new BigDecimal("25.00"), decl.getRendimientoFinalPct());
        assertFalse(res.anomalo());
    }

    @Test
    @DisplayName("QR.6: factor Seco 3.58 (27.93% max, 29.33% tolerado) detecta anomalía sobre captura origen aunque proceso sea normal")
    void qr6_secoFactor35800_anomaloSobreCapturaOrigenAunqueProcesoNormal() {
        when(configService.getValor(eq("rendimiento_base"), anyString())).thenReturn("CAPTURA_ORIGEN");
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any()))
                .thenReturn(Optional.of(factor(new BigDecimal("3.5800"))));

        // Lote con masa en planta 12000 kg -> 3000/12000 = 25.00% en proceso, pero 3000/10000 = 30.00% sobre origen (> 29.33%)
        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(13L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("12000")).build();
        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(102L).folioDeclaracionPpla("PPLA-QR6-3").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-14"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("3000")).build();

        CapturaOrigenProduccion.ResultadoCapturaOrigen cap = new CapturaOrigenProduccion.ResultadoCapturaOrigen(
                new BigDecimal("10000.00"), true, 1, List.of("RECOLECTOR:99"), null);

        RendimientoPlantaService.ResultadoRendimiento res = service.evaluarYActualizar(
                decl, List.of(lote), cap, new java.util.Date());

        assertTrue(res.anomalo());
        assertEquals("EXCESO", res.tipoAnomalia());
        assertEquals(RendimientoPlantaService.BASE_CAPTURA_ORIGEN, res.baseAplicada());
        assertEquals(RendimientoPlantaService.FUENTE_FACTORES_ORIGEN, res.fuente());
        assertEquals(new BigDecimal("25.00"), res.rendimientoPct());
        assertEquals(new BigDecimal("30.00"), res.rendimientoFinalPct());
        assertEquals(new BigDecimal("27.93"), res.rendimientoMaxTeoricoPct());
        assertEquals("29.33", res.umbralCriterio());
        assertTrue(res.detalleAnomalia().contains("captura biológica de origen"));

        service.registrarHallazgosPostGuardado(decl, res);
        verify(hallazgoService).registrar(
                eq("PLANTA_PRODUCCION"), eq(102L), eq("RENDIMIENTO_ANOMALO"), eq("EXCESO"),
                any(CriterioHallazgo.class), contains("captura biológica de origen"),
                eq("VALIDACION"), eq(99L), isNull());
    }

    @Test
    @DisplayName("QR.6: aislamiento por base en rendimiento_rango ignora rango MASA_RECIBIDA bajo CAPTURA_ORIGEN y aplica rango CAPTURA_ORIGEN si existe")
    void qr6_rendimientoRangoAisladoPorBase_ignoraMasaRecibidaEnCapturaOrigen() {
        when(configService.getValor(eq("rendimiento_base"), anyString())).thenReturn("CAPTURA_ORIGEN");
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any()))
                .thenReturn(Optional.of(factor(new BigDecimal("3.5800"))));

        RendimientoRangoModel rangoMasa = RendimientoRangoModel.builder()
                .id(70L).base("MASA_RECIBIDA").especie(pelillo).humedadEntrada(humHumedo).producto(prodSeco)
                .maxPct(new BigDecimal("40.00")).activo(true).vigenciaInicio(Date.valueOf("2026-01-01")).build();
        lenient().when(rendimientoRangoRepository.findVigentePorBase(eq(1L), eq(1L), eq(10L), eq("MASA_RECIBIDA"), any()))
                .thenReturn(Optional.of(rangoMasa));

        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(14L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("10000")).build();
        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(103L).folioDeclaracionPpla("PPLA-QR6-4").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-14"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("3000")).build();
        CapturaOrigenProduccion.ResultadoCapturaOrigen cap = new CapturaOrigenProduccion.ResultadoCapturaOrigen(
                new BigDecimal("10000.00"), true, 1, List.of("RECOLECTOR:1"), null);

        // Sin rango CAPTURA_ORIGEN -> ignora el rango de MASA_RECIBIDA (40%) y cae a FACTORES_ORIGEN (27.93%) => anómalo
        RendimientoPlantaService.ResultadoRendimiento sinRangoOrigen = service.evaluarYActualizar(
                decl, List.of(lote), cap, new java.util.Date());
        assertEquals(RendimientoPlantaService.FUENTE_FACTORES_ORIGEN, sinRangoOrigen.fuente());
        assertTrue(sinRangoOrigen.anomalo());

        // Con rango CAPTURA_ORIGEN (max 35%) -> usa TABLA_ORIGEN => 30.00% normal
        RendimientoRangoModel rangoOrigen = RendimientoRangoModel.builder()
                .id(71L).base("CAPTURA_ORIGEN").especie(pelillo).humedadEntrada(humHumedo).producto(prodSeco)
                .minPct(new BigDecimal("20.00")).maxPct(new BigDecimal("35.00")).activo(true)
                .vigenciaInicio(Date.valueOf("2026-01-01")).build();
        when(rendimientoRangoRepository.findVigentePorBase(eq(1L), eq(1L), eq(10L), eq("CAPTURA_ORIGEN"), any()))
                .thenReturn(Optional.of(rangoOrigen));

        RendimientoPlantaService.ResultadoRendimiento conRangoOrigen = service.evaluarYActualizar(
                decl, List.of(lote), cap, new java.util.Date());
        assertEquals(RendimientoPlantaService.FUENTE_TABLA_ORIGEN, conRangoOrigen.fuente());
        assertEquals(new BigDecimal("35.00"), conRangoOrigen.rendimientoMaxTeoricoPct());
        assertFalse(conRangoOrigen.anomalo());
    }

    @Test
    @DisplayName("QR.6: cadena incompleta respalda en MASA_RECIBIDA (RESPALDO_MASA_FACTORES) y no emite marca de cadena incompleta")
    void qr6_cadenaIncompleta_respaldaEnMasaRecibidaYSinMarcaCadenaIncompleta() {
        when(configService.getValor(eq("rendimiento_base"), anyString())).thenReturn("CAPTURA_ORIGEN");
        when(factorConversionService.findFactorVigente(eq(1L), eq(1L), any()))
                .thenReturn(Optional.of(factor(new BigDecimal("1.00"))));
        when(factorConversionService.findFactorVigente(eq(1L), eq(3L), any()))
                .thenReturn(Optional.of(factor(new BigDecimal("3.5800"))));

        DeclaracionPlantaAbastecimientoModel lote = DeclaracionPlantaAbastecimientoModel.builder()
                .id(15L).especie(pelillo).humedadEstadoRecepcion(humHumedo).pesoRomanaKg(new BigDecimal("10000")).build();
        DeclaracionPlantaProduccionModel decl = DeclaracionPlantaProduccionModel.builder()
                .id(104L).folioDeclaracionPpla("PPLA-QR6-5").usuario(planta)
                .fechaProduccion(Date.valueOf("2026-11-14"))
                .materiaPrimaEspecie(pelillo).productoResultante(prodSeco)
                .cantidadProducto(new BigDecimal("2500")).build();

        CapturaOrigenProduccion.ResultadoCapturaOrigen capIncompleta = new CapturaOrigenProduccion.ResultadoCapturaOrigen(
                new BigDecimal("4000.00"), false, 1, List.of("RECOLECTOR:1"),
                CapturaOrigenProduccion.MOTIVO_LOTE_SIN_ORIGENES);

        RendimientoPlantaService.ResultadoRendimiento res = service.evaluarYActualizar(
                decl, List.of(lote), capIncompleta, new java.util.Date());

        assertFalse(Boolean.TRUE.equals(decl.getCapturaOrigenCompleta()));
        assertEquals(new BigDecimal("4000.00"), decl.getCapturaOrigenKg());
        assertEquals(new BigDecimal("62.50"), decl.getRendimientoFinalPct());
        assertEquals(new BigDecimal("25.00"), decl.getRendimientoPct());
        assertEquals(RendimientoPlantaService.BASE_MASA_RECIBIDA, res.baseAplicada());
        assertEquals(RendimientoPlantaService.FUENTE_RESPALDO_FACTORES, res.fuente());
        assertFalse(res.anomalo());

        service.registrarHallazgosPostGuardado(decl, res);
        verifyNoInteractions(hallazgoService);
    }
}

