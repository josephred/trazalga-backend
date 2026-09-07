package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.VedaEspecieRepository;
import com.trazalga.api.services.VedaEvaluadorService.EvaluacionVedaResult;

@ExtendWith(MockitoExtension.class)
public class VedaEvaluadorServiceTest {

    @Mock
    private VedaEspecieRepository vedaRepository;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @InjectMocks
    private VedaEvaluadorService vedaEvaluadorService;

    private EspecieModel huiroNegro;
    private ExtraccionTipoModel barreteado;
    private ExtraccionTipoModel varado;
    private RegionModel coquimbo;

    @BeforeEach
    void setUp() {
        huiroNegro = new EspecieModel();
        huiroNegro.setId(1L);
        huiroNegro.setNombre("Huiro Negro");

        barreteado = new ExtraccionTipoModel();
        barreteado.setId(10L);
        barreteado.setNombre("Barreteado");

        varado = new ExtraccionTipoModel();
        varado.setId(20L);
        varado.setNombre("Varado");

        coquimbo = new RegionModel();
        coquimbo.setId(4L);
        coquimbo.setNombre("Coquimbo");
    }

    private Date toDate(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void testVedaRecurrenteAnualMesVedadoBloqueo() {
        // Veda anual para huiro negro por barreteado en meses 1,2,4,5,6,7,8,10,11 (habilitado solo en 3, 9, 12)
        VedaEspecieModel veda = new VedaEspecieModel();
        veda.setId(100L);
        veda.setEspecie(huiroNegro);
        veda.setExtraccionTipo(barreteado);
        veda.setRegion(coquimbo);
        veda.setRecurrenciaAnual(true);
        veda.setMesesVeda("1,2,4,5,6,7,8,10,11");
        veda.setActivo(true);
        veda.setResolucion("Res. Ex. Subpesca 142/2024");

        when(vedaRepository.findByActivoTrue()).thenReturn(List.of(veda));
        when(configuracionGeneralService.getValor("veda_modo_operacion", "BLOQUEO_ESTRICTO"))
                .thenReturn("BLOQUEO_ESTRICTO");

        // Intentar extracción el 15 de Agosto (mes 8) por barreteado en Coquimbo
        Date fechaAgosto = toDate(2026, 8, 15);
        EvaluacionVedaResult res = vedaEvaluadorService.evaluar(1L, 10L, 4L, fechaAgosto);

        assertTrue(res.isEnVeda());
        assertTrue(res.isBloquear());
        assertTrue(res.getMensaje().toUpperCase().contains("VEDA"));
        assertTrue(res.getMensaje().contains("Huiro Negro"));
    }

    @Test
    void testVedaRecurrenteAnualMesHabilitadoPermitido() {
        VedaEspecieModel veda = new VedaEspecieModel();
        veda.setId(100L);
        veda.setEspecie(huiroNegro);
        veda.setExtraccionTipo(barreteado);
        veda.setRecurrenciaAnual(true);
        veda.setMesesVeda("1,2,4,5,6,7,8,10,11");
        veda.setActivo(true);

        when(vedaRepository.findByActivoTrue()).thenReturn(List.of(veda));

        // Intentar extracción el 10 de Septiembre (mes 9, mes habilitado)
        Date fechaSeptiembre = toDate(2026, 9, 10);
        EvaluacionVedaResult res = vedaEvaluadorService.evaluar(1L, 10L, 4L, fechaSeptiembre);

        assertFalse(res.isEnVeda());
        assertFalse(res.isBloquear());
    }

    @Test
    void testVedaDiferenciadaPorMetodoVaradoPermitido() {
        // La veda es solo para Barreteado
        VedaEspecieModel veda = new VedaEspecieModel();
        veda.setId(100L);
        veda.setEspecie(huiroNegro);
        veda.setExtraccionTipo(barreteado);
        veda.setRecurrenciaAnual(true);
        veda.setMesesVeda("8"); // Agosto en veda por barreteado
        veda.setActivo(true);

        when(vedaRepository.findByActivoTrue()).thenReturn(List.of(veda));

        // Extracción en Agosto pero por Varado (id 20)
        Date fechaAgosto = toDate(2026, 8, 15);
        EvaluacionVedaResult res = vedaEvaluadorService.evaluar(1L, 20L, 4L, fechaAgosto);

        assertFalse(res.isEnVeda());
        assertFalse(res.isBloquear());
    }

    @Test
    void testVedaModoAlertaFiscalizacionNoBloquea() {
        VedaEspecieModel veda = new VedaEspecieModel();
        veda.setId(100L);
        veda.setEspecie(huiroNegro);
        veda.setExtraccionTipo(barreteado);
        veda.setRecurrenciaAnual(true);
        veda.setMesesVeda("8");
        veda.setActivo(true);

        when(vedaRepository.findByActivoTrue()).thenReturn(List.of(veda));
        when(configuracionGeneralService.getValor("veda_modo_operacion", "BLOQUEO_ESTRICTO"))
                .thenReturn("ALERTA_FISCALIZACION");

        Date fechaAgosto = toDate(2026, 8, 15);
        EvaluacionVedaResult res = vedaEvaluadorService.evaluar(1L, 10L, 4L, fechaAgosto);

        assertTrue(res.isEnVeda());
        assertFalse(res.isBloquear()); // Con ALERTA_FISCALIZACION no bloquea, genera marca
    }
}
