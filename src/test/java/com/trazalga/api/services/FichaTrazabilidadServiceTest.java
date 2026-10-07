package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.dto.*;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;

@ExtendWith(MockitoExtension.class)
public class FichaTrazabilidadServiceTest {

    @Mock
    private CadenaCustodiaService cadenaCustodiaService;

    @Mock
    private DeclaracionMarcaService declaracionMarcaService;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @Mock
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Mock
    private IDeclaracionArmadorRepository armadorRepository;

    @Mock
    private IDeclaracionAreaRepository areaRepository;

    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Mock
    private DeclaracionBuzosRepository buzosRepository;

    @Mock
    private FactorConversionService factorConversionService;

    @InjectMocks
    private FichaTrazabilidadService service;

    private CadenaCustodiaService.Cadena mockCadena;

    @BeforeEach
    void setUp() {
        lenient().when(declaracionMarcaService.getByDeclaracion(anyString(), anyLong()))
                .thenReturn(Collections.emptyList());
        lenient().when(bloqueoCargaService.bloqueosPorTipoEIds(anyString(), anyCollection()))
                .thenReturn(Collections.emptyMap());
    }

    /**
     * T2.3 Caso de Aceptación:
     * - Dos recolectores (1.000 y 1.500 kg en húmedo) declaran el 01-10 a las 08:00 y 09:00 a nombre de comercializador.
     * - Comercializador despacha el 01-10 a las 20:00 con guía 4455 y patente ABCD12.
     * - Planta recibe el 02-10 a las 10:00 con 2.300 kg en romana.
     * Resultado:
     * - Ficha del primer recolector muestra el segundo como "misma carga".
     * - Comercializador con recepción 01-10 08:00, despacho 01-10 20:00 y 12.0 h en bodega, en verde.
     * - Planta con 2.300 kg en romana y variación de -8.0%.
     */
    @Test
    @DisplayName("T2.3 Aceptación: Escenario completo 2 recolectores -> 1 comercializador -> planta (12.0h bodega, -8.0% romana)")
    void testAceptacion_EscenarioCompleto_FichaPrimerRecolector() {
        Long rec1Id = 1001L;
        Long rec2Id = 1002L;
        Long comId = 2001L;
        Long plaId = 3001L;

        LocalDateTime dtRec1 = LocalDateTime.of(2026, 10, 1, 8, 0, 0);
        LocalDateTime dtRec2 = LocalDateTime.of(2026, 10, 1, 9, 0, 0);
        LocalDateTime dtComSalida = LocalDateTime.of(2026, 10, 1, 20, 0, 0);

        // 1. Simular respuesta de CadenaCustodiaService
        mockCadena = CadenaCustodiaService.Cadena.builder()
                .tipoConsulta("RECOLECTOR")
                .idConsulta(rec1Id)
                .estadoHumedadPredominante("HÚMEDO")
                .origenes(Arrays.asList(
                        CadenaCustodiaService.OrigenRef.builder()
                                .tipo("RECOLECTOR").id(rec1Id).folio("RO-1001")
                                .timestamp(dtRec1).cantidad(new BigDecimal("1000.00"))
                                .especieNombre("Huiro negro").humedadNombre("Húmedo")
                                .build(),
                        CadenaCustodiaService.OrigenRef.builder()
                                .tipo("RECOLECTOR").id(rec2Id).folio("RO-1002")
                                .timestamp(dtRec2).cantidad(new BigDecimal("1500.00"))
                                .especieNombre("Huiro negro").humedadNombre("Húmedo")
                                .build()
                ))
                .comercializadoresTramos(Collections.singletonList(
                        CadenaCustodiaService.ComercializadorTramo.builder()
                                .id(comId)
                                .salto(1)
                                .entrada(dtRec1) // 08:00
                                .salida(dtComSalida) // 20:00
                                .horasEnBodega(12.0)
                                .semaforo("VERDE")
                                .despachado(true)
                                .build()
                ))
                .plantaAbastecimientoId(plaId)
                .build();

        when(cadenaCustodiaService.resolver("RECOLECTOR", rec1Id)).thenReturn(mockCadena);

        // 2. Mock entidades en repositorios
        UsuarioModel uRec1 = UsuarioModel.builder().id(11L).rut("11.111.111-1").nombres("Juan").apellidop("Pescador").build();
        UsuarioModel uRec2 = UsuarioModel.builder().id(12L).rut("12.122.122-2").nombres("Pedro").apellidop("Orilla").build();
        EspecieModel espHuiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        HumedadEstadoModel humHumedo = HumedadEstadoModel.builder().id(1L).nombre("Húmedo").build();

        DeclaracionRecolectorModel r1 = DeclaracionRecolectorModel.builder()
                .id(rec1Id).usuario(uRec1).desembarque(new BigDecimal("1000.00")).captura(new BigDecimal("1000.00"))
                .especie(espHuiro).humedadEstado(humHumedo)
                .folioOrigen("DUMMY-02OCT-REC-01").folioDesembarqueRo("RO-02OCT-1001")
                .fechaDeclaracion(java.sql.Date.valueOf("2026-10-01")).hora("08:00:00")
                .build();

        DeclaracionRecolectorModel r2 = DeclaracionRecolectorModel.builder()
                .id(rec2Id).usuario(uRec2).desembarque(new BigDecimal("1500.00")).captura(new BigDecimal("1500.00"))
                .especie(espHuiro).humedadEstado(humHumedo)
                .folioOrigen("DUMMY-02OCT-REC-02").folioDesembarqueRo("RO-02OCT-1002")
                .fechaDeclaracion(java.sql.Date.valueOf("2026-10-01")).hora("09:00:00")
                .build();

        when(recolectorRepository.findById(rec1Id)).thenReturn(Optional.of(r1));
        when(recolectorRepository.findById(rec2Id)).thenReturn(Optional.of(r2));

        UsuarioModel uCom = UsuarioModel.builder().id(21L).rut("76.999.888-1").nombres("Comercializadora Algas Ltda").build();
        DeclaracionComercializadorModel cModel = DeclaracionComercializadorModel.builder()
                .id(comId).usuario(uCom).cantidad(new BigDecimal("2500.00"))
                .documentoTributarioOrigenNumero("4455").documentoTributarioDestinoNumero("4455")
                .placaPatente("ABCD12")
                .fechaDeclaracion(java.sql.Date.valueOf("2026-10-01")).hora("20:00:00")
                .build();
        when(comercializadorRepository.findById(comId)).thenReturn(Optional.of(cModel));

        DeclaracionPlantaAbastecimientoModel pModel = DeclaracionPlantaAbastecimientoModel.builder()
                .id(plaId).nombrePlanta("Planta Biopacífico Ficha")
                .fechaIngresoPlanta(java.sql.Date.valueOf("2026-10-02")).hora("10:00:00")
                .pesoRomanaKg(new BigDecimal("2300.00")).voucherRomanaNumero("VCH-02OCT-9988")
                .fechaPesaje(java.sql.Date.valueOf("2026-10-02"))
                .humedadEstado(humHumedo)
                .build();
        when(plantaRepository.findById(plaId)).thenReturn(Optional.of(pModel));

        // 3. Ejecutar servicio
        FichaTrazabilidadDTO ficha = service.obtenerFicha("RECOLECTOR", rec1Id);

        // 4. Validar resultados de aceptación
        assertNotNull(ficha);

        // Orígenes: Recolector 1 marcado consultada=true; Recolector 2 mismaCarga=true
        assertEquals(2, ficha.getOrigenes().size());
        FichaOrigenDTO o1 = ficha.getOrigenes().get(0);
        assertTrue(o1.isConsultada(), "El primer origen debe ser el consultado");
        assertFalse(o1.isMismaCarga());
        assertEquals(rec1Id, o1.getId());
        assertEquals(new BigDecimal("1000.00"), o1.getDesembarqueKg());

        FichaOrigenDTO o2 = ficha.getOrigenes().get(1);
        assertFalse(o2.isConsultada());
        assertTrue(o2.isMismaCarga(), "El segundo origen debe aparecer como 'misma carga'");
        assertEquals(rec2Id, o2.getId());
        assertEquals(new BigDecimal("1500.00"), o2.getDesembarqueKg());

        // Total origen = 2500 kg
        assertEquals(new BigDecimal("2500.00"), ficha.getTotalKgOrigen());

        // Comercializador: recepción 08:00, despacho 20:00, 12.0h en bodega (VERDE)
        assertEquals(1, ficha.getComercializadores().size());
        FichaComercializadorDTO com = ficha.getComercializadores().get(0);
        assertEquals(comId, com.getId());
        assertEquals("08:00:00", com.getHoraRecepcion());
        assertEquals("20:00:00", com.getHoraDespacho());
        assertEquals(12.0, com.getHorasEnBodega(), 0.01);
        assertEquals("VERDE", com.getSemaforo());
        assertEquals("4455", com.getDocOrigenNumero());
        assertEquals("ABCD12", com.getPatenteCamion());

        // Planta: 2.300 kg en romana y variación de -8.0%
        assertNotNull(ficha.getPlanta());
        assertTrue(ficha.getPlanta().isRecepcionada());
        assertTrue(ficha.getPlanta().isConRomana());
        assertEquals(new BigDecimal("2300.00"), ficha.getPlanta().getPesoRomanaKg());
        assertEquals("VCH-02OCT-9988", ficha.getPlanta().getNumeroVoucherRomana());
        assertEquals(-8.0, ficha.getPlanta().getVariacionPct(), 0.01);
        assertEquals("Variación de la recepción completa", ficha.getPlanta().getRotuloVariacion());
    }

