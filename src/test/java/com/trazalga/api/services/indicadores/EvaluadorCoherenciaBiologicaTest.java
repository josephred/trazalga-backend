package com.trazalga.api.services.indicadores;

import com.trazalga.api.services.parametros.ParametrosVariacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TV.2 & TV.6 · Pruebas unitarias de EvaluadorCoherenciaBiologica.
 * Valida la matriz biológica completa y los casos de la reunión del 09-10-2026.
 */
class EvaluadorCoherenciaBiologicaTest {

    private EvaluadorCoherenciaBiologica evaluador;
    private ParametrosVariacion paramsEstandar;

    @BeforeEach
    void setUp() {
        evaluador = new EvaluadorCoherenciaBiologica();
        // Parámetros acordados en reunión: tol 5%, transito min 3d, merma min hum 5%, merma max sec 3%, rojo 10%
        paramsEstandar = new ParametrosVariacion(
                true,   // bioPerdidaActivo
                3,      // bioHumedoDiasMinimosTransito
                5.0,    // bioHumedoMermaMinimaPct
                3.0,    // bioSecoMermaMaximaPct
                false,  // variacionPesoAlertaEquivalente
                5.0,    // variacionPesoUmbralGeneralPct (tol)
                10.0    // riesgoVariacionRojoPct
        );
    }

