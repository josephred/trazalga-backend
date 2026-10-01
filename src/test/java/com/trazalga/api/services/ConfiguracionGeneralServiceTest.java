package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.ConfiguracionGeneralModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ConfiguracionGeneralRepository;

@ExtendWith(MockitoExtension.class)
public class ConfiguracionGeneralServiceTest {

    @Mock
    private ConfiguracionGeneralRepository repository;

    @Mock
    private ConfiguracionAuditoriaService auditoriaService;

    @InjectMocks
    private ConfiguracionGeneralService service;

    private ConfiguracionGeneralModel configVoucher;

    @BeforeEach
    void setUp() {
        configVoucher = ConfiguracionGeneralModel.builder()
                .id(1L)
                .clave("variacion_peso_exige_voucher")
                .valor("true")
                .descripcion("Exige voucher de pesaje en romana")
                .categoria("CADENA")
                .build();
    }

    @Test
    @DisplayName("T0.1: updateConfig desactiva variacion_peso_exige_voucher y registra auditoria con motivo")
    void testUpdateConfig_DesactivarVoucher_RegistraAuditoriaConMotivo() {
        when(repository.findByClave("variacion_peso_exige_voucher")).thenReturn(Optional.of(configVoucher));
        when(repository.save(any(ConfiguracionGeneralModel.class))).thenAnswer(inv -> inv.getArgument(0));

        String motivo = "Transición hasta publicación de APK con campos de romana (T0.1 del plan 25-sep)";
        ConfiguracionGeneralModel updated = service.updateConfig("variacion_peso_exige_voucher", "false", motivo, null);

        assertNotNull(updated);
        assertEquals("false", updated.getValor());
        verify(repository, times(1)).save(configVoucher);

        verify(auditoriaService, times(1)).registrar(
                eq("CONFIGURACION_GENERAL"),
                eq("variacion_peso_exige_voucher"),
                eq("valor"),
                eq("true"),
                eq("false"),
                eq(motivo),
                isNull()
        );
    }

    @Test
    @DisplayName("updateConfig sin cambio de valor no genera registro redundante en auditoria")
    void testUpdateConfig_SinCambio_NoGeneraAuditoria() {
        when(repository.findByClave("variacion_peso_exige_voucher")).thenReturn(Optional.of(configVoucher));
        when(repository.save(any(ConfiguracionGeneralModel.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionGeneralModel updated = service.updateConfig("variacion_peso_exige_voucher", "true");

        assertEquals("true", updated.getValor());
        verify(repository, times(1)).save(configVoucher);
        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateConfig lanza excepcion si la clave no existe")
    void testUpdateConfig_ClaveInexistente_LanzaExcepcion() {
        when(repository.findByClave("clave_inexistente")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            service.updateConfig("clave_inexistente", "nuevo_valor");
        });

        verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }
}
