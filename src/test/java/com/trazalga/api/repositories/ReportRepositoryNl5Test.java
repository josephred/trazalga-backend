package com.trazalga.api.repositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.parametros.ParametrosRetencion;
import com.trazalga.api.services.parametros.ParametrosService;
import com.trazalga.api.services.retencion.FiltroRetencionBodega;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

/**
 * NL.5: Verifica que los reportes de {@link ReportRepository} y el filtro de
 * {@link RetencionCustodiaService#lotesEnBodega} afectados por desfase UTC en columnas DATE
 * vinculen instancias de {@link java.sql.Date} en hora de Chile ({@code America/Santiago}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportRepositoryNl5Test {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private ParametrosService parametrosService;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @InjectMocks
    private ReportRepository reportRepository;

    @InjectMocks
    private RetencionCustodiaService retencionCustodiaService;

    private Date inicioChile;
    private Date finChile;

    private static Object[] filaCeros() {
        return new Object[]{0L, 0.0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L};
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportRepository, "entityManager", entityManager);
        ReflectionTestUtils.setField(retencionCustodiaService, "entityManager", entityManager);

        inicioChile = Date.from(ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, ApiApplication.ZONA_CHILE).toInstant());
        finChile = Date.from(ZonedDateTime.of(2026, 7, 31, 0, 0, 0, 0, ApiApplication.ZONA_CHILE).toInstant());

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        when(query.getSingleResult()).thenReturn(filaCeros());

        when(parametrosService.variacion()).thenReturn(
                new com.trazalga.api.services.parametros.ParametrosVariacion(true, 3, 5.0, 3.0, false, 5.0, 10.0)
        );
        when(parametrosService.retencion()).thenReturn(
                new ParametrosRetencion(true, 24, 72, 216, 80.0, true, 120, 0, 0, "TODAS", 80.0)
        );
        when(bloqueoCargaService.getMarcasBloqueantes()).thenReturn(Collections.emptySet());
    }

    @Test
    @DisplayName("NL.5: ReportRepository.generateReport envia fechaInicio y fechaFin como java.sql.Date")
    void generateReportVinculaSqlDate() {
        reportRepository.generateReport(inicioChile, finChile, 1, null);
        assertParametrosDia("fechaInicio", "fechaFin");
    }

    @Test
    @DisplayName("NL.5: Reportes de listado con diff=SI vinculan startDate y endDate como java.sql.Date")
    void reportesListadoVinculanSqlDate() {
        reportRepository.getIndicadoresRecolector(inicioChile, finChile);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getExtraccionVedaDetalle(inicioChile, finChile, null, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getVolumenPorEspecie(inicioChile, finChile, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getCurvaSnake(inicioChile, finChile, null, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getDobleOperacion(inicioChile, finChile, 5.0);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getTiempoValidacionDetalle(inicioChile, finChile);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getCadenaOrigenPlanta(inicioChile, finChile, null, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getVariacionPesoDetalle(inicioChile, finChile);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getTrazabilidadLoteDetalle(inicioChile, finChile, null, 20.0, 20.0, 20.0, 24, 72, 216, 80, null, false);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getDesembarqueFisicoDetalle(inicioChile, finChile, null, null, null, null, null, null, null, null, 20.0, true, false, false);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getCapturaCorregidaMetrics(inicioChile, finChile, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getRetencionPorHumedad(inicioChile, finChile, 24, 72, 216, 80);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getDeclaracionesConGeo(inicioChile, finChile, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        reportRepository.getDeclaracionesOrigenGeo(inicioChile, finChile, null);
        assertParametrosDia("startDate", "endDate");
    }

    @Test
    @DisplayName("NL.5: Reportes agregados y conteo sin geo vinculan startDate y endDate como java.sql.Date")
    void reportesAgregadosVinculanSqlDate() {
        reportRepository.getExtraccionVedaMetrics(inicioChile, finChile, null, null);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getSingleResult()).thenReturn(filaCeros());

        reportRepository.getResumenGlobal(inicioChile, finChile);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getSingleResult()).thenReturn(filaCeros());

        reportRepository.getTiempoValidacionMetrics(inicioChile, finChile);
        assertParametrosDia("startDate", "endDate");
        reset(query);
        when(query.getSingleResult()).thenReturn(filaCeros());

        reportRepository.getVariacionPesoMetrics(inicioChile, finChile, 5.0);
        assertParametrosDia("startDate", "endDate");
        reset(query);

        when(query.getSingleResult()).thenReturn(0L);
        reportRepository.contarDeclaracionesSinGeo(inicioChile, finChile, null);
        assertParametrosDia("startDate", "endDate");
    }

    @Test
    @DisplayName("NL.5: RetencionCustodiaService.lotesEnBodega (soloEnBodega=false) vincula java.sql.Date")
    void lotesEnBodegaHistoricoVinculaSqlDate() {
        FiltroRetencionBodega filtro = FiltroRetencionBodega.builder()
                .startDate(inicioChile)
                .endDate(finChile)
                .soloEnBodega(false)
                .build();

        retencionCustodiaService.lotesEnBodega(filtro);
        assertParametrosDia("startDate", "endDate");
    }

    private void assertParametrosDia(String paramInicio, String paramFin) {
        ArgumentCaptor<String> nombres = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valores = ArgumentCaptor.forClass(Object.class);
        verify(query, atLeast(2)).setParameter(nombres.capture(), valores.capture());

        Map<String, Object> bound = new LinkedHashMap<>();
        List<String> keys = nombres.getAllValues();
        List<Object> vals = valores.getAllValues();
        for (int i = 0; i < keys.size(); i++) {
            bound.put(keys.get(i), vals.get(i));
        }

        Object inicio = bound.get(paramInicio);
        Object fin = bound.get(paramFin);
        assertNotNull(inicio, "Falta parametro " + paramInicio);
        assertNotNull(fin, "Falta parametro " + paramFin);
        assertEquals(java.sql.Date.class, inicio.getClass(), paramInicio + " debe ser java.sql.Date");
        assertEquals(java.sql.Date.class, fin.getClass(), paramFin + " debe ser java.sql.Date");
        assertEquals("2026-07-01", inicio.toString());
        assertEquals("2026-07-31", fin.toString());
    }
}
