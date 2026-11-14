package com.trazalga.api.repositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.dto.geo.PuntoReferencia;
import com.trazalga.api.services.geo.ReferenciaOrigenService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportRepositoryQe2Test {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private ReferenciaOrigenService referenciaOrigenService;

    @InjectMocks
    private ReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportRepository, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    @DisplayName("QE.2: generateReport resuelve latitud/longitud de origen y destino (referencia oficial + respaldo GPS)")
    void generateReportIncluyeCoordenadasOrigenYDestino() {
        Object[] row = new Object[37];
        row[0] = 10L;
        row[1] = "RO-10";
        row[2] = java.sql.Date.valueOf("2026-10-08");
        row[3] = "09:30:00";
        row[4] = "Pedro";
        row[5] = "Soto";
        row[6] = "";
        row[7] = "11111111-1";
        row[8] = "Planta";
        row[9] = "Norte";
        row[10] = "";
        row[11] = "76000000-1";
        row[12] = new BigDecimal("450.00");
        row[13] = "Huiro Negro";
        row[23] = -29.9601;
        row[24] = -71.3402;
        row[25] = 3L;      // caleta_id
        row[29] = 41L;     // comuna_id
        row[30] = "PLA-01";
        row[31] = "76000000-1";
        row[32] = "Planta Norte";
        row[33] = -27.0800; // gps planta respaldo
        row[34] = -70.8500;
        row[35] = 1;       // esPlanta = true

        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        when(referenciaOrigenService.resolverPorIds("RECOLECTOR", 3L, null, null, null, 41L))
                .thenReturn(PuntoReferencia.builder()
                        .tipo("CALETA")
                        .id(3L)
                        .nombre("Caleta Coquimbo")
                        .lat(-29.955651)
                        .lon(-71.335132)
                        .fuente("CALETA")
                        .build());
        when(referenciaOrigenService.resolverPlantaOficial("PLA-01", "76000000-1", "Planta Norte"))
                .thenReturn(PuntoReferencia.builder()
                        .tipo("PLANTA")
                        .id(1L)
                        .nombre("Planta Norte Oficial")
                        .lat(-27.066700)
                        .lon(-70.820600)
                        .fuente("SERNAPESCA_2026_10")
                        .build());

        List<ReportDTO> out = reportRepository.generateReport(
                new Date(), new Date(), 1, null, referenciaOrigenService);

        assertEquals(1, out.size());
        ReportDTO dto = out.get(0);
        assertEquals(-29.955651, dto.getLatitudOrigen(), 1e-6);
        assertEquals(-71.335132, dto.getLongitudOrigen(), 1e-6);
        assertEquals(-27.066700, dto.getLatitudDestino(), 1e-6);
        assertEquals(-70.820600, dto.getLongitudDestino(), 1e-6);
        // Preserva campos base
        assertEquals("RO-10", dto.getFolio());
        assertEquals(-29.9601, dto.getLatitud(), 1e-6);
    }

    @Test
    @DisplayName("QE.2: si faltan referencias oficiales usa GPS declarado de origen y GPS de recepción como destino")
    void generateReportRespaldoGpsCuandoNoHayReferencia() {
        Object[] row = new Object[37];
        row[0] = 11L;
        row[1] = "RO-11";
        row[2] = java.sql.Date.valueOf("2026-10-08");
        row[3] = "10:00:00";
        row[12] = new BigDecimal("200.00");
        row[13] = "Huiro Palo";
        row[23] = -30.1111;
        row[24] = -71.2222;
        row[30] = "PLA-99";
        row[33] = -29.9100;
        row[34] = -71.2500;
        row[35] = 1;

        when(query.getResultList()).thenReturn(Collections.singletonList(row));

        List<ReportDTO> out = reportRepository.generateReport(
                new Date(), new Date(), 1, null, referenciaOrigenService);

        assertEquals(1, out.size());
        ReportDTO dto = out.get(0);
        assertEquals(-30.1111, dto.getLatitudOrigen(), 1e-6);
        assertEquals(-71.2222, dto.getLongitudOrigen(), 1e-6);
        assertEquals(-29.9100, dto.getLatitudDestino(), 1e-6);
        assertEquals(-71.2500, dto.getLongitudDestino(), 1e-6);
    }
}
