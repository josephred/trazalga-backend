package com.trazalga.api.revision09oct;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.FactorConversionService;
import com.trazalga.api.services.indicadores.EvaluadorCoherenciaBiologica;
import com.trazalga.api.services.indicadores.VariacionPesoService;
import com.trazalga.api.services.parametros.ParametrosService;
import com.trazalga.api.services.parametros.ParametrosVariacion;

import jakarta.persistence.EntityManager;

/** Revisión 12-10 · Caso A del plan, con las fechas como las entrega Hibernate (java.sql.Date). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevisionTVFechaSqlTest {
    @Mock EntityManager em;
    @Mock IDeclaracionPlantaAbastecimientoRepository plantaRepo;
    @Mock IDeclaracionComercializadorRepository comRepo;
    @Mock IDeclaracionRecolectorRepository recRepo;
    @Mock IDeclaracionArmadorRepository armRepo;
    @Mock IDeclaracionAreaRepository areaRepo;
    @Mock FactorConversionService factores;
    @Mock ParametrosService parametros;

    @Test
    @DisplayName("Caso A con java.sql.Date: Δ −2,9 % VERDE, sin excepción")
    void casoAConFechasSql() {
        when(parametros.variacion()).thenReturn(new ParametrosVariacion(true, 3, 5.0, 3.0, false, 5.0, 10.0));
        VariacionPesoService service = new VariacionPesoService(em, plantaRepo, comRepo, recRepo, armRepo, areaRepo,
                factores, parametros, new EvaluadorCoherenciaBiologica());

        HumedadEstadoModel hum = new HumedadEstadoModel(); hum.setId(1L); hum.setNombre("HÚMEDO");
        EspecieModel esp = new EspecieModel(); esp.setId(1L); esp.setNombre("Huiro negro");

        DeclaracionPlantaAbastecimientoModel p = new DeclaracionPlantaAbastecimientoModel();
        p.setId(100L); p.setPesoRomanaKg(new BigDecimal("2330")); p.setHora("22:00");
        p.setFechaIngresoPlanta(java.sql.Date.valueOf("2026-10-10"));
        p.setHumedadEstado(hum); p.setEspecie(esp);

        DeclaracionComercializadorModel c = new DeclaracionComercializadorModel();
        c.setId(10L); c.setCantidad(new BigDecimal("2400")); c.setHora("08:00");
        c.setFechaTraslado(java.sql.Date.valueOf("2026-10-10"));
        c.setHumedadEstado(hum); c.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO"); c.setDeclaracionDestinatario(100L);
        when(comRepo.findAll()).thenReturn(List.of(c));

        var res = assertDoesNotThrow(() -> service.evaluarRecepcion(p));
        assertEquals(-2.9, res.getDeltaPct(), 0.05);
        assertEquals("VERDE", res.getSemaforo());
    }
}
