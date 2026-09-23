package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class ReportServiceVariacionPesoTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @InjectMocks
    private ReportService reportService;

    @InjectMocks
    private DeclaracionPlantaAbastecimientoService plantaService;

    @Test
    @DisplayName("R6.1: getVariacionPesoMetrics separa poblaciones Pesaje y Documental con totales y promedios propios")
    void testVariacionPesoMetrics_SeparacionPoblaciones() {
        Date startDate = new Date();
        Date endDate = new Date();
        Double umbral = 5.0;

        Map<String, Object> repoResult = new HashMap<>();
        repoResult.put("umbralPct", umbral);
        repoResult.put("totalConciliaciones", 92L);
        repoResult.put("promedioVariacionPct", 0.1);
        repoResult.put("fueraUmbral", 1L);
        repoResult.put("pesajes", 3L);
        repoResult.put("documentos", 89L);

        Map<String, Object> pesajeMap = new HashMap<>();
        pesajeMap.put("total", 3L);
        pesajeMap.put("promedioVariacionPct", 2.3);
        pesajeMap.put("fueraUmbral", 1L);
        repoResult.put("pesaje", pesajeMap);

        Map<String, Object> docMap = new HashMap<>();
        docMap.put("total", 89L);
        docMap.put("promedioVariacionPct", 0.0);
        docMap.put("fueraUmbral", 0L);
        repoResult.put("documental", docMap);

        Map<String, Object> consolidadoMap = new HashMap<>();
        consolidadoMap.put("totalConciliaciones", 92L);
        consolidadoMap.put("promedioVariacionPct", 0.1);
        consolidadoMap.put("fueraUmbral", 1L);
        repoResult.put("consolidado", consolidadoMap);

        when(reportRepository.getVariacionPesoMetrics(startDate, endDate, umbral)).thenReturn(repoResult);

        Map<String, Object> result = reportService.getVariacionPesoMetrics(startDate, endDate, umbral);

        assertNotNull(result);
        assertEquals(92L, result.get("totalConciliaciones"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pesaje = (Map<String, Object>) result.get("pesaje");
        assertNotNull(pesaje);
        assertEquals(3L, pesaje.get("total"));
        assertEquals(2.3, pesaje.get("promedioVariacionPct"));
        assertEquals(1L, pesaje.get("fueraUmbral"));

        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) result.get("documental");
        assertNotNull(doc);
        assertEquals(89L, doc.get("total"));
        assertEquals(0.0, doc.get("promedioVariacionPct"));
        assertEquals(0L, doc.get("fueraUmbral"));

        // Aceptación R6.1: 3 pesajes tienen su propio promedio (2.3%), distinto del de 89 documentos (0.0%). Suma = 92.
        assertNotEquals(pesaje.get("promedioVariacionPct"), doc.get("promedioVariacionPct"));
        assertEquals(92L, ((Number) pesaje.get("total")).longValue() + ((Number) doc.get("total")).longValue());
    }

    @Test
    @DisplayName("R6.2: variacion_peso_exige_voucher=true rechaza recepcion en planta sin voucher o sin peso")
    void testValidarVoucherRomana_ExigeVoucher_RechazaSiFalta() {
        when(configService.getBoolean("variacion_peso_exige_voucher", true)).thenReturn(true);

        DeclaracionPlantaAbastecimientoModel declSinVoucher = new DeclaracionPlantaAbastecimientoModel();
        declSinVoucher.setPesoRomanaKg(new BigDecimal("1500.00"));
        // voucherRomanaNumero es null

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            plantaService.save(declSinVoucher);
        });
        assertTrue(ex1.getMessage().contains("exige número de voucher y peso en romana"));

        DeclaracionPlantaAbastecimientoModel declSinPeso = new DeclaracionPlantaAbastecimientoModel();
        declSinPeso.setVoucherRomanaNumero("VOUCH-12345");
        // pesoRomanaKg es null

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            plantaService.save(declSinPeso);
        });
        assertTrue(ex2.getMessage().contains("exige número de voucher y peso en romana"));
    }

    @Test
    @DisplayName("R6.2: variacion_peso_exige_voucher=true acepta recepcion si tiene voucher y peso")
    void testValidarVoucherRomana_ExigeVoucher_AceptaConDatos() {
        when(configService.getBoolean("variacion_peso_exige_voucher", true)).thenReturn(true);

        DeclaracionPlantaAbastecimientoModel declValida = new DeclaracionPlantaAbastecimientoModel();
        declValida.setVoucherRomanaNumero("VOUCH-12345");
        declValida.setPesoRomanaKg(new BigDecimal("1500.00"));
        declValida.setCantidad(new BigDecimal("1500.00"));

        when(plantaRepository.save(any(DeclaracionPlantaAbastecimientoModel.class))).thenReturn(declValida);

        DeclaracionPlantaAbastecimientoModel guardada = plantaService.save(declValida);
        assertNotNull(guardada);
        assertEquals("VOUCH-12345", guardada.getVoucherRomanaNumero());
        assertEquals(new BigDecimal("1500.00"), guardada.getPesoRomanaKg());
    }

    @Test
    @DisplayName("R6.2: variacion_peso_exige_voucher=false permite recepcion sin voucher para transicion documental")
    void testValidarVoucherRomana_NoExigeVoucher_PermiteSinVoucher() {
        when(configService.getBoolean("variacion_peso_exige_voucher", true)).thenReturn(false);

        DeclaracionPlantaAbastecimientoModel declTransicion = new DeclaracionPlantaAbastecimientoModel();
        declTransicion.setCantidad(new BigDecimal("2000.00"));

        when(plantaRepository.save(any(DeclaracionPlantaAbastecimientoModel.class))).thenReturn(declTransicion);

        DeclaracionPlantaAbastecimientoModel guardada = plantaService.save(declTransicion);
        assertNotNull(guardada);
    }
}
