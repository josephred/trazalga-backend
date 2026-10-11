package com.trazalga.api.services.hallazgos;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CriterioHallazgoTest {

    @Test
    @DisplayName("TA.3 - Criterio en kg se formatea con puntos de miles en es-CL")
    void criterioKilosFormateaCorrectamente() {
        CriterioHallazgo c = CriterioHallazgo.deKilos("desembarque_umbral_atipico_kg", 5000, 6200);
        assertEquals("6.200 kg supera el umbral de 5.000 kg", c.texto());
    }

    @Test
    @DisplayName("TA.3 - Criterio en horas se formatea con coma decimal en es-CL")
    void criterioHorasFormateaCorrectamente() {
        CriterioHallazgo c = CriterioHallazgo.deHoras("retencion_bloqueo_humedo_horas", 120.0, 126.4);
        assertEquals("126,4 h supera el umbral de 120,0 h", c.texto());
    }

    @Test
    @DisplayName("TA.3 - Criterio en porcentaje se formatea con coma decimal en es-CL")
    void criterioPorcentajeFormateaCorrectamente() {
        CriterioHallazgo c = CriterioHallazgo.dePorcentaje("variacion_peso_tolerancia_pct", 15.0, 25.4);
        assertEquals("25,4% supera el umbral de 15,0%", c.texto());
    }

    @Test
    @DisplayName("TA.3 - Criterio en fecha formatea comparación temporal")
    void criterioFechaFormateaCorrectamente() {
        CriterioHallazgo c = CriterioHallazgo.deFecha("cuota_fecha_cierre", "2026-03-31", "2026-04-02");
        assertEquals("Fecha observada 2026-04-02 es posterior al cierre del 2026-03-31", c.texto());
    }
}