    @Test
    @DisplayName("Caso A: Húmedo 2.400 kg -> 2.330 kg (Δ -2,9%) en 14h (< 3d) -> VERDE")
    void testCasoA_HumedoTransitoCortoEnTolerancia() {
        double deltaPct = -2.916;
        double dias = 14.0 / 24.0; // ~0.58 días

        var resultado = evaluador.evaluar("HÚMEDO", "HÚMEDO", deltaPct, null, dias, paramsEstandar);

        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.VERDE, resultado.semaforo());
        assertEquals("MASA_ESTABLE", resultado.criterio());
        assertFalse(resultado.alerta());
    }

    @Test
    @DisplayName("Caso B: Húmedo 3.000 kg -> 3.050 kg (Δ +1,7%) en 4 días (>= 3d) -> ROJO HUMEDO_SIN_MERMA")
    void testCasoB_HumedoTransitoLargoSinMerma() {
        double deltaPct = 1.667;
        double dias = 4.0;

        var resultado = evaluador.evaluar("HÚMEDO", "HÚMEDO", deltaPct, null, dias, paramsEstandar);

        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.ROJO, resultado.semaforo());
        assertEquals("HUMEDO_SIN_MERMA", resultado.criterio());
        assertTrue(resultado.alerta());
    }

    @Test
    @DisplayName("Caso C: Húmedo 3.000 kg -> 2.700 kg (Δ -10,0%) en 4 días (>= 3d) -> VERDE DESHIDRATACION_COHERENTE")
    void testCasoC_HumedoTransitoLargoDeshidratacionCoherente() {
        double deltaPct = -10.0;
        double dias = 4.0;

        var resultado = evaluador.evaluar("HÚMEDO", "HÚMEDO", deltaPct, null, dias, paramsEstandar);

        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.VERDE, resultado.semaforo());
        assertEquals("DESHIDRATACION_COHERENTE", resultado.criterio());
        assertFalse(resultado.alerta());
    }

    @Test
    @DisplayName("Caso D1: Seco 2.000 kg -> 1.880 kg (Δ -6,0%) en 6 días -> AMARILLO SECO_MERMA_EXCESIVA")
    void testCasoD1_SecoMermaExcesivaModerada() {
        double deltaPct = -6.0;
        double dias = 6.0;

        var resultado = evaluador.evaluar("SECO", "SECO", deltaPct, null, dias, paramsEstandar);

        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.AMARILLO, resultado.semaforo());
        assertEquals("SECO_MERMA_EXCESIVA", resultado.criterio());
        assertTrue(resultado.alerta());
    }

    @Test
    @DisplayName("Caso D2: Seco 2.000 kg -> 1.750 kg (Δ -12,5%) en 6 días -> ROJO SECO_MERMA_EXCESIVA")
    void testCasoD2_SecoMermaExcesivaCritica() {
        double deltaPct = -12.5;
        double dias = 6.0;

        var resultado = evaluador.evaluar("SECO", "SECO", deltaPct, null, dias, paramsEstandar);

        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.ROJO, resultado.semaforo());
        assertEquals("SECO_MERMA_EXCESIVA", resultado.criterio());
        assertTrue(resultado.alerta());
    }

    @Test
    @DisplayName("Ganancia no explicada: Δ% > tol (+7,0% > 5,0%) -> ROJO GANANCIA_NO_EXPLICADA")
    void testGananciaNoExplicadaUniversal() {
        var resHumedo = evaluador.evaluar("HÚMEDO", "HÚMEDO", 7.0, null, 1.0, paramsEstandar);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.ROJO, resHumedo.semaforo());
        assertEquals("GANANCIA_NO_EXPLICADA", resHumedo.criterio());

        var resSeco = evaluador.evaluar("SECO", "SECO", 7.0, null, 1.0, paramsEstandar);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.ROJO, resSeco.semaforo());
        assertEquals("GANANCIA_NO_EXPLICADA", resSeco.criterio());
    }

    @Test
    @DisplayName("Húmedo tránsito corto con merma alta: 1 día, Δ -7,0% (merma 7% > tol 5%) -> AMARILLO MERMA_ALTA_TRANSITO_CORTO")
    void testHumedoTransitoCortoMermaAlta() {
        var res = evaluador.evaluar("HÚMEDO", "HÚMEDO", -7.0, null, 1.0, paramsEstandar);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.AMARILLO, res.semaforo());
        assertEquals("MERMA_ALTA_TRANSITO_CORTO", res.criterio());
        assertTrue(res.alerta());
    }

    @Test
    @DisplayName("Húmedo tránsito largo con merma insuficiente: 4 días, Δ -3,0% (merma 3% < min 5%) -> AMARILLO HUMEDO_MERMA_INSUFICIENTE")
    void testHumedoTransitoLargoMermaInsuficiente() {
        var res = evaluador.evaluar("HÚMEDO", "HÚMEDO", -3.0, null, 4.0, paramsEstandar);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.AMARILLO, res.semaforo());
        assertEquals("HUMEDO_MERMA_INSUFICIENTE", res.criterio());
        assertTrue(res.alerta());
    }

    @Test
    @DisplayName("Seco masa estable: 6 días, Δ -2,0% (merma 2% <= max 3%) -> VERDE MASA_ESTABLE")
    void testSecoMasaEstable() {
        var res = evaluador.evaluar("SECO", "SECO", -2.0, null, 6.0, paramsEstandar);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.VERDE, res.semaforo());
        assertEquals("MASA_ESTABLE", res.criterio());
        assertFalse(res.alerta());
    }

    @Test
    @DisplayName("Caso G: Húmedo -> Semiseco, 5 días: VERDE en modo físico y VERDE en modo equivalente")
    void testCasoG_CambioDeHumedadConFactor() {
        double deltaFisicoPct = -56.666;
        double deltaEqPct = 3.539;
        double dias = 5.0;

        // Modo 1: alerta equivalente desactivada (modo por defecto) -> evalúa merma física en húmedo
        var resFisico = evaluador.evaluar("HÚMEDO", "SEMISECO", deltaFisicoPct, deltaEqPct, dias, paramsEstandar);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.VERDE, resFisico.semaforo());
        assertEquals("DESHIDRATACION_COHERENTE", resFisico.criterio());

        // Modo 2: alerta equivalente activada -> evalúa sobre deltaEqPct (+3,5% <= 5%)
        ParametrosVariacion paramsConEquiv = new ParametrosVariacion(
                true, 3, 5.0, 3.0, true, 5.0, 10.0
        );
        var resEquiv = evaluador.evaluar("HÚMEDO", "SEMISECO", deltaFisicoPct, deltaEqPct, dias, paramsConEquiv);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.VERDE, resEquiv.semaforo());
        assertEquals("CAMBIO_HUMEDAD_COHERENTE", resEquiv.criterio());

        // Variaciones equivalentes anómalas
        var resEquivAtencion = evaluador.evaluar("HÚMEDO", "SEMISECO", deltaFisicoPct, -7.5, dias, paramsConEquiv);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.AMARILLO, resEquivAtencion.semaforo());
        assertEquals("CAMBIO_HUMEDAD_ATENCION", resEquivAtencion.criterio());

        var resEquivCritica = evaluador.evaluar("HÚMEDO", "SEMISECO", deltaFisicoPct, 14.0, dias, paramsConEquiv);
        assertEquals(EvaluadorCoherenciaBiologica.SemaforoVariacion.ROJO, resEquivCritica.semaforo());
        assertEquals("CAMBIO_HUMEDAD_CRITICA", resEquivCritica.criterio());
    }
}
