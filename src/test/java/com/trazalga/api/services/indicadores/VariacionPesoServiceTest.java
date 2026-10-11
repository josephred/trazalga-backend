package com.trazalga.api.services.indicadores;

import com.trazalga.api.dto.DespachoVariacionDTO;
import com.trazalga.api.dto.OrigenInformativoDTO;
import com.trazalga.api.dto.ResultadoVariacionPesoDTO;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.FactorConversionService;
import com.trazalga.api.services.parametros.ParametrosService;
import com.trazalga.api.services.parametros.ParametrosVariacion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TV.6 · Pruebas de aceptación de VariacionPesoService con los casos acordados en la reunión del 09-10-2026.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VariacionPesoServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;
    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;
    @Mock
    private IDeclaracionRecolectorRepository recolectorRepository;
    @Mock
    private IDeclaracionArmadorRepository armadorRepository;
    @Mock
    private IDeclaracionAreaRepository areaRepository;
    @Mock
    private FactorConversionService factorConversionService;
    @Mock
    private ParametrosService parametrosService;

    private EvaluadorCoherenciaBiologica evaluadorCoherenciaBiologica;
    private VariacionPesoService service;

    private ParametrosVariacion paramsEstandar;
    private EspecieModel especieHuiroNegro;
    private HumedadEstadoModel humHumedo;
    private HumedadEstadoModel humSemiseco;
    private HumedadEstadoModel humSeco;

    @BeforeEach
    void setUp() {
        evaluadorCoherenciaBiologica = new EvaluadorCoherenciaBiologica();
        service = new VariacionPesoService(
                entityManager,
                plantaRepository,
                comercializadorRepository,
                recolectorRepository,
                armadorRepository,
                areaRepository,
                factorConversionService,
                parametrosService,
                evaluadorCoherenciaBiologica
        );

        paramsEstandar = new ParametrosVariacion(
                true,   // bioPerdidaActivo
                3,      // bioHumedoDiasMinimosTransito
                5.0,    // bioHumedoMermaMinimaPct
                3.0,    // bioSecoMermaMaximaPct
                false,  // variacionPesoAlertaEquivalente
                5.0,    // variacionPesoUmbralGeneralPct
                10.0    // riesgoVariacionRojoPct
        );
        when(parametrosService.variacion()).thenReturn(paramsEstandar);

        especieHuiroNegro = new EspecieModel();
        especieHuiroNegro.setId(1L);
        especieHuiroNegro.setNombre("Huiro negro");

        humHumedo = new HumedadEstadoModel();
        humHumedo.setId(1L);
        humHumedo.setNombre("HÚMEDO");

        humSemiseco = new HumedadEstadoModel();
        humSemiseco.setId(2L);
        humSemiseco.setNombre("SEMISECO");

        humSeco = new HumedadEstadoModel();
        humSeco.setId(3L);
        humSeco.setNombre("SECO");
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("Caso A: Despacho 2.400 kg húmedo -> Romana 2.330 kg (14h) -> Δ -2,9% VERDE")
    void testCasoA_Despacho2400_Romana2330_14Horas() {
        LocalDate hoy = LocalDate.of(2026, 10, 10);
        Date fSalida = toDate(hoy);
        Date fLlegada = toDate(hoy);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(100L);
        planta.setFolioDeclaracionAPla("DPA-100");
        planta.setPesoRomanaKg(new BigDecimal("2330"));
        planta.setFechaIngresoPlanta(fLlegada);
        planta.setHora("22:00");
        planta.setHumedadEstado(humHumedo);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(10L);
        com.setFolioDesembarqueAc("DC-10");
        com.setCantidad(new BigDecimal("2400"));
        com.setFechaTraslado(fSalida);
        com.setHora("08:00"); // 14 horas de diferencia
        com.setHumedadEstado(humHumedo);
        com.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com.setDeclaracionDestinatario(100L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(2400.0, res.getKgDespacho());
        assertEquals(2330.0, res.getKgRomana());
        assertEquals(-70.0, res.getDeltaKg());
        assertEquals(-2.9, res.getDeltaPct(), 0.05);
        assertEquals(14.0, res.getHorasTransitoPromedio(), 0.1);
        assertEquals(0.6, res.getDiasTransito(), 0.1);
        assertEquals("COMERCIALIZADOR_PLANTA", res.getTramo());
        assertEquals("VERDE", res.getSemaforo());
        assertFalse(res.isAlerta());
    }

    @Test
    @DisplayName("Caso B: Despacho 3.000 kg húmedo -> Romana 3.050 kg (4 días) -> Δ +1,7% ROJO (HUMEDO_SIN_MERMA)")
    void testCasoB_Despacho3000_Romana3050_4Dias() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 5);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(101L);
        planta.setPesoRomanaKg(new BigDecimal("3050"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("10:00");
        planta.setHumedadEstado(humHumedo);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(11L);
        com.setCantidad(new BigDecimal("3000"));
        com.setFechaTraslado(toDate(fSalidaLd));
        com.setHora("10:00"); // 4 días exactos
        com.setHumedadEstado(humHumedo);
        com.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com.setDeclaracionDestinatario(101L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(3000.0, res.getKgDespacho());
        assertEquals(3050.0, res.getKgRomana());
        assertEquals(50.0, res.getDeltaKg());
        assertEquals(1.7, res.getDeltaPct(), 0.05);
        assertEquals(4.0, res.getDiasTransito(), 0.1);
        assertEquals("ROJO", res.getSemaforo());
        assertEquals("HUMEDO_SIN_MERMA", res.getCriterio());
        assertTrue(res.isAlerta());
    }

    @Test
    @DisplayName("Caso C: Despacho 3.000 kg húmedo -> Romana 2.700 kg (4 días) -> Δ -10,0% VERDE (DESHIDRATACION_COHERENTE)")
    void testCasoC_Despacho3000_Romana2700_4Dias() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 5);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(102L);
        planta.setPesoRomanaKg(new BigDecimal("2700"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("12:00");
        planta.setHumedadEstado(humHumedo);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(12L);
        com.setCantidad(new BigDecimal("3000"));
        com.setFechaTraslado(toDate(fSalidaLd));
        com.setHora("12:00");
        com.setHumedadEstado(humHumedo);
        com.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com.setDeclaracionDestinatario(102L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(3000.0, res.getKgDespacho());
        assertEquals(2700.0, res.getKgRomana());
        assertEquals(-300.0, res.getDeltaKg());
        assertEquals(-10.0, res.getDeltaPct(), 0.05);
        assertEquals(4.0, res.getDiasTransito(), 0.1);
        assertEquals("VERDE", res.getSemaforo());
        assertEquals("DESHIDRATACION_COHERENTE", res.getCriterio());
        assertFalse(res.isAlerta());
    }

    @Test
    @DisplayName("Caso D1: Despacho 2.000 kg seco -> Romana 1.880 kg (6 días) -> Δ -6,0% AMARILLO (SECO_MERMA_EXCESIVA)")
    void testCasoD1_Despacho2000Seco_Romana1880_6Dias() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 7);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(103L);
        planta.setPesoRomanaKg(new BigDecimal("1880"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("08:00");
        planta.setHumedadEstado(humSeco);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(13L);
        com.setCantidad(new BigDecimal("2000"));
        com.setFechaTraslado(toDate(fSalidaLd));
        com.setHora("08:00");
        com.setHumedadEstado(humSeco);
        com.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com.setDeclaracionDestinatario(103L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(2000.0, res.getKgDespacho());
        assertEquals(1880.0, res.getKgRomana());
        assertEquals(-120.0, res.getDeltaKg());
        assertEquals(-6.0, res.getDeltaPct(), 0.05);
        assertEquals(6.0, res.getDiasTransito(), 0.1);
        assertEquals("AMARILLO", res.getSemaforo());
        assertEquals("SECO_MERMA_EXCESIVA", res.getCriterio());
        assertTrue(res.isAlerta());
    }

    @Test
    @DisplayName("Caso D2: Despacho 2.000 kg seco -> Romana 1.750 kg (6 días) -> Δ -12,5% ROJO")
    void testCasoD2_Despacho2000Seco_Romana1750_6Dias() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 7);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(104L);
        planta.setPesoRomanaKg(new BigDecimal("1750"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("08:00");
        planta.setHumedadEstado(humSeco);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(14L);
        com.setCantidad(new BigDecimal("2000"));
        com.setFechaTraslado(toDate(fSalidaLd));
        com.setHora("08:00");
        com.setHumedadEstado(humSeco);
        com.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com.setDeclaracionDestinatario(104L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(2000.0, res.getKgDespacho());
        assertEquals(1750.0, res.getKgRomana());
        assertEquals(-250.0, res.getDeltaKg());
        assertEquals(-12.5, res.getDeltaPct(), 0.05);
        assertEquals(6.0, res.getDiasTransito(), 0.1);
        assertEquals("ROJO", res.getSemaforo());
        assertEquals("SECO_MERMA_EXCESIVA", res.getCriterio());
        assertTrue(res.isAlerta());
    }

    @Test
    @DisplayName("Caso E: 1.200 + 800 kg húmedo (2 comercializadores) -> Romana 1.970 kg (1 día) -> Δ -1,5% VERDE, con orígenes informativos")
    void testCasoE_DosComercializadores_MultiplesOrigenesInformativos() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 2);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(105L);
        planta.setPesoRomanaKg(new BigDecimal("1970"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("10:00");
        planta.setHumedadEstado(humHumedo);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com1 = new DeclaracionComercializadorModel();
        com1.setId(21L);
        com1.setCantidad(new BigDecimal("1200"));
        com1.setFechaTraslado(toDate(fSalidaLd));
        com1.setHora("10:00");
        com1.setHumedadEstado(humHumedo);
        com1.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com1.setDeclaracionDestinatario(105L);

        DeclaracionComercializadorModel com2 = new DeclaracionComercializadorModel();
        com2.setId(22L);
        com2.setCantidad(new BigDecimal("800"));
        com2.setFechaTraslado(toDate(fSalidaLd));
        com2.setHora("10:00");
        com2.setHumedadEstado(humHumedo);
        com2.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com2.setDeclaracionDestinatario(105L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com1, com2));
        when(plantaRepository.findById(105L)).thenReturn(Optional.of(planta));

        // Simulamos que el comercializador 21 tiene 2 orígenes asignados
        DeclaracionRecolectorModel orig1 = new DeclaracionRecolectorModel();
        orig1.setId(301L);
        orig1.setDesembarque(new BigDecimal("700"));
        orig1.setConsumidaPorTipo("COMERCIALIZADOR");
        orig1.setDeclaracionDestinatario(21L);
        orig1.setHumedadEstado(humHumedo);

        DeclaracionRecolectorModel orig2 = new DeclaracionRecolectorModel();
        orig2.setId(302L);
        orig2.setDesembarque(new BigDecimal("500"));
        orig2.setConsumidaPorTipo("COMERCIALIZADOR");
        orig2.setDeclaracionDestinatario(21L);
        orig2.setHumedadEstado(humHumedo);

        when(comercializadorRepository.findById(21L)).thenReturn(Optional.of(com1));
        when(comercializadorRepository.findById(22L)).thenReturn(Optional.of(com2));
        when(recolectorRepository.findAll()).thenReturn(List.of(orig1, orig2));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(2000.0, res.getKgDespacho());
        assertEquals(1970.0, res.getKgRomana());
        assertEquals(-30.0, res.getDeltaKg());
        assertEquals(-1.5, res.getDeltaPct(), 0.05);
        assertEquals(1.0, res.getDiasTransito(), 0.1);
        assertEquals("COMERCIALIZADOR_PLANTA", res.getTramo());
        assertEquals("VERDE", res.getSemaforo());

        // Verificar obtención de orígenes informativos bajo demanda
        List<OrigenInformativoDTO> origenes = service.obtenerOrigenesInformativos(105L);
        assertEquals(2, origenes.size());
        assertEquals("informativo: no se compara", origenes.get(0).getLeyendaInformativa());
    }

    @Test
    @DisplayName("Caso F: Venta directa: recolector 1.000 kg húmedo -> Romana 960 kg (1 día) -> Tramo DIRECTO · Δ -4,0% VERDE")
    void testCasoF_VentaDirecta_Recolector1000_Romana960_TramoDirecto() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 2);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(106L);
        planta.setPesoRomanaKg(new BigDecimal("960"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("12:00");
        planta.setHumedadEstado(humHumedo);
        planta.setEspecie(especieHuiroNegro);

        DeclaracionRecolectorModel rec = new DeclaracionRecolectorModel();
        rec.setId(401L);
        rec.setFolioDesembarqueRo("RO-401");
        rec.setDesembarque(new BigDecimal("1000"));
        rec.setFechaDeclaracion(toDate(fSalidaLd));
        rec.setHora("12:00");
        rec.setHumedadEstado(humHumedo);
        rec.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        rec.setDeclaracionDestinatario(106L);

        when(recolectorRepository.findAll()).thenReturn(List.of(rec));

        ResultadoVariacionPesoDTO res = service.evaluarRecepcion(planta);

        assertNotNull(res);
        assertEquals(1000.0, res.getKgDespacho());
        assertEquals(960.0, res.getKgRomana());
        assertEquals(-40.0, res.getDeltaKg());
        assertEquals(-4.0, res.getDeltaPct(), 0.05);
        assertEquals("DIRECTO", res.getTramo());
        assertEquals("VERDE", res.getSemaforo());
        assertFalse(res.isAlerta());
    }

    @Test
    @DisplayName("Caso G: 3.000 kg húmedo (3.390 kg captura) -> Romana 1.300 kg semiseco (3.510 kg captura) (5 días) -> Δ -56,7%, Δeq +3,5% VERDE")
    void testCasoG_HumedoASemisecoConFactoresEquivalentes() {
        LocalDate fSalidaLd = LocalDate.of(2026, 10, 1);
        LocalDate fLlegadaLd = LocalDate.of(2026, 10, 6);

        DeclaracionPlantaAbastecimientoModel planta = new DeclaracionPlantaAbastecimientoModel();
        planta.setId(107L);
        planta.setPesoRomanaKg(new BigDecimal("1300"));
        planta.setFechaIngresoPlanta(toDate(fLlegadaLd));
        planta.setHora("10:00");
        planta.setHumedadEstado(humHumedo);
        planta.setHumedadEstadoRecepcion(humSemiseco); // Cambio de humedad en recepción verificado
        planta.setEspecie(especieHuiroNegro);

        DeclaracionComercializadorModel com = new DeclaracionComercializadorModel();
        com.setId(51L);
        com.setCantidad(new BigDecimal("3000"));
        com.setFechaTraslado(toDate(fSalidaLd));
        com.setHora("10:00");
        com.setHumedadEstado(humHumedo);
        com.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        com.setDeclaracionDestinatario(107L);

        when(comercializadorRepository.findAll()).thenReturn(List.of(com));

        // Factores: Húmedo = 1.13 -> 3.000 * 1.13 = 3.390 kg captura
        //           Semiseco = 2.70 -> 1.300 * 2.70 = 3.510 kg captura
        FactorConversionModel fHum = new FactorConversionModel();
        fHum.setFactor(new BigDecimal("1.13"));
        when(factorConversionService.findFactorVigente(eq(1L), eq(1L), any())).thenReturn(Optional.of(fHum));

        FactorConversionModel fSemi = new FactorConversionModel();
        fSemi.setFactor(new BigDecimal("2.70"));
        when(factorConversionService.findFactorVigente(eq(1L), eq(2L), any())).thenReturn(Optional.of(fSemi));

        // 1. Modo por defecto (sin alerta equivalente activa): evalúa merma en húmedo tras 5 días (56,7% >= 5% -> VERDE)
        ResultadoVariacionPesoDTO res1 = service.evaluarRecepcion(planta);
        assertNotNull(res1);
        assertEquals(3000.0, res1.getKgDespacho());
        assertEquals(1300.0, res1.getKgRomana());
        assertEquals(-1700.0, res1.getDeltaKg());
        assertEquals(-56.7, res1.getDeltaPct(), 0.1);
        assertEquals(3390.0, res1.getCapturaDespachoEq(), 0.5);
        assertEquals(3510.0, res1.getCapturaRecepcionEq(), 0.5);
        assertEquals(3.5, res1.getDeltaEqPct(), 0.1);
        assertEquals("VERDE", res1.getSemaforo());

        // 2. Modo con alerta equivalente activa: estrategia Equivalente evalúa sobre Δeq% (+3,5% <= 5% -> VERDE)
        ParametrosVariacion paramsConEquiv = new ParametrosVariacion(
                true, 3, 5.0, 3.0, true, 5.0, 10.0
        );
        when(parametrosService.variacion()).thenReturn(paramsConEquiv);

        ResultadoVariacionPesoDTO res2 = service.evaluarRecepcion(planta);
        assertEquals("VERDE", res2.getSemaforo());
        assertEquals("CAMBIO_HUMEDAD_COHERENTE", res2.getCriterio());
    }
}
