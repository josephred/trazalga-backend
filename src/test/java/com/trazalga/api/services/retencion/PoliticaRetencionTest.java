package com.trazalga.api.services.retencion;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trazalga.api.services.parametros.ParametrosRetencion;

/**
 * Pruebas unitarias rigurosas del motor de política de retención y bloqueo automático (TR.1, TR.3, TR.5).
 */
public class PoliticaRetencionTest {

    private PoliticaRetencion politica;
    private ParametrosRetencion paramsDefault;
    private ParametrosRetencion paramsBloqueoActivo;

    @BeforeEach
    void setUp() {
        politica = new PoliticaRetencion();

        // Parámetros por defecto: bloqueo inactivo
        paramsDefault = new ParametrosRetencion(
                true,       // retencionActivo
                24,         // humedoMaxHoras
                72,         // semihumedoMaxHoras
                216,        // semisecoMaxHoras
                80.0,       // semaforoPreavisoPct
                false,      // bloqueoActivo
                120,        // bloqueoHumedoHoras (5 días)
                0,          // bloqueoSemihumedoHoras
                0,          // bloqueoSemisecoHoras
                "TODAS",    // bloqueoEspecies
                80.0        // bloqueoPreavisoPct
        );

        // Parámetros con bloqueo activo
        paramsBloqueoActivo = new ParametrosRetencion(
                true,
                24,
                72,
                216,
                80.0,
                true,       // bloqueoActivo = true
                120,        // bloqueoHumedoHoras = 120 (5 días)
                0,
                0,
                "TODAS",
                80.0
        );
    }

