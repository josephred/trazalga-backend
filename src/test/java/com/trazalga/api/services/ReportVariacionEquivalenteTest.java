package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.repositories.ReportRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
public class ReportVariacionEquivalenteTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private FactorConversionService factorConversionService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @InjectMocks
    private ReportRepository reportRepository;

    private Query mockQueryPlanta;
    private Query mockQueryRecolector;

    @BeforeEach
    void setUp() {
        mockQueryPlanta = mock(Query.class);
        mockQueryRecolector = mock(Query.class);
    }

    /**
     * T3.2 Caso de Aceptación:
     * - 2.500 kg de huiro palo en húmedo (captura de 2.825 kg, con factor 1,13)
     * - Llegan a planta con 1.600 kg en romana, en semihúmedo (factor 1,75: 2.800 kg).
     * - La variación física es de -36,0 % y la equivalente, de -0,9 %.
     * - Con variacion_peso_alerta_equivalente = true, ese lote NO alerta (fueraUmbral = false).
     * - Con variacion_peso_alerta_equivalente = false, ese lote alerta como hoy (fueraUmbral = true).
     */
    @Test
    @DisplayName("T3.2 Aceptación: Con parámetro false alerta por variación física (-36%), con parámetro true no alerta por variación equivalente (-0.9%)")
    void testAceptacion_VariacionEquivalenteHumedadRecepcion() {
        Date fechaIngreso = new Date();
        Long espIdHuiroPalo = 1L;
        Long humOrigenHumedoId = 1L;
        Long humRecepcionSemiHumId = 2L;

        // Fila planta:
        // [0:id, 1:folio_origen, 2:peso_romana_kg, 3:fecha_pesaje, 4:fecha_ingreso_planta, 5:voucher, 6:adjunto,
        //  7:declaraciones_seleccionadas, 8:especie_id, 9:rut, 10:nombre, 11:hum_orig_id, 12:hum_orig_nom,
        //  13:hum_rec_id, 14:hum_rec_nom]
        Object[] plantaRow = new Object[] {
            100L, "PLA-100", 1600.0, fechaIngreso, fechaIngreso, "VCH-123", null,
            "RECOLECTOR:10", espIdHuiroPalo, "77123456-7", "Planta Procesadora",
            humOrigenHumedoId, "Húmedo", humRecepcionSemiHumId, "Semihúmedo"
        };

        // Fila recolector origen:
        // [0:id, 1:folio_origen, 2:desembarque/kg, 3:fecha_declaracion, 4:especie_nom, 5:rut, 6:actor, 7:captura, 8:humedad_nom]
        Object[] recRow = new Object[] {
            10L, "RO-10", 2500.0, fechaIngreso, "Huiro palo", "12345678-5", "Juan Pescador",
            2825.0, "Húmedo"
        };

        Query mockAuxQuery = mock(Query.class);
        lenient().when(mockAuxQuery.setParameter(anyString(), any())).thenReturn(mockAuxQuery);
        lenient().when(mockAuxQuery.getResultList()).thenReturn(Collections.emptyList());

        when(mockQueryRecolector.setParameter(anyString(), any())).thenReturn(mockQueryRecolector);
        when(mockQueryRecolector.getResultList()).thenReturn(Collections.singletonList(recRow));

        when(mockQueryPlanta.getResultList()).thenReturn(Collections.singletonList(plantaRow));

        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM declaracion_planta_abastecimiento")) {
                return mockQueryPlanta;
            } else if (sql.contains("FROM declaracion_recolector r")) {
                return mockQueryRecolector;
            }
            return mockAuxQuery;
        });

        // Factor para Huiro palo en recepción (Semihúmedo) = 1.75
        FactorConversionModel factorModel = FactorConversionModel.builder()
                .factor(new BigDecimal("1.75"))
                .build();
        when(factorConversionService.findFactorVigente(eq(espIdHuiroPalo), eq(humRecepcionSemiHumId), any(Date.class)))
                .thenReturn(Optional.of(factorModel));

        // CASO 1: variacion_peso_alerta_equivalente = false (comportamiento histórico: alerta por -36.0%)
        when(configuracionGeneralService.getBoolean("variacion_peso_alerta_equivalente", false)).thenReturn(false);

        List<Map<String, Object>> resFalse = reportRepository.getCadenaOrigenPlanta(null, null);
        assertEquals(1, resFalse.size());
        Map<String, Object> loteFalse = resFalse.get(0);

        assertEquals(2500.0, (Double) loteFalse.get("kgOrigen"), 0.01);
        assertEquals(1600.0, (Double) loteFalse.get("kgPlanta"), 0.01);
        assertEquals(-36.0, (Double) loteFalse.get("variacionPct"), 0.01);

        assertEquals(2825.0, (Double) loteFalse.get("capturaOrigen"), 0.01);
        assertEquals(2800.0, (Double) loteFalse.get("capturaPlanta"), 0.01); // 1600 * 1.75 = 2800
        assertEquals(-0.9, (Double) loteFalse.get("variacionEqPct"), 0.01); // (2800 - 2825) / 2825 * 100 = -0.88 -> -0.9%

        assertEquals("Húmedo", loteFalse.get("humedadOrigen"));
        assertEquals("Semihúmedo", loteFalse.get("humedadRecepcion"));
        assertTrue((Boolean) loteFalse.get("fueraUmbral"), "Con parámetro false, variación física -36.0% excede umbral 5.0% y dispara alerta");

        // CASO 2: variacion_peso_alerta_equivalente = true (nueva regla T3.2: evalúa variación equivalente -0.9%)
        when(configuracionGeneralService.getBoolean("variacion_peso_alerta_equivalente", false)).thenReturn(true);

        List<Map<String, Object>> resTrue = reportRepository.getCadenaOrigenPlanta(null, null);
        assertEquals(1, resTrue.size());
        Map<String, Object> loteTrue = resTrue.get(0);

        assertEquals(-0.9, (Double) loteTrue.get("variacionEqPct"), 0.01);
        assertFalse((Boolean) loteTrue.get("fueraUmbral"), "Con parámetro true, variación equivalente -0.9% está dentro del umbral 5.0% y NO dispara alerta");
    }
}
