package com.trazalga.api.tasks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.VedaEspecieRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.NotificationService;

@ExtendWith(MockitoExtension.class)
public class AlertaProgramadaTaskTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private CuotaExtraccionService cuotaExtraccionService;

    @Mock
    private VedaEspecieRepository vedaRepository;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @InjectMocks
    private AlertaProgramadaTask task;

    private EspecieModel huiroPalo;
    private UsuarioModel admin;

    @BeforeEach
    void setUp() {
        huiroPalo = EspecieModel.builder().id(1L).nombre("Huiro Palo").build();
        admin = UsuarioModel.builder().id(1L).nombres("Admin").build();

        lenient().when(configuracionGeneralService.getValor("cuota_umbral_restante_pct", "10.0")).thenReturn("10.0");
        lenient().when(configuracionGeneralService.getValor("cuota_dias_previos_expiracion", "5")).thenReturn("5");
        lenient().when(configuracionGeneralService.getValor("cuota_desvio_velocidad_pct", "25.0")).thenReturn("25.0");
        lenient().when(usuarioRepository.findByPerfilId(1L)).thenReturn(Collections.singletonList(admin));
    }

    @Test
    void testVelocidadConsumo_CuotaAcelerada_GeneraAlerta() {
        // Cuota anual de 1.000 kg vigente del 1 de enero al 31 de diciembre
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        Date fechaInicio = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date fechaFin = Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant());

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .id(10L)
                .especie(huiroPalo)
                .periodo("ANUAL")
                .limiteKg(1000.0)
                .fechaInicio(fechaInicio)
                .fechaFin(fechaFin)
                .activo(true)
                .esPlantilla(false)
                .build();

        // Evaluación al 1 de abril de 2026 (~25% del año)
        LocalDate hoyLocal = LocalDate.of(2026, 4, 1);
        Date hoy = Date.from(hoyLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());

        when(cuotaRepository.findByActivoTrue()).thenReturn(Collections.singletonList(cuota));
        when(cuotaExtraccionService.calcularLimiteEfectivo(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(1000.0));
        // Consumo 600 kg (60% del límite)
        when(cuotaExtraccionService.calcularConsumoAcumulado(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(600.0));
        when(cuotaExtraccionService.describirAlcance(cuota)).thenReturn("Nacional");

        task.revisarCuotas(hoy, hoyLocal);

        // Desvío = 60% - 25% = 35% > 25% -> Debe notificar alerta de ritmo acelerado
        verify(notificationService, atLeastOnce()).sendPushNotificationToUser(
                eq(1L),
                contains("Alerta de Ritmo de Consumo: Cuota Acelerada"),
                contains("desvío")
        );
    }

    @Test
    void testVelocidadConsumo_CuotaRitmoNormal_NoGeneraAlerta() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        Date fechaInicio = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date fechaFin = Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant());

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .id(11L)
                .especie(huiroPalo)
                .periodo("ANUAL")
                .limiteKg(1000.0)
                .fechaInicio(fechaInicio)
                .fechaFin(fechaFin)
                .activo(true)
                .esPlantilla(false)
                .build();

        LocalDate hoyLocal = LocalDate.of(2026, 4, 1);
        Date hoy = Date.from(hoyLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());

        when(cuotaRepository.findByActivoTrue()).thenReturn(Collections.singletonList(cuota));
        when(cuotaExtraccionService.calcularLimiteEfectivo(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(1000.0));
        // Consumo 200 kg (20% del límite frente a ~25% de tiempo: desvío negativo)
        when(cuotaExtraccionService.calcularConsumoAcumulado(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(200.0));
        when(cuotaExtraccionService.describirAlcance(cuota)).thenReturn("Nacional");

        task.revisarCuotas(hoy, hoyLocal);

        verify(notificationService, never()).sendPushNotificationToUser(
                anyLong(),
                contains("Alerta de Ritmo de Consumo"),
                anyString()
        );
    }

    @Test
    void testVelocidadConsumo_PeriodoTempranoMenor10Pct_EvitaFalsoPositivo() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        Date fechaInicio = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date fechaFin = Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant());

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .id(12L)
                .especie(huiroPalo)
                .periodo("ANUAL")
                .limiteKg(1000.0)
                .fechaInicio(fechaInicio)
                .fechaFin(fechaFin)
                .activo(true)
                .esPlantilla(false)
                .build();

        // Al 5 de enero (~1.3% del año transcurrido)
        LocalDate hoyLocal = LocalDate.of(2026, 1, 5);
        Date hoy = Date.from(hoyLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());

        when(cuotaRepository.findByActivoTrue()).thenReturn(Collections.singletonList(cuota));
        when(cuotaExtraccionService.calcularLimiteEfectivo(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(1000.0));
        // Consumo 300 kg (30%), pero pctTiempo < 10%
        when(cuotaExtraccionService.calcularConsumoAcumulado(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(300.0));
        when(cuotaExtraccionService.describirAlcance(cuota)).thenReturn("Nacional");

        task.revisarCuotas(hoy, hoyLocal);

        verify(notificationService, never()).sendPushNotificationToUser(
                anyLong(),
                contains("Alerta de Ritmo de Consumo"),
                anyString()
        );
    }

    @Test
    void testVelocidadConsumo_ToleranciaConfigurable_DisparaConUmbralMasBajo() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        Date fechaInicio = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date fechaFin = Date.from(end.atStartOfDay(ZoneId.systemDefault()).toInstant());

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .id(13L)
                .especie(huiroPalo)
                .periodo("ANUAL")
                .limiteKg(1000.0)
                .fechaInicio(fechaInicio)
                .fechaFin(fechaFin)
                .activo(true)
                .esPlantilla(false)
                .build();

        LocalDate hoyLocal = LocalDate.of(2026, 4, 1);
        Date hoy = Date.from(hoyLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // Configuramos tolerancia baja: 10.0%
        when(configuracionGeneralService.getValor("cuota_desvio_velocidad_pct", "25.0")).thenReturn("10.0");

        when(cuotaRepository.findByActivoTrue()).thenReturn(Collections.singletonList(cuota));
        when(cuotaExtraccionService.calcularLimiteEfectivo(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(1000.0));
        // Consumo 400 kg (40%). Desvío = 40% - 25% = 15% > 10% -> Debe disparar alerta
        when(cuotaExtraccionService.calcularConsumoAcumulado(eq(cuota), any(Date.class))).thenReturn(BigDecimal.valueOf(400.0));
        when(cuotaExtraccionService.describirAlcance(cuota)).thenReturn("Nacional");

        task.revisarCuotas(hoy, hoyLocal);

        verify(notificationService, atLeastOnce()).sendPushNotificationToUser(
                eq(1L),
                contains("Alerta de Ritmo de Consumo: Cuota Acelerada"),
                contains("tolerancia 10")
        );
    }
}