    @Test
    @DisplayName("TR.1: Normalización exhaustiva de estados de humedad canónicos")
    void testNormalizarEstadoHumedad() {
        assertEquals("HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad(null));
        assertEquals("HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad(""));
        assertEquals("HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad("húmedo"));
        assertEquals("HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad("HUMEDO"));
        assertEquals("HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad(" Recurso Húmedo "));

        assertEquals("SEMI HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad("semihumedo"));
        assertEquals("SEMI HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad("SEMI HÚMEDO"));
        assertEquals("SEMI HÚMEDO", PoliticaRetencion.normalizarEstadoHumedad("semi-humedo"));

        assertEquals("SEMI SECO", PoliticaRetencion.normalizarEstadoHumedad("semiseco"));
        assertEquals("SEMI SECO", PoliticaRetencion.normalizarEstadoHumedad("SEMI SECO"));
        assertEquals("SEMI SECO", PoliticaRetencion.normalizarEstadoHumedad("semi-seco"));

        assertEquals("SECO", PoliticaRetencion.normalizarEstadoHumedad("seco"));
        assertEquals("SECO", PoliticaRetencion.normalizarEstadoHumedad("SECO"));
        assertEquals("SECO", PoliticaRetencion.normalizarEstadoHumedad(" alga seca "));
    }

    @Test
    @DisplayName("TR.1 / Res. 3602: Semáforo normativo para recurso HÚMEDO (máx 24h, preaviso 80% = 19h)")
    void testSemaforoHumedo() {
        // 10h -> VERDE
        ResultadoEvaluacionRetencion r10 = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 10.0, paramsDefault);
        assertEquals("VERDE", r10.getSemaforo());
        assertFalse(r10.isVencido());
        assertFalse(r10.isEnPreavisoSemaforo());
        assertEquals("HÚMEDO", r10.getTramoReal());
        assertFalse(r10.isInconsistenciaHumedad());

        // 20h -> AMARILLO (preaviso a partir de 19h)
        ResultadoEvaluacionRetencion r20 = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 20.0, paramsDefault);
        assertEquals("AMARILLO", r20.getSemaforo());
        assertFalse(r20.isVencido());
        assertTrue(r20.isEnPreavisoSemaforo());

        // 25h -> ROJO (vencido > 24h)
        ResultadoEvaluacionRetencion r25 = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 25.0, paramsDefault);
        assertEquals("ROJO", r25.getSemaforo());
        assertTrue(r25.isVencido());
        // A las 25h biológicamente ya pasó a SEMI HÚMEDO
        assertEquals("SEMI HÚMEDO", r25.getTramoReal());
        assertTrue(r25.isInconsistenciaHumedad());
    }

    @Test
    @DisplayName("TR.1 / Res. 3602: Semáforo normativo para recurso SEMI HÚMEDO (máx 72h, preaviso 80% = 58h)")
    void testSemaforoSemiHumedo() {
        ResultadoEvaluacionRetencion r50 = politica.evaluar(1L, "Huiro Palo", "SEMI HÚMEDO", 50.0, paramsDefault);
        assertEquals("VERDE", r50.getSemaforo());

        ResultadoEvaluacionRetencion r60 = politica.evaluar(1L, "Huiro Palo", "SEMI HÚMEDO", 60.0, paramsDefault);
        assertEquals("AMARILLO", r60.getSemaforo());
        assertTrue(r60.isEnPreavisoSemaforo());

        ResultadoEvaluacionRetencion r75 = politica.evaluar(1L, "Huiro Palo", "SEMI HÚMEDO", 75.0, paramsDefault);
        assertEquals("ROJO", r75.getSemaforo());
        assertTrue(r75.isVencido());
    }

    @Test
    @DisplayName("TR.1 / Res. 3602: Semáforo normativo para recurso SEMI SECO (máx 216h, preaviso 80% = 173h)")
    void testSemaforoSemiSeco() {
        ResultadoEvaluacionRetencion r150 = politica.evaluar(2L, "Luga Negra", "SEMI SECO", 150.0, paramsDefault);
        assertEquals("VERDE", r150.getSemaforo());

        ResultadoEvaluacionRetencion r180 = politica.evaluar(2L, "Luga Negra", "SEMI SECO", 180.0, paramsDefault);
        assertEquals("AMARILLO", r180.getSemaforo());

        ResultadoEvaluacionRetencion r220 = politica.evaluar(2L, "Luga Negra", "SEMI SECO", 220.0, paramsDefault);
        assertEquals("ROJO", r220.getSemaforo());
        assertTrue(r220.isVencido());
    }

    @Test
    @DisplayName("TR.1 / Res. 3602: Recurso SECO sin límite normativo (siempre VERDE)")
    void testSemaforoSecoSinLimite() {
        ResultadoEvaluacionRetencion r500 = politica.evaluar(1L, "Huiro Negro", "SECO", 500.0, paramsDefault);
        assertEquals("VERDE", r500.getSemaforo());
        assertFalse(r500.isVencido());
        assertEquals(Integer.MAX_VALUE, r500.getPlazoMaxHoras());
    }

    @Test
    @DisplayName("TR.3: Bloqueo automático D3 activo bloquea a las 120h en húmedo")
    void testBloqueoAutomaticoHumedo120h() {
        // Bloqueo inactivo: 125h húmedo es ROJO normativo pero NO bloquea automáticamente
        ResultadoEvaluacionRetencion rInactivo = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 125.0, paramsDefault);
        assertEquals("ROJO", rInactivo.getSemaforo());
        assertFalse(rInactivo.isBloqueoRequerido());

        // Bloqueo activo: 120h húmedo dispara bloqueoRequerido = true
        ResultadoEvaluacionRetencion rActivo = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 120.0, paramsBloqueoActivo);
        assertEquals("ROJO", rActivo.getSemaforo());
        assertTrue(rActivo.isBloqueoRequerido());
        assertTrue(rActivo.getMotivoDetalle().contains("Retención excedida"));

        // Preaviso de bloqueo: 100h (80% de 120 = 96h)
        ResultadoEvaluacionRetencion rPreaviso = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 100.0, paramsBloqueoActivo);
        assertFalse(rPreaviso.isBloqueoRequerido());
        assertTrue(rPreaviso.isEnPreavisoBloqueo());
    }

    @Test
    @DisplayName("TR.3: Recurso SECO con 300h no se bloquea por descomposición")
    void testSeco300hNoBloquea() {
        ResultadoEvaluacionRetencion rSeco = politica.evaluar(1L, "Huiro Negro", "SECO", 300.0, paramsBloqueoActivo);
        assertEquals("VERDE", rSeco.getSemaforo());
        assertFalse(rSeco.isBloqueoRequerido());
        assertFalse(rSeco.isEnPreavisoBloqueo());
    }

    @Test
    @DisplayName("TR.3: Filtrado por especie en política de bloqueo")
    void testFiltradoPorEspecieEnBloqueo() {
        ParametrosRetencion paramsEspecie = new ParametrosRetencion(
                true, 24, 72, 216, 80.0,
                true, 120, 0, 0,
                "1,Huiro Negro", 80.0
        );

        // Especie 1 (Huiro Negro) -> Aplica
        ResultadoEvaluacionRetencion rAplica = politica.evaluar(1L, "Huiro Negro", "HÚMEDO", 130.0, paramsEspecie);
        assertTrue(rAplica.isBloqueoRequerido());

        // Especie 2 (Luga Negra) -> No aplica según lista
        ResultadoEvaluacionRetencion rNoAplica = politica.evaluar(2L, "Luga Negra", "HÚMEDO", 130.0, paramsEspecie);
        assertFalse(rNoAplica.isBloqueoRequerido());
    }
}
