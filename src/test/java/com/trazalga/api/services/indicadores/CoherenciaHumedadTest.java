package com.trazalga.api.services.indicadores;

import com.trazalga.api.services.indicadores.CoherenciaHumedad.ParametrosCoherencia;
import com.trazalga.api.services.indicadores.CoherenciaHumedad.ResultadoCoherencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TH.6 · Casos unitarios de coherencia entre extracción y estado de humedad (Res. 3602).
 * Extracción el 01-10-2026; límites de 24, 72 y 216 h.
 */
class CoherenciaHumedadTest {

    private static final LocalDate EXTRACCION_01_OCT = LocalDate.of(2026, 10, 1);

    private ParametrosCoherencia paramsDireccional() {
        return ParametrosCoherencia.builder()
                .edadMaxHumedoHoras(24)
                .edadMaxSemihumedoHoras(72)
                .edadMaxSemisecoHoras(216)
                .modo(CoherenciaHumedad.MODO_DIRECCIONAL)
                .activo(true)
                .build();
    }

    private ParametrosCoherencia paramsEstricto() {
        return ParametrosCoherencia.builder()
                .edadMaxHumedoHoras(24)
                .edadMaxSemihumedoHoras(72)
                .edadMaxSemisecoHoras(216)
                .modo(CoherenciaHumedad.MODO_ESTRICTO)
                .activo(true)
                .build();
    }

    @Test
    void casoA_humedoMismoDia1800_hMin0_coherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 1, 18, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("HÚMEDO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(0.0, dir.getHMin(), 0.01);
        assertEquals("HÚMEDO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, dir.getResultado());
        assertFalse(dir.isIncoherente());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("HÚMEDO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, est.getResultado());
        assertFalse(est.isIncoherente());
    }

    @Test
    void casoB_humedoDiaSiguiente1000_hMin10_coherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 2, 10, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("HÚMEDO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(10.0, dir.getHMin(), 0.01);
        assertEquals("HÚMEDO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, dir.getResultado());
        assertFalse(dir.isIncoherente());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("HÚMEDO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, est.getResultado());
        assertFalse(est.isIncoherente());
    }

    @Test
    void casoC_humedo03Oct1000_hMin34_incoherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 3, 10, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("HÚMEDO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(34.0, dir.getHMin(), 0.01);
        assertEquals("SEMI HÚMEDO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, dir.getResultado());
        assertTrue(dir.isIncoherente());
        assertNotNull(dir.getCriterio());
        assertEquals("humedad_edad_max_humedo_horas", dir.getCriterio().parametro());
        assertEquals("24.0", dir.getCriterio().umbral());
        assertEquals("34.0", dir.getCriterio().valorObservado());
        assertEquals("h", dir.getCriterio().unidad());
        assertTrue(dir.getExplicacion().contains("Declarado HÚMEDO con al menos 34 h desde la extracción (01-10-2026)"));

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("HÚMEDO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, est.getResultado());
        assertTrue(est.isIncoherente());
    }

    @Test
    void casoD_semiHumedo04Oct2300_hMin71_coherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 4, 23, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("SEMI HÚMEDO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(71.0, dir.getHMin(), 0.01);
        assertEquals("SEMI HÚMEDO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, dir.getResultado());
        assertFalse(dir.isIncoherente());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("SEMI HÚMEDO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, est.getResultado());
        assertFalse(est.isIncoherente());
    }

    @Test
    void casoE_semiHumedo06Oct0900_hMin105_incoherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 6, 9, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("SEMI HÚMEDO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(105.0, dir.getHMin(), 0.01);
        assertEquals("SEMI SECO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, dir.getResultado());
        assertTrue(dir.isIncoherente());
        assertNotNull(dir.getCriterio());
        assertEquals("humedad_edad_max_semihumedo_horas", dir.getCriterio().parametro());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("SEMI HÚMEDO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, est.getResultado());
        assertTrue(est.isIncoherente());
    }

    @Test
    void casoF_semiSeco10Oct1200_hMin204_coherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 10, 12, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("SEMI SECO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(204.0, dir.getHMin(), 0.01);
        assertEquals("SEMI SECO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, dir.getResultado());
        assertFalse(dir.isIncoherente());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("SEMI SECO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, est.getResultado());
        assertFalse(est.isIncoherente());
    }

    @Test
    void casoG_semiSeco12Oct0800_hMin248_incoherenteEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 12, 8, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("SEMI SECO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(248.0, dir.getHMin(), 0.01);
        assertEquals("SECO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, dir.getResultado());
        assertTrue(dir.isIncoherente());
        assertNotNull(dir.getCriterio());
        assertEquals("humedad_edad_max_semiseco_horas", dir.getCriterio().parametro());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("SEMI SECO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, est.getResultado());
        assertTrue(est.isIncoherente());
    }

    @Test
    void casoH_secoMismoDia1500_coherenteConAvisoEnDireccional_incoherenteEnEstricto() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 1, 15, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("SECO", EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(0.0, dir.getHMin(), 0.01);
        assertEquals(15.0, dir.getHMax(), 0.01);
        assertEquals("HÚMEDO", dir.getTramoMinimo());
        assertEquals(CoherenciaHumedad.RESULTADO_COHERENTE, dir.getResultado());
        assertFalse(dir.isIncoherente());
        assertTrue(dir.isMasSecoQueSuEdad());
        assertEquals("más seco que su edad", dir.getAviso());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("SECO", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_INCOHERENTE, est.getResultado());
        assertTrue(est.isIncoherente());
        assertTrue(est.isMasSecoQueSuEdad());
    }

    @Test
    void casoI_sinHumedad_sinDatoEnAmbosModos() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 2, 10, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar(null, EXTRACCION_01_OCT, momento, paramsDireccional());
        assertEquals(CoherenciaHumedad.RESULTADO_SIN_DATO, dir.getResultado());
        assertFalse(dir.isIncoherente());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("   ", EXTRACCION_01_OCT, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_SIN_DATO, est.getResultado());
        assertFalse(est.isIncoherente());
    }

    @Test
    void casoJ_extraccionPosteriorAlMomento_sinDatoEnAmbosModos() {
        LocalDate extraccionFutura = LocalDate.of(2026, 10, 5);
        LocalDateTime momento = LocalDateTime.of(2026, 10, 3, 10, 0);

        ResultadoCoherencia dir = CoherenciaHumedad.evaluar("HÚMEDO", extraccionFutura, momento, paramsDireccional());
        assertEquals(CoherenciaHumedad.RESULTADO_SIN_DATO, dir.getResultado());
        assertFalse(dir.isIncoherente());

        ResultadoCoherencia est = CoherenciaHumedad.evaluar("HÚMEDO", extraccionFutura, momento, paramsEstricto());
        assertEquals(CoherenciaHumedad.RESULTADO_SIN_DATO, est.getResultado());
        assertFalse(est.isIncoherente());
    }

    @Test
    void normalizarEstadoDeclarado_secoPlantaCuentaComoSeco_sinFallbackHumedo() {
        assertEquals("SECO", CoherenciaHumedad.normalizarEstadoDeclarado("Seco Planta"));
        assertNull(CoherenciaHumedad.normalizarEstadoDeclarado(null));
        assertNull(CoherenciaHumedad.normalizarEstadoDeclarado("DESCONOCIDO"));
    }
}