    @Test
    @DisplayName("T2.3: Consulta centrada en el Comercializador retorna la misma cadena")
    void testAceptacion_ConsultaCentradaEnComercializador() {
        Long comId = 2001L;

        mockCadena = CadenaCustodiaService.Cadena.builder()
                .tipoConsulta("COMERCIALIZADOR")
                .idConsulta(comId)
                .estadoHumedadPredominante("HÚMEDO")
                .origenes(Collections.singletonList(
                        CadenaCustodiaService.OrigenRef.builder()
                                .tipo("ARMADOR").id(501L).folio("DA-501")
                                .cantidad(new BigDecimal("3000.00"))
                                .build()
                ))
                .comercializadoresTramos(Collections.singletonList(
                        CadenaCustodiaService.ComercializadorTramo.builder()
                                .id(comId)
                                .salto(1)
                                .despachado(true)
                                .horasEnBodega(15.0)
                                .semaforo("VERDE")
                                .build()
                ))
                .plantaAbastecimientoId(null) // Aún no llega a planta
                .build();

        when(cadenaCustodiaService.resolver("COMERCIALIZADOR", comId)).thenReturn(mockCadena);

        FichaTrazabilidadDTO ficha = service.obtenerFicha("COMERCIALIZADOR", comId);

        assertNotNull(ficha);
        assertEquals("COMERCIALIZADOR", ficha.getTipoConsulta());
        assertEquals(comId, ficha.getIdConsulta());
        assertEquals(1, ficha.getOrigenes().size());
        assertEquals(1, ficha.getComercializadores().size());

        // Planta aún no recepcionada
        assertNotNull(ficha.getPlanta());
        assertFalse(ficha.getPlanta().isRecepcionada());
        assertEquals("Aún no recepcionada en planta", ficha.getPlanta().getMensajeEstado());
    }

