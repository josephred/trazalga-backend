package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
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
import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.ILimiteExtraccionDiarioConfigRepository;
import com.trazalga.api.services.LimiteExtraccionDiarioService.EvaluacionLedResult;

@ExtendWith(MockitoExtension.class)
public class LimiteExtraccionDiarioServiceTest {

    @Mock
    private ILimiteExtraccionDiarioConfigRepository ledConfigRepository;

    @Mock
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

    @Mock
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @InjectMocks
    private LimiteExtraccionDiarioService limiteExtraccionDiarioService;

    private LimiteExtraccionDiarioConfigModel reglaArmador;

    @BeforeEach
    void setUp() {
        reglaArmador = new LimiteExtraccionDiarioConfigModel();
        reglaArmador.setId(1L);
        reglaArmador.setNombreRegla("LED Oficial Huiro Palo Barreteado");
        reglaArmador.setPerfilAplicable("ARMADOR");
        reglaArmador.setUnidadAgregacion("EMBARCACION");
        reglaArmador.setMetrica("DESEMBARQUE");
        reglaArmador.setLimiteKg(new BigDecimal("2000.00"));
        reglaArmador.setMargenToleranciaPct(new BigDecimal("10.00")); // 2000 + 10% = 2200 kg
        reglaArmador.setModoAccion("BLOQUEO_DECLARACION");
        reglaArmador.setActivo(true);
    }

    @Test
    void testExtraccionDentroDeLimitePermitido() {
        when(ledConfigRepository.findReglasVigentes(isNull(), any(Date.class)))
                .thenReturn(List.of(reglaArmador));
        when(declaracionArmadorRepository.sumDesembarqueByEmbarcacionAndFecha(eq(100L), any(), any(Date.class)))
                .thenReturn(new BigDecimal("800.00"));

        // Declara 1000 kg -> Total 1800 kg <= 2200 kg
        EvaluacionLedResult res = limiteExtraccionDiarioService.evaluar(
                "ARMADOR", 100L, 5L, null, 1L, 10L, 4L, new Date(),
                new BigDecimal("1000.00"), new BigDecimal("1000.00")
        );

        assertFalse(res.isExcede());
        assertFalse(res.isBloquear());
        assertEquals(new BigDecimal("1800.00"), res.getTotalAcumulado());
    }

    @Test
    void testExtraccionEnMargenToleranciaPermitido() {
        when(ledConfigRepository.findReglasVigentes(isNull(), any(Date.class)))
                .thenReturn(List.of(reglaArmador));
        when(declaracionArmadorRepository.sumDesembarqueByEmbarcacionAndFecha(eq(100L), any(), any(Date.class)))
                .thenReturn(new BigDecimal("1500.00"));

        // Declara 600 kg -> Total 2100 kg. Límite 2000 kg pero con 10% tolerancia llega a 2200 kg.
        EvaluacionLedResult res = limiteExtraccionDiarioService.evaluar(
                "ARMADOR", 100L, 5L, null, 1L, 10L, 4L, new Date(),
                new BigDecimal("600.00"), new BigDecimal("600.00")
        );

        assertFalse(res.isExcede());
        assertFalse(res.isBloquear());
    }

    @Test
    void testExtraccionSuperaLimiteYToleranciaBloquea() {
        when(ledConfigRepository.findReglasVigentes(isNull(), any(Date.class)))
                .thenReturn(List.of(reglaArmador));
        when(declaracionArmadorRepository.sumDesembarqueByEmbarcacionAndFecha(eq(100L), any(), any(Date.class)))
                .thenReturn(new BigDecimal("1500.00"));

        // Declara 800 kg -> Total 2300 kg > 2200 kg (límite + 10% tolerancia)
        EvaluacionLedResult res = limiteExtraccionDiarioService.evaluar(
                "ARMADOR", 100L, 5L, null, 1L, 10L, 4L, new Date(),
                new BigDecimal("800.00"), new BigDecimal("800.00")
        );

        assertTrue(res.isExcede());
        assertTrue(res.isBloquear());
        assertTrue(res.getMensaje().contains("ha sido superado"));
    }

    @Test
    void testExtraccionSuperaLimiteModoAlertaNoBloquea() {
        reglaArmador.setModoAccion("SOLO_ALERTA");
        when(ledConfigRepository.findReglasVigentes(isNull(), any(Date.class)))
                .thenReturn(List.of(reglaArmador));
        when(declaracionArmadorRepository.sumDesembarqueByEmbarcacionAndFecha(eq(100L), any(), any(Date.class)))
                .thenReturn(new BigDecimal("1500.00"));

        // Declara 800 kg -> Total 2300 kg
        EvaluacionLedResult res = limiteExtraccionDiarioService.evaluar(
                "ARMADOR", 100L, 5L, null, 1L, 10L, 4L, new Date(),
                new BigDecimal("800.00"), new BigDecimal("800.00")
        );

        assertTrue(res.isExcede());
        assertFalse(res.isBloquear()); // Con SOLO_ALERTA no bloquea la declaración
    }

    @Test
    void testAgregacionPorEmbarcacionDiferenteMismoArmadorNoSuma() {
        when(ledConfigRepository.findReglasVigentes(isNull(), any(Date.class)))
                .thenReturn(List.of(reglaArmador));
        // Embarcación 200 tiene 0 acumulado hoy aunque el armador sea el mismo
        when(declaracionArmadorRepository.sumDesembarqueByEmbarcacionAndFecha(eq(200L), any(), any(Date.class)))
                .thenReturn(BigDecimal.ZERO);

        EvaluacionLedResult res = limiteExtraccionDiarioService.evaluar(
                "ARMADOR", 200L, 5L, null, 1L, 10L, 4L, new Date(),
                new BigDecimal("1500.00"), new BigDecimal("1500.00")
        );

        assertFalse(res.isExcede());
        assertFalse(res.isBloquear());
        assertEquals(new BigDecimal("1500.00"), res.getTotalAcumulado());
    }
}
