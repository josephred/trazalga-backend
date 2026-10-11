package com.trazalga.api.services;

import com.trazalga.api.dto.TrazabilidadResponseDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadenaCustodiaServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ConfiguracionGeneralService configService;

    private CadenaCustodiaService service;

    @BeforeEach
    void setUp() {
        service = new CadenaCustodiaService(entityManager, configService);

        lenient().when(configService.getInt("retencion_humedo_max_horas", 24)).thenReturn(24);
        lenient().when(configService.getInt("retencion_semihumedo_max_horas", 72)).thenReturn(72);
        lenient().when(configService.getInt("retencion_semiseco_max_horas", 216)).thenReturn(216);
        lenient().when(configService.getInt("retencion_preaviso_pct", 80)).thenReturn(80);
    }

    private Object[] buildMockRow(Long id, String nombres, String apellidop, String rut,
                                  Date fecha, BigDecimal cantidad, String folio,
                                  Long destId, String destTipo, String decSel,
                                  String hora, Long uDestId,
                                  Long espId, String espNom,
                                  Long humId, String humNom,
                                  String rutDest, String nomDest) {
        Object[] row = new Object[39];
        row[0] = id;
        row[1] = nombres;
        row[2] = apellidop;
        row[3] = rut;
        row[4] = fecha;
        row[5] = cantidad;
        row[6] = folio;
        row[7] = destId;
        row[8] = destTipo;
        row[9] = decSel;
        row[10] = hora;
        row[11] = uDestId;
        row[12] = espId;
        row[13] = espNom;
        row[14] = humId;
        row[15] = humNom;
        row[37] = rutDest;
        row[38] = nomDest;
        return row;
    }

    @Test
    @DisplayName("T2.7: Directo a planta - Origen va directo a planta de abastecimiento sin comercializador")
    void testDirectoAPlanta() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 1, 8, 0, 0);
        Date fechaOrigen = cal.getTime();

        cal.set(2026, Calendar.OCTOBER, 1, 14, 0, 0);
        Date fechaPlanta = cal.getTime();

        Query qRecolector = mock(Query.class);
        Object[] rowRec = buildMockRow(
                10L, "Juan", "Perez", "11.111.111-1",
                fechaOrigen, new BigDecimal("1200.00"), "RO-001",
                50L, "PLANTA_ABASTECIMIENTO", null,
                "08:00:00", 99L,
                1L, "Huiro Negro", 1L, "HÚMEDO",
                "77.777.777-7", "Planta del Norte"
        );
        when(qRecolector.setParameter(eq("ids"), any())).thenReturn(qRecolector);
        when(qRecolector.getResultList()).thenReturn(Collections.singletonList(rowRec));

        Query qPlanta = mock(Query.class);
        Object[] rowPlanta = buildMockRow(
                50L, "Planta", "Norte", "77.777.777-7",
                fechaPlanta, new BigDecimal("1180.00"), "A-PLA-050",
                null, null, "RECOLECTOR:10",
                "14:00:00", null,
                1L, "Huiro Negro", 1L, "HÚMEDO",
                null, null
        );
        when(qPlanta.setParameter(eq("ids"), any())).thenReturn(qPlanta);
        when(qPlanta.getResultList()).thenReturn(Collections.singletonList(rowPlanta));

        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRecolector);
        when(entityManager.createNativeQuery(contains("declaracion_planta_abastecimiento"))).thenReturn(qPlanta);

        CadenaCustodiaService.Cadena cadena = service.resolver("RECOLECTOR", 10L);

        assertNotNull(cadena);
        assertEquals(1, cadena.getOrigenes().size());
        assertEquals(10L, cadena.getOrigenes().get(0).getId());
        assertEquals("RECOLECTOR", cadena.getOrigenes().get(0).getTipo());
        assertTrue(cadena.getComercializadorIds().isEmpty());
        assertTrue(cadena.getComercializadoresTramos().isEmpty());
        assertEquals(50L, cadena.getPlantaAbastecimientoId());
        assertEquals(2, cadena.getNodos().size());
        assertEquals(1, cadena.getEnlaces().size());
        assertEquals("RECOLECTOR:10", cadena.getEnlaces().get(0).getSource());
        assertEquals("PLANTA_ABASTECIMIENTO:50", cadena.getEnlaces().get(0).getTarget());
    }

    @Test
    @DisplayName("T2.7: Un comercializador - Origen -> Comercializador -> Planta de abastecimiento con horas en bodega")
    void testUnComercializador() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 1, 8, 0, 0);
        Date fechaOrigen = cal.getTime();

        cal.set(2026, Calendar.OCTOBER, 1, 20, 0, 0);
        Date fechaCom = cal.getTime();

        cal.set(2026, Calendar.OCTOBER, 2, 10, 0, 0);
        Date fechaPlanta = cal.getTime();

        // 1. Recolector
        Query qRec = mock(Query.class);
        Object[] rowRec = buildMockRow(
                1L, "Carlos", "Alga", "12.345.678-9",
                fechaOrigen, new BigDecimal("1000.00"), "RO-101",
                20L, "COMERCIALIZADOR", null,
                "08:00:00", 55L,
                1L, "Huiro Negro", 1L, "HÚMEDO",
                "88.888.888-8", "Comercializadora Costa"
        );
        when(qRec.setParameter(eq("ids"), any())).thenReturn(qRec);
        when(qRec.getResultList()).thenReturn(Collections.singletonList(rowRec));

        // 2. Comercializador
        Query qCom = mock(Query.class);
        Object[] rowCom = buildMockRow(
                20L, "Comercializadora", "Costa", "88.888.888-8",
                fechaCom, new BigDecimal("1000.00"), "AC-202",
                300L, "PLANTA_ABASTECIMIENTO", "RECOLECTOR:1",
                "20:00:00", 99L,
                null, null, null, null,
                "99.999.999-9", "Planta Bahía"
        );
        when(qCom.setParameter(eq("ids"), any())).thenReturn(qCom);
        when(qCom.getResultList()).thenReturn(Collections.singletonList(rowCom));

        // 3. Planta Abastecimiento
        Query qPlanta = mock(Query.class);
        Object[] rowPlanta = buildMockRow(
                300L, "Planta", "Bahía", "99.999.999-9",
                fechaPlanta, new BigDecimal("980.00"), "A-PLA-300",
                null, null, "COMERCIALIZADOR:20",
                "10:00:00", null,
                1L, "Huiro Negro", 1L, "HÚMEDO",
                null, null
        );
        when(qPlanta.setParameter(eq("ids"), any())).thenReturn(qPlanta);
        when(qPlanta.getResultList()).thenReturn(Collections.singletonList(rowPlanta));

        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);
        when(entityManager.createNativeQuery(contains("declaracion_comercializador"))).thenReturn(qCom);
        when(entityManager.createNativeQuery(contains("declaracion_planta_abastecimiento"))).thenReturn(qPlanta);

        CadenaCustodiaService.Cadena cadena = service.resolver("RECOLECTOR", 1L);

        assertNotNull(cadena);
        assertEquals(1, cadena.getOrigenes().size());
        assertEquals(List.of(20L), cadena.getComercializadorIds());
        assertEquals(300L, cadena.getPlantaAbastecimientoId());

        assertEquals(1, cadena.getComercializadoresTramos().size());
        CadenaCustodiaService.ComercializadorTramo tramo = cadena.getComercializadoresTramos().get(0);
        assertEquals(1, tramo.getSalto());
        assertEquals(20L, tramo.getId());
        assertTrue(tramo.isDespachado());
        // 08:00 a 20:00 el mismo día = exactamente 12.0 horas
        assertEquals(12.0, tramo.getHorasEnBodega(), 0.01);
        assertEquals("VERDE", tramo.getSemaforo());
    }

    @Test
    @DisplayName("T2.7: Dos comercializadores - Origen -> Com 1 -> Com 2 -> Planta con orden de saltos")
    void testDosComercializadores() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 1, 8, 0, 0);
        Date fechaOrigen = cal.getTime();

        cal.set(2026, Calendar.OCTOBER, 1, 16, 0, 0);
        Date fechaCom1 = cal.getTime();

        cal.set(2026, Calendar.OCTOBER, 4, 18, 0, 0);
        Date fechaCom2 = cal.getTime();

        cal.set(2026, Calendar.OCTOBER, 5, 10, 0, 0);
        Date fechaPlanta = cal.getTime();

        // 1. Armador
        Query qArm = mock(Query.class);
        Object[] rowArm = buildMockRow(
                100L, "Pedro", "Armador", "13.456.789-0",
                fechaOrigen, new BigDecimal("3000.00"), "DA-100",
                200L, "COMERCIALIZADOR", null,
                "08:00:00", 55L,
                1L, "Huiro Negro", 2L, "SEMI HÚMEDO",
                "88.888.888-8", "Comercializador Intermedio 1"
        );
        when(qArm.setParameter(eq("ids"), any())).thenReturn(qArm);
        when(qArm.getResultList()).thenReturn(Collections.singletonList(rowArm));

        // 2. Comercializador 1 y 2
        Query qCom = mock(Query.class);
        Object[] rowCom1 = buildMockRow(
                200L, "Comercializador", "Uno", "88.888.888-8",
                fechaCom1, new BigDecimal("3000.00"), "AC-200",
                300L, "COMERCIALIZADOR", "ARMADOR:100",
                "16:00:00", 77L,
                null, null, null, null,
                "77.777.777-7", "Comercializador Dos"
        );
        Object[] rowCom2 = buildMockRow(
                300L, "Comercializador", "Dos", "77.777.777-7",
                fechaCom2, new BigDecimal("2900.00"), "AC-300",
                400L, "PLANTA_ABASTECIMIENTO", "COMERCIALIZADOR:200",
                "18:00:00", 99L,
                null, null, null, null,
                "99.999.999-9", "Planta Abastecedora"
        );

        List<Long> capturedComIds = new ArrayList<>();
        when(qCom.setParameter(eq("ids"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Collection<Long> idsArg = (Collection<Long>) inv.getArgument(1);
            capturedComIds.clear();
            capturedComIds.addAll(idsArg);
            return qCom;
        });
        when(qCom.getResultList()).thenAnswer(inv -> {
            List<Object[]> res = new ArrayList<>();
            if (capturedComIds.contains(200L)) res.add(rowCom1);
            if (capturedComIds.contains(300L)) res.add(rowCom2);
            return res;
        });

        // 3. Planta
        Query qPlanta = mock(Query.class);
        Object[] rowPlanta = buildMockRow(
                400L, "Planta", "Abastecedora", "99.999.999-9",
                fechaPlanta, new BigDecimal("2850.00"), "A-PLA-400",
                null, null, "COMERCIALIZADOR:300",
                "10:00:00", null,
                1L, "Huiro Negro", 2L, "SEMI HÚMEDO",
                null, null
        );
        when(qPlanta.setParameter(eq("ids"), any())).thenReturn(qPlanta);
        when(qPlanta.getResultList()).thenReturn(Collections.singletonList(rowPlanta));

        when(entityManager.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm);
        when(entityManager.createNativeQuery(contains("declaracion_comercializador"))).thenReturn(qCom);
        when(entityManager.createNativeQuery(contains("declaracion_planta_abastecimiento"))).thenReturn(qPlanta);

        CadenaCustodiaService.Cadena cadena = service.resolver("ARMADOR", 100L);

        assertNotNull(cadena);
        assertEquals(1, cadena.getOrigenes().size());
        // Comercializadores en orden de saltos: 200 y luego 300
        assertEquals(List.of(200L, 300L), cadena.getComercializadorIds());
        assertEquals(400L, cadena.getPlantaAbastecimientoId());

        assertEquals(2, cadena.getComercializadoresTramos().size());
        CadenaCustodiaService.ComercializadorTramo tramo1 = cadena.getComercializadoresTramos().get(0);
        assertEquals(1, tramo1.getSalto());
        assertEquals(200L, tramo1.getId());
        // 08:00 a 16:00 = 8.0 horas
        assertEquals(8.0, tramo1.getHorasEnBodega(), 0.01);
        assertEquals("VERDE", tramo1.getSemaforo());

        CadenaCustodiaService.ComercializadorTramo tramo2 = cadena.getComercializadoresTramos().get(1);
        assertEquals(2, tramo2.getSalto());
        assertEquals(300L, tramo2.getId());
        // Desde 01-10 16:00 hasta 04-10 18:00 = 3 días (72h) + 2h = 74.0 horas
        assertEquals(74.0, tramo2.getHorasEnBodega(), 0.01);
        // Para SEMI HÚMEDO, plazo máximo es 72 horas -> 74h es ROJO
        assertEquals("ROJO", tramo2.getSemaforo());
    }

    @Test
    @DisplayName("T2.7: Sin despachar - Carga recibida por comercializador pero aún no despachada")
    void testSinDespachar() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 1, 8, 0, 0);
        Date fechaOrigen = cal.getTime();

        Query qRec = mock(Query.class);
        // Recolector con usuario_destinatario pero sin declaracion_destinatario_id
        Object[] rowRec = buildMockRow(
                50L, "Luis", "Recolector", "14.567.890-1",
                fechaOrigen, new BigDecimal("1500.00"), "RO-050",
                null, null, null,
                "08:00:00", 77L,
                1L, "Huiro Negro", 1L, "HÚMEDO",
                "88.888.888-8", "Comercializador En Bodega"
        );
        when(qRec.setParameter(eq("ids"), any())).thenReturn(qRec);
        when(qRec.getResultList()).thenReturn(Collections.singletonList(rowRec));

        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);

        CadenaCustodiaService.Cadena cadena = service.resolver("RECOLECTOR", 50L);

        assertNotNull(cadena);
        assertEquals(1, cadena.getOrigenes().size());
        assertTrue(cadena.getComercializadorIds().isEmpty());
        assertNull(cadena.getPlantaAbastecimientoId());

        // Debe generar el tramo en bodega virtual sin despachar
        assertEquals(1, cadena.getComercializadoresTramos().size());
        CadenaCustodiaService.ComercializadorTramo tramo = cadena.getComercializadoresTramos().get(0);
        assertEquals(1, tramo.getSalto());
        assertNull(tramo.getId());
        assertFalse(tramo.isDespachado());
        assertNull(tramo.getSalida());
        assertNotNull(tramo.getEntrada());
        assertTrue(tramo.getHorasEnBodega() > 0.0);
        assertEquals("Comercializador En Bodega", tramo.getActor());
    }

    @Test
    @DisplayName("T2.1: Cálculo preciso de horasEnBodega y evaluación de semáforo Res. 3602")
    void testHorasEnBodegaYSemaforoRes3602() {
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 1, 8, 0, 0);
        LocalDateTime salida12h = LocalDateTime.of(2026, 10, 1, 20, 0, 0);
        LocalDateTime salida20h = LocalDateTime.of(2026, 10, 2, 4, 0, 0);
        LocalDateTime salida30h = LocalDateTime.of(2026, 10, 2, 14, 0, 0);

        assertEquals(12.0, service.horasEnBodega(entrada, salida12h));
        assertEquals(20.0, service.horasEnBodega(entrada, salida20h));
        assertEquals(30.0, service.horasEnBodega(entrada, salida30h));
        assertEquals(0.0, service.horasEnBodega(null, salida12h));

        // Húmedo: max 24h, preaviso 80% = 19h
        assertEquals("VERDE", service.calcularSemaforo(12.0, "HÚMEDO"));
        assertEquals("AMARILLO", service.calcularSemaforo(20.0, "HÚMEDO"));
        assertEquals("ROJO", service.calcularSemaforo(30.0, "HÚMEDO"));

        // Seco: plazo indefinido -> siempre VERDE
        assertEquals("VERDE", service.calcularSemaforo(500.0, "SECO"));

        // Semi Seco: max 216h, preaviso 80% = 173h
        assertEquals("VERDE", service.calcularSemaforo(100.0, "SEMI SECO"));
        assertEquals("AMARILLO", service.calcularSemaforo(180.0, "SEMI SECO"));
        assertEquals("ROJO", service.calcularSemaforo(220.0, "SEMI SECO"));
    }

    @Test
    @DisplayName("T2.1: Normalización de tipos y estados de humedad")
    void testNormalizaciones() {
        assertEquals("RECOLECTOR", service.normalizarTipo(1));
        assertEquals("ARMADOR", service.normalizarTipo(2));
        assertEquals("AREA", service.normalizarTipo(3));
        assertEquals("COMERCIALIZADOR", service.normalizarTipo(4));
        assertEquals("PLANTA_ABASTECIMIENTO", service.normalizarTipo(5));
        assertEquals("PLANTA_PRODUCCION", service.normalizarTipo(6));
        assertEquals("PLANTA_DESTINO", service.normalizarTipo(7));

        assertEquals("RECOLECTOR", service.normalizarTipo("RO"));
        assertEquals("ARMADOR", service.normalizarTipo("DA"));
        assertEquals("AREA", service.normalizarTipo("AMERB"));
        assertEquals("COMERCIALIZADOR", service.normalizarTipo("AC"));
        assertEquals("PLANTA_ABASTECIMIENTO", service.normalizarTipo("A-PLA"));
        assertEquals("PLANTA_PRODUCCION", service.normalizarTipo("P-PLA"));

        assertEquals("HÚMEDO", service.normalizarEstadoHumedad("Húmedo"));
        assertEquals("SEMI HÚMEDO", service.normalizarEstadoHumedad("semi humedo"));
        assertEquals("SEMI SECO", service.normalizarEstadoHumedad("Semi-Seco"));
        assertEquals("SECO", service.normalizarEstadoHumedad("seco"));
    }

    @Test
    @DisplayName("T2.1: getTrazabilidad devuelve DTO idéntico con nodos y enlaces")
    void testGetTrazabilidadRetornaDTO() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.OCTOBER, 1, 8, 0, 0);
        Date fecha = cal.getTime();

        Query qRec = mock(Query.class);
        Object[] rowRec = buildMockRow(
                10L, "Juan", "Perez", "11.111.111-1",
                fecha, new BigDecimal("1000.00"), "RO-001",
                null, null, null,
                "08:00:00", null,
                1L, "Huiro Negro", 1L, "HÚMEDO",
                null, null
        );
        when(qRec.setParameter(eq("ids"), any())).thenReturn(qRec);
        when(qRec.getResultList()).thenReturn(Collections.singletonList(rowRec));
        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);

        TrazabilidadResponseDTO dto = service.getTrazabilidad(1, 10L);

        assertNotNull(dto);
        assertEquals(1, dto.getNodos().size());
        assertEquals("RECOLECTOR:10", dto.getNodos().get(0).getIdUnico());
        assertEquals("Juan Perez", dto.getNodos().get(0).getNombreActor());
    }
}