    @Test
    @DisplayName("T2.3: Comercializador aún sin despachar expone tiempo en bodega hasta hoy")
    void testComercializadorSinDespachar_ExponeTiempoVirtual() {
        Long comDestinatarioId = 999L;
        LocalDateTime entrada = LocalDateTime.now().minusHours(24);

        mockCadena = CadenaCustodiaService.Cadena.builder()
                .tipoConsulta("RECOLECTOR")
                .idConsulta(101L)
                .origenes(Collections.emptyList())
                .comercializadoresTramos(Collections.singletonList(
                        CadenaCustodiaService.ComercializadorTramo.builder()
                                .id(null)
                                .salto(1)
                                .despachado(false)
                                .actor("Comercializador En Espera")
                                .rut("88.888.888-8")
                                .cantidad(new BigDecimal("1200.00"))
                                .entrada(entrada)
                                .horasEnBodega(24.0)
                                .semaforo("VERDE")
                                .build()
                ))
                .build();

        when(cadenaCustodiaService.resolver("RECOLECTOR", 101L)).thenReturn(mockCadena);

        FichaTrazabilidadDTO ficha = service.obtenerFicha("RECOLECTOR", 101L);

        assertEquals(1, ficha.getComercializadores().size());
        FichaComercializadorDTO c = ficha.getComercializadores().get(0);
        assertFalse(c.isDespachado());
        assertEquals("Comercializador En Espera", c.getNombre());
        assertEquals("88.888.888-8", c.getRut());
        assertEquals(24.0, c.getHorasEnBodega(), 0.1);
        assertEquals("Carga en bodega virtual; aún sin declaración de despacho", c.getNotaRecepcion());
    }

