package com.trazalga.api.services;

import com.trazalga.api.repositories.ReportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para T2.5 (Hallazgo K7):
 * - Enlace de trazabilidad por punteros y tokens (folios distintos por formulario).
 * - Coincidencia estricta de horas en bodega entre Indicador 7 y CadenaCustodiaService.
 * - Comparación de peso (merma/variación) calculada por despacho (kgBase = kg_origen_despacho).
 * - Consulta de control de comercializadores multisalto.
 */
@ExtendWith(MockitoExtension.class)
public class ReportServiceRetencionTokensTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private ReportRepository reportRepository;

    private ReportService reportService;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, configService);
    }

    /**
     * Construye una fila simulada con la estructura exacta devuelta por sqlTrazabilidadLoteBase (24 columnas).
     */
    private Object[] buildLoteRow(String folioOrigen, String eslabon, String actorOrig, String rutOrig,
                                  String especie, String humedad, Date fechaOrig, Double kgOrig,
                                  Double kgCom, Date fechaCom, String actorCom,
                                  Double kgPla, Date fechaPla, String actorPla,
                                  String horaOrig, String horaCom, String horaPla,
                                  Double kgOrigenDespacho, Long idCom, Long idPla) {
        Object[] row = new Object[24];
        row[0] = folioOrigen;             // orig.folio_origen
        row[1] = eslabon;                 // orig.eslabon_origen
        row[2] = actorOrig;               // orig.actor_origen
        row[3] = rutOrig;                 // orig.rut_origen
        row[4] = especie;                 // orig.especie
        row[5] = humedad;                 // orig.humedad_origen
        row[6] = fechaOrig;               // orig.fecha_origen
        row[7] = kgOrig;                  // orig.kg_origen
        row[8] = kgOrig;                  // orig.captura_origen
        row[9] = 1.0;                     // orig.factor_aplicado
        row[10] = null;                   // orig.embarcacion
        row[11] = kgCom;                  // c.cantidad as kg_comercializador
        row[12] = fechaCom;               // c.fecha_declaracion as fecha_comercializador
        row[13] = actorCom;               // actor_comercializador
        row[14] = kgPla;                  // kg_planta
        row[15] = fechaPla;               // fecha_planta
        row[16] = actorPla;               // actor_planta
        row[17] = null;                   // marcas_activas
        row[18] = horaOrig;               // orig.hora_origen
        row[19] = horaCom;                // c.hora
        row[20] = horaPla;                // hora_planta
        row[21] = kgOrigenDespacho;       // kg_origen_despacho (COALESCE tot_orig, orig.kg_origen)
        row[22] = idCom;                  // c.id
        row[23] = idPla;                  // p.id
        return row;
    }

    @Test
    @DisplayName("T2.5: Horas en bodega de Indicador 7 coinciden exactamente con CadenaCustodiaService.horasEnBodega (Escenario TX.3: 12.0 h)")
    void testRetencionHorasCoincidenConCadenaCustodia() throws Exception {
        Date fOrigen = dateFormat.parse("2026-10-01");
        Date fCom = dateFormat.parse("2026-10-01");
        Date fPla = dateFormat.parse("2026-10-02");

        // Escenario TX.3: Origen 08:00 -> Comercializador despacha a las 20:00 -> Planta llega al día siguiente a las 10:00
        Object[] rowRec1 = buildLoteRow(
                "RO-02OCT-1001", "RECOLECTOR", "Recolector Ficha 1", "11.111.111-1",
                "Huiro Negro", "HÚMEDO", fOrigen, 1000.0,
                2500.0, fCom, "Comercializadora Ficha",
                2300.0, fPla, "Planta Biopacífico",
                "08:00:00", "20:00:00", "10:00:00",
                2500.0, 20L, 30L
        );

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(rowRec1));

        Map<String, Object> result = reportRepository.getRetencionPorHumedad(null, null, 24, 72, 216, 80);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) result.get("lotes");

        assertNotNull(lotes);
        assertEquals(1, lotes.size());

        Map<String, Object> lote = lotes.get(0);
        assertEquals("RO-02OCT-1001", lote.get("folio"));
        assertEquals("HÚMEDO", lote.get("estadoDeclarado"));
        assertEquals("VERDE", lote.get("semaforo"));

        // Verificación de horas: 08:00 a 20:00 = 12 horas exactas
        assertEquals(12L, ((Number) lote.get("horasTranscurridas")).longValue());

        // Comprobación de coincidencia estricta con CadenaCustodiaService.horasEnBodega
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 1, 8, 0, 0);
        LocalDateTime salida = LocalDateTime.of(2026, 10, 1, 20, 0, 0);
        double horasServicio = CadenaCustodiaService.horasEnBodega(entrada, salida);

        assertEquals(12.0, horasServicio);
        assertEquals(Math.round(horasServicio), ((Number) lote.get("horasTranscurridas")).longValue());
    }

    @Test
    @DisplayName("T2.5: Variación porcentual en getTrazabilidadLoteDetalle se calcula contra kg_origen_despacho (-8.0%)")
    void testCalculoDeltaPorDespachoConsolidado() throws Exception {
        Date fOrigen = dateFormat.parse("2026-10-01");
        Date fCom = dateFormat.parse("2026-10-01");
        Date fPla = dateFormat.parse("2026-10-02");

        // Dos recolectores agrupados en un despacho conjunto de 2500 kg:
        // Rec 1: 1000 kg. Rec 2: 1500 kg. Planta recibe 2300 kg.
        // Variación de la recepción = (2300 - 2500) / 2500 * 100 = -8.0%
        Object[] rowRec1 = buildLoteRow(
                "RO-02OCT-1001", "RECOLECTOR", "Recolector Ficha 1", "11.111.111-1",
                "Huiro Negro", "HÚMEDO", fOrigen, 1000.0,
                2500.0, fCom, "Comercializadora Ficha",
                2300.0, fPla, "Planta Biopacífico",
                "08:00:00", "20:00:00", "10:00:00",
                2500.0, 20L, 30L
        );
        Object[] rowRec2 = buildLoteRow(
                "RO-02OCT-1002", "RECOLECTOR", "Recolector Ficha 2", "22.222.222-2",
                "Huiro Negro", "HÚMEDO", fOrigen, 1500.0,
                2500.0, fCom, "Comercializadora Ficha",
                2300.0, fPla, "Planta Biopacífico",
                "09:00:00", "20:00:00", "10:00:00",
                2500.0, 20L, 30L
        );

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Arrays.asList(rowRec1, rowRec2));

        List<Map<String, Object>> detalle = reportRepository.getTrazabilidadLoteDetalle(
                null, null, null, 5.0, 5.0, 3.0,
                3, 3, 5, 7, "HUMEDO", true);

        assertNotNull(detalle);
        assertEquals(2, detalle.size());

        Map<String, Object> det1 = detalle.get(0);
        Map<String, Object> det2 = detalle.get(1);

        // Ambas filas deben comparar contra el consolidado de despacho de 2500 kg
        assertEquals(-200.0, (Double) det1.get("deltaKg"), 0.01);
        assertEquals(-8.0, (Double) det1.get("deltaPct"), 0.01);

        assertEquals(-200.0, (Double) det2.get("deltaKg"), 0.01);
        assertEquals(-8.0, (Double) det2.get("deltaPct"), 0.01);
    }

    @Test
    @DisplayName("T2.5: Lote aún en bodega virtual (sin recepción en planta) no marca destinado y calcula tiempo hasta ahora")
    void testLoteEnBodegaSinDespachar() throws Exception {
        Date fOrigen = dateFormat.parse("2026-10-01");
        Date fCom = dateFormat.parse("2026-10-01");

        // Lote recibido en comercializador pero sin registrar recepción en planta (kgPla = null, fechaPla = null)
        Object[] rowEnBodega = buildLoteRow(
                "RO-02OCT-1003", "RECOLECTOR", "Recolector Bodega", "33.333.333-3",
                "Huiro Negro", "HÚMEDO", fOrigen, 1200.0,
                1200.0, fCom, "Comercializadora Ficha",
                null, null, null,
                "08:00:00", "12:00:00", null,
                1200.0, 20L, null
        );

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(rowEnBodega));

        Map<String, Object> result = reportRepository.getRetencionPorHumedad(null, null, 24, 72, 216, 80);

        assertNotNull(result);
        assertEquals(1L, ((Number) result.get("totalLotesEnBodega")).longValue());
        assertEquals(0L, ((Number) result.get("lotesDestinados")).longValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) result.get("lotes");
        assertNotNull(lotes);
        assertEquals(1, lotes.size());
        assertEquals(false, lotes.get(0).get("destinado"));
    }

    @Test
    @DisplayName("T2.5: Directo a planta sin comercializador calcula tiempo de tránsito directo")
    void testDirectoAPlantaSinComercializador() throws Exception {
        Date fOrigen = dateFormat.parse("2026-10-01");
        Date fPla = dateFormat.parse("2026-10-01");

        // Venta directa a planta: c.id es null, p.id es 30L
        Object[] rowDirecto = buildLoteRow(
                "RO-02OCT-DIR", "RECOLECTOR", "Recolector Directo", "44.444.444-4",
                "Huiro Negro", "HÚMEDO", fOrigen, 1000.0,
                null, null, null,
                940.0, fPla, "Planta Biopacífico",
                "08:00:00", null, "14:00:00",
                1000.0, null, 30L
        );

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(rowDirecto));

        Map<String, Object> result = reportRepository.getRetencionPorHumedad(null, null, 24, 72, 216, 80);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) result.get("lotes");
        assertNotNull(lotes);
        assertEquals(1, lotes.size());

        Map<String, Object> lote = lotes.get(0);
        assertEquals(true, lote.get("destinado"));
        // De 08:00 a 14:00 = 6 horas
        assertEquals(6L, ((Number) lote.get("horasTranscurridas")).longValue());
        assertEquals("VERDE", lote.get("semaforo"));
    }

    @Test
    @DisplayName("T2.5: Consulta de control contarComercializadoresMultisalto delega a native query")
    void testContarComercializadoresMultisalto() {
        when(entityManager.createNativeQuery("SELECT COUNT(*) FROM declaracion_comercializador WHERE consumida_por_tipo = 'COMERCIALIZADOR'"))
                .thenReturn(query);
        when(query.getSingleResult()).thenReturn(3L);

        long count = reportService.contarComercializadoresMultisalto();

        assertEquals(3L, count);
        verify(query, times(1)).getSingleResult();
    }
}
