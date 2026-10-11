package com.trazalga.api.services;

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

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.VedaEspecieRepository;

/**
 * Pruebas unitarias para RevisionVedasService (TC.6).
 * Verifica la detección de inicio de vedas biológicas y envío de avisos a administradores.
 */
@ExtendWith(MockitoExtension.class)
public class RevisionVedasServiceTest {

    @Mock
    private VedaEspecieRepository vedaRepository;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @InjectMocks
    private RevisionVedasService service;

    private UsuarioModel admin;
    private EspecieModel especieHuiro;

    @BeforeEach
    void setUp() {
        admin = UsuarioModel.builder().id(1L).nombres("Admin").build();
        especieHuiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();

        lenient().when(configuracionGeneralService.getValor("veda_dias_aviso_previo", "7")).thenReturn("7");
        lenient().when(usuarioRepository.findByPerfilId(1L)).thenReturn(List.of(admin));
    }

    @Test
    @DisplayName("TC.6: Veda puntual próxima a iniciar dentro de 3 días genera aviso previo a admins")
    void testVedaProxima_GeneraAviso() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        LocalDate inicioVeda = LocalDate.of(2026, 11, 4); // en 3 días

        VedaEspecieModel veda = VedaEspecieModel.builder()
                .id(1L)
                .activo(true)
                .recurrenciaAnual(false)
                .fechaInicio(Date.valueOf(inicioVeda))
                .fechaFin(Date.valueOf(LocalDate.of(2026, 12, 31)))
                .especie(especieHuiro)
                .resolucion("RES-1234")
                .build();

        when(vedaRepository.findByActivoTrue()).thenReturn(List.of(veda));

        int procesadas = service.revisar(hoy);

        assertEquals(1, procesadas);
        verify(notificationService).sendPushNotificationToUser(
                eq(1L),
                contains("Aviso Previo de Inicio de Veda"),
                contains("entrará en vigor en 3 día(s)")
        );
    }

    @Test
    @DisplayName("TC.6: Veda que inicia en 20 días no genera aviso (umbral de 7 días)")
    void testVedaLejana_NoGeneraAviso() {
        LocalDate hoy = LocalDate.of(2026, 11, 1);
        LocalDate inicioVeda = LocalDate.of(2026, 11, 21); // en 20 días

        VedaEspecieModel veda = VedaEspecieModel.builder()
                .id(1L)
                .activo(true)
                .recurrenciaAnual(false)
                .fechaInicio(Date.valueOf(inicioVeda))
                .fechaFin(Date.valueOf(LocalDate.of(2026, 12, 31)))
                .especie(especieHuiro)
                .build();

        when(vedaRepository.findByActivoTrue()).thenReturn(List.of(veda));

        int procesadas = service.revisar(hoy);

        assertEquals(1, procesadas);
        verify(notificationService, never()).sendPushNotificationToUser(anyLong(), anyString(), anyString());
    }
}