    @Test
    @DisplayName("T2.3: Alertas y retenciones activas se consolidan en el bloque superior")
    void testAlertasYRetencionesConsolidadas() {
        Long recId = 101L;

        mockCadena = CadenaCustodiaService.Cadena.builder()
                .tipoConsulta("RECOLECTOR")
                .idConsulta(recId)
                .origenes(Collections.singletonList(
                        CadenaCustodiaService.OrigenRef.builder()
                                .tipo("RECOLECTOR").id(recId).folio("RO-101")
                                .cantidad(new BigDecimal("500.00"))
                                .build()
                ))
                .build();

        when(cadenaCustodiaService.resolver("RECOLECTOR", recId)).thenReturn(mockCadena);

        // Mock marca activa EN_VEDA
        DeclaracionMarcaModel marcaVeda = DeclaracionMarcaModel.builder()
                .id(77L).declaracionTipo("RECOLECTOR").declaracionId(recId)
                .marca("EN_VEDA").detalle("Zona en veda biológica")
                .resuelta(false).build();
        when(declaracionMarcaService.getByDeclaracion("RECOLECTOR", recId))
                .thenReturn(Collections.singletonList(marcaVeda));

        // Mock retención activa
        MotivoBloqueoDTO bloqueo = MotivoBloqueoDTO.builder()
                .marca("EN_VEDA").marcaId(77L).detalle("Retención preventiva")
                .build();
        when(bloqueoCargaService.bloqueosPorTipoEIds(eq("RECOLECTOR"), anyCollection()))
                .thenReturn(Collections.singletonMap(recId, bloqueo));

        FichaTrazabilidadDTO ficha = service.obtenerFicha("RECOLECTOR", recId);

        assertNotNull(ficha.getAlertas());
        assertEquals(1, ficha.getAlertas().getTotalMarcasActivas());
        assertTrue(ficha.getAlertas().isHayCargasRetenidas());
        assertEquals(1, ficha.getAlertas().getMotivosRetencion().size());
        assertTrue(ficha.getAlertas().getMotivosRetencion().get(0).contains("EN_VEDA"));

        // El origen individual también refleja la retención
        assertTrue(ficha.getOrigenes().get(0).isRetenida());
        assertNotNull(ficha.getOrigenes().get(0).getMotivoBloqueo());
    }

    @Test
    @DisplayName("T2.3: Lanzar 404 si la cadena no existe o parámetros son nulos")
    void testValidacionParametros() {
        assertThrows(ResponseStatusException.class, () -> service.obtenerFicha(null, 10L));
        assertThrows(ResponseStatusException.class, () -> service.obtenerFicha("RECOLECTOR", null));
    }

