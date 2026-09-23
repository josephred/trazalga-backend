package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.repositories.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class ReportServiceVedaLedTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private ReportService reportService;

    private Date startDate;
    private Date endDate;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JULY, 1);
        startDate = cal.getTime();
        cal.set(2026, Calendar.JULY, 31);
        endDate = cal.getTime();
    }

    @Test
    void testExtraccionVedaMetricsAndDetalleCuadraturaExacta() {
        // Simular que el repositorio devuelve métricas agregadas
        Map<String, Object> metricsMock = new HashMap<>();
        metricsMock.put("declaracionesVeda", 2L);
        metricsMock.put("totalKgVeda", 4500.0);

        // Simular que el repositorio devuelve el detalle nominal exacto
        List<Map<String, Object>> detalleMock = new ArrayList<>();
        Map<String, Object> d1 = new HashMap<>();
        d1.put("id", 101L);
        d1.put("folio", "DA-101");
        d1.put("tipoDeclaracion", "ARMADOR");
        d1.put("rut", "11.111.111-1");
        d1.put("nombreDeclarante", "Juan Pérez");
        d1.put("caleta", "Caleta Guayacán");
        d1.put("comuna", "Coquimbo");
        d1.put("region", "Coquimbo");
        d1.put("especie", "Huiro Negro");
        d1.put("metodo", "Barreteado");
        d1.put("kilos", 2500.0);
        d1.put("kg", 2500.0);
        d1.put("resolucion", "Res. Ex. 1234/2026 Subpesca");
        detalleMock.add(d1);

        Map<String, Object> d2 = new HashMap<>();
        d2.put("id", 102L);
        d2.put("folio", "DR-102");
        d2.put("tipoDeclaracion", "RECOLECTOR");
        d2.put("rut", "22.222.222-2");
        d2.put("nombreDeclarante", "María González");
        d2.put("caleta", "Caleta Peñuelas");
        d2.put("comuna", "Coquimbo");
        d2.put("region", "Coquimbo");
        d2.put("especie", "Huiro Negro");
        d2.put("metodo", "Barreteado");
        d2.put("kilos", 2000.0);
        d2.put("kg", 2000.0);
        d2.put("resolucion", "Res. Ex. 1234/2026 Subpesca");
        detalleMock.add(d2);

        when(reportRepository.getExtraccionVedaMetrics(startDate, endDate, 1L, 4L)).thenReturn(metricsMock);
        when(reportRepository.getExtraccionVedaDetalle(startDate, endDate, 1L, 4L)).thenReturn(detalleMock);

        Map<String, Object> metrics = reportService.getExtraccionVedaMetrics(startDate, endDate, 1L, 4L);
        List<Map<String, Object>> detalle = reportService.getExtraccionVedaDetalle(startDate, endDate, 1L, 4L);

        // Criterio de Aceptación R5.1: la suma de kilos del detalle iguala totalKgVeda y el conteo iguala declaracionesVeda
        long countDetalle = detalle.size();
        double sumKilos = detalle.stream().mapToDouble(d -> ((Number) d.get("kilos")).doubleValue()).sum();

        assertEquals(((Number) metrics.get("declaracionesVeda")).longValue(), countDetalle);
        assertEquals(((Number) metrics.get("totalKgVeda")).doubleValue(), sumKilos);
        assertEquals("Barreteado", detalle.get(0).get("metodo"));
        assertEquals("DA-101", detalle.get(0).get("folio"));
        assertEquals("Res. Ex. 1234/2026 Subpesca", detalle.get(0).get("resolucion"));
    }

    @Test
    void testLedHallazgosOrdenamientoDescendentePorExceso() {
        List<Map<String, Object>> hallazgosMock = new ArrayList<>();

        Map<String, Object> h1 = new HashMap<>();
        h1.put("folio", "DA-201");
        h1.put("embarcacion", "Don Pedro");
        h1.put("matricula", "COQ-1234");
        h1.put("armador", "15.432.123-K - Roberto Tapia");
        h1.put("rut", "15.432.123-K");
        h1.put("caleta", "Caleta Guayacán");
        h1.put("kgDia", 3500.0);
        h1.put("limiteKg", 2000.0);
        h1.put("excesoKg", 1500.0);
        h1.put("nombreRegla", "LED Coquimbo Huiro Palo");
        hallazgosMock.add(h1);

        Map<String, Object> h2 = new HashMap<>();
        h2.put("folio", "BLOQUEADO (Intento)");
        h2.put("embarcacion", "Santa María");
        h2.put("matricula", "COQ-5678");
        h2.put("armador", "12.345.678-9 - Carmen Silva");
        h2.put("rut", "12.345.678-9");
        h2.put("caleta", "Caleta Los Vilos");
        h2.put("kgDia", 2800.0);
        h2.put("limiteKg", 2000.0);
        h2.put("excesoKg", 800.0);
        h2.put("nombreRegla", "LED Coquimbo Huiro Palo");
        hallazgosMock.add(h2);

        when(reportRepository.getLedHallazgos(startDate, endDate, 4L, null)).thenReturn(hallazgosMock);

        List<Map<String, Object>> res = reportService.getLedHallazgos(startDate, endDate, 4L, null);

        assertNotNull(res);
        assertEquals(2, res.size());

        // Criterio de Aceptación R4.2: ordenadas por exceso descendente
        double exceso1 = ((Number) res.get(0).get("excesoKg")).doubleValue();
        double exceso2 = ((Number) res.get(1).get("excesoKg")).doubleValue();
        assertTrue(exceso1 >= exceso2, "Los hallazgos deben ordenarse por exceso desc");
        assertEquals("COQ-1234", res.get(0).get("matricula"));
        assertEquals("BLOQUEADO (Intento)", res.get(1).get("folio"));
    }
}
