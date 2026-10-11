package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.CuotaExtraccionEventoModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionEventoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.NotificationService;

/**
 * TC.3: Pruebas unitarias para cierre automático de cuotas por vencimiento (D5).
 * Verifica que RevisionCuotasService cierre cuotas vencidas cuando cuota_cierre_automatico_vencimiento = true,
 * respete idempotencia y no las cierre cuando el flag está en false.
 */
@ExtendWith(MockitoExtension.class)
public class RevisionCuotasVencimientoTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private IAvisoEnviadoRepository avisoEnviadoRepository;

    @Mock
    private CuotaExtraccionService cuotaExtraccionService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @Mock
    private ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @InjectMocks
    private RevisionCuotasService service;

    @BeforeEach
    void setUp() {
        lenient().when(configuracionGeneralService.getValor("cuota_umbral_restante_pct", "10.0")).thenReturn("10.0");
        lenient().when(configuracionGeneralService.getValor("cuota_dias_previos_expiracion", "5")).thenReturn("5");
        lenient().when(configuracionGeneralService.getValor("cuota_desvio_velocidad_pct", "25.0")).thenReturn("25.0");
    }

    @Test
    @DisplayName("TC.3: Cuota vencida se cierra automáticamente con motivo VENCIMIENTO y evento de auditoría si flag está activo")
    void testCuotaVencida_CierraAutomaticamente_ConFlagActivo() {
        LocalDate hoy = LocalDate.of(2026, 4, 1);
        Date hoyDate = Date.valueOf(hoy);

        // Cuota vencida el 31-03-2026
        CuotaExtraccionModel cuotaVencida = CuotaExtraccionModel.builder()
                .id(101L)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(50_000.0)
                .activo(true)
                .build();

        when(configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false)).thenReturn(true);
        when(cuotaRepository.findActivasConRelaciones()).thenReturn(List.of(cuotaVencida));

        int procesadas = service.revisar(hoy, hoyDate);

        assertEquals(1, procesadas);
        assertEquals("CERRADA", cuotaVencida.getEstado());
        assertEquals("VENCIMIENTO", cuotaVencida.getMotivoCierre());
        assertNotNull(cuotaVencida.getFechaCierre());

        verify(cuotaRepository).save(cuotaVencida);
        verify(cuotaEventoRepository).save(argThat(ev ->
                "CERRADA".equals(ev.getTipo()) &&
                "VENCIMIENTO".equals(ev.getMotivo()) &&
                cuotaVencida.equals(ev.getCuota())
        ));
    }

    @Test
    @DisplayName("TC.3: Cuota vencida NO se cierra automáticamente si flag está desactivado")
    void testCuotaVencida_NoSeCierra_ConFlagInactivo() {
        LocalDate hoy = LocalDate.of(2026, 4, 1);
        Date hoyDate = Date.valueOf(hoy);

        CuotaExtraccionModel cuotaVencida = CuotaExtraccionModel.builder()
                .id(102L)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(50_000.0)
                .activo(true)
                .build();

        when(configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false)).thenReturn(false);
        when(cuotaRepository.findActivasConRelaciones()).thenReturn(List.of(cuotaVencida));

        int procesadas = service.revisar(hoy, hoyDate);

        assertEquals(0, procesadas);
        assertEquals("ABIERTA", cuotaVencida.getEstado());
        assertNull(cuotaVencida.getFechaCierre());

        verify(cuotaRepository, never()).save(cuotaVencida);
        verify(cuotaEventoRepository, never()).save(any(CuotaExtraccionEventoModel.class));
    }

    @Test
    @DisplayName("TC.3: Idempotencia: Cuota ya cerrada se ignora sin duplicar eventos")
    void testCuotaYaCerrada_IgnoradaSinDuplicarEventos() {
        LocalDate hoy = LocalDate.of(2026, 4, 1);
        Date hoyDate = Date.valueOf(hoy);

        CuotaExtraccionModel cuotaCerrada = CuotaExtraccionModel.builder()
                .id(103L)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(50_000.0)
                .activo(true)
                .build();
        cuotaCerrada.cerrar("VENCIMIENTO", Date.valueOf("2026-03-31"), null);

        when(cuotaRepository.findActivasConRelaciones()).thenReturn(List.of(cuotaCerrada));

        int procesadas = service.revisar(hoy, hoyDate);

        assertEquals(0, procesadas);
        verify(cuotaRepository, never()).save(any());
        verify(cuotaEventoRepository, never()).save(any());
    }
}