    @Test
    @DisplayName("T3.3: La ficha muestra estado de origen, estado de recepción, variación física y variación equivalente")
    void testT33_FichaMuestraAmbosEstadosYAmbasVariaciones() {
        Long recId = 301L;
        Long plaId = 401L;
        Long espId = 1L;
        Date fecha = new Date();

        mockCadena = CadenaCustodiaService.Cadena.builder()
                .tipoConsulta("RECOLECTOR")
                .idConsulta(recId)
                .estadoHumedadPredominante("Húmedo")
                .plantaAbastecimientoId(plaId)
                .origenes(Collections.singletonList(
                        CadenaCustodiaService.OrigenRef.builder()
                                .tipo("RECOLECTOR").id(recId).folio("RO-301")
                                .cantidad(new BigDecimal("2500.00"))
                                .fecha(fecha)
                                .build()
                ))
                .build();
        when(cadenaCustodiaService.resolver("RECOLECTOR", recId)).thenReturn(mockCadena);

        EspecieModel especie = new EspecieModel().setId(espId).setNombre("Huiro palo");
        HumedadEstadoModel humOrigen = new HumedadEstadoModel().setId(1L).setNombre("Húmedo");
        HumedadEstadoModel humRecepcion = new HumedadEstadoModel().setId(2L).setNombre("Semihúmedo");

        DeclaracionRecolectorModel rec = new DeclaracionRecolectorModel();
        rec.setId(recId);
        rec.setFolioOrigen("RO-301");
        rec.setDesembarque(new BigDecimal("2500.00"));
        rec.setCaptura(new BigDecimal("2825.00")); // 2500 * 1.13
        rec.setEspecie(especie);
        rec.setHumedadEstado(humOrigen);
        when(recolectorRepository.findById(recId)).thenReturn(Optional.of(rec));

        DeclaracionPlantaAbastecimientoModel pla = DeclaracionPlantaAbastecimientoModel.builder()
                .id(plaId)
                .folioOrigen("PLA-401")
                .pesoRomanaKg(new BigDecimal("1600.00"))
                .voucherRomanaNumero("VCH-5544")
                .fechaIngresoPlanta(fecha)
                .especie(especie)
                .humedadEstado(humOrigen)
                .humedadEstadoRecepcion(humRecepcion)
                .build();
        when(plantaRepository.findById(plaId)).thenReturn(Optional.of(pla));

        // Factor para Huiro palo en Semihúmedo = 1.75 -> Captura planta = 1600 * 1.75 = 2800 kg
        FactorConversionModel fc = FactorConversionModel.builder()
                .factor(new BigDecimal("1.75"))
                .build();
        when(factorConversionService.findFactorVigente(eq(espId), eq(2L), any(Date.class)))
                .thenReturn(Optional.of(fc));

        FichaTrazabilidadDTO ficha = service.obtenerFicha("RECOLECTOR", recId);

        assertNotNull(ficha);
        assertNotNull(ficha.getPlanta());

        // Ambos estados
        assertEquals("Húmedo", ficha.getPlanta().getHumedadEstadoOrigen());
        assertEquals("Semihúmedo", ficha.getPlanta().getHumedadEstadoRecepcion());

        // Ambas variaciones
        // Física: (1600 - 2500) / 2500 * 100 = -36.0%
        assertEquals(-36.0, ficha.getPlanta().getVariacionPct(), 0.01);
        // Equivalente: (2800 - 2825) / 2825 * 100 = -0.9%
        assertNotNull(ficha.getPlanta().getVariacionEqPct());
        assertEquals(-0.9, ficha.getPlanta().getVariacionEqPct(), 0.01);

        // Capturas totales
        assertEquals(new BigDecimal("2825.00"), ficha.getPlanta().getCapturaOrigenTotal());
        assertEquals(new BigDecimal("2800.00"), ficha.getPlanta().getCapturaPlantaTotal());
    }
}
