package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;

/**
 * Pruebas unitarias para CierreCuotaService (TC.1 / K1).
 */
@ExtendWith(MockitoExtension.class)
public class CierreCuotaServiceTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private com.trazalga.api.repositories.ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CierreCuotaService cierreCuotaService;

    @Test
    @DisplayName("TC.1: Cerrar cuota existente establece estado CERRADA, fecha de cierre y motivo ADMINISTRATIVO por defecto")
    void testCerrarCuota_Exito() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(10L);

        when(cuotaRepository.findById(10L)).thenReturn(Optional.of(cuota));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        CuotaExtraccionModel resultado = cierreCuotaService.cerrar(10L, null, "Cierre mensual", 1L);

        assertNotNull(resultado);
        assertEquals("CERRADA", resultado.getEstado());
        assertEquals("ADMINISTRATIVO", resultado.getMotivoCierre());
        assertNotNull(resultado.getFechaCierre());
        verify(cuotaRepository).save(cuota);
        verify(cuotaEventoRepository).save(any());
        verify(eventPublisher).publishEvent(any(com.trazalga.api.events.CuotaCerrada.class));
    }

    @Test
    @DisplayName("TC.2: Reabrir cuota cerrada transiciona a ABIERTA y limpia fechas de cierre")
    void testReabrirCuota_Exito() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(10L);
        cuota.cerrar("ADMINISTRATIVO", new java.util.Date(), 1L);

        when(cuotaRepository.findById(10L)).thenReturn(Optional.of(cuota));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        CuotaExtraccionModel resultado = cierreCuotaService.reabrir(10L, "Corrección administrativa", 1L);

        assertNotNull(resultado);
        assertEquals("ABIERTA", resultado.getEstado());
        assertNull(resultado.getFechaCierre());
        assertNull(resultado.getMotivoCierre());
        verify(cuotaEventoRepository).save(any());
    }

    @Test
    @DisplayName("TC.2: Reabrir cuota sin motivo lanza IllegalArgumentException")
    void testReabrirCuota_SinMotivo_LanzaExcepcion() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(10L);
        cuota.cerrar("ADMINISTRATIVO", new java.util.Date(), 1L);

        when(cuotaRepository.findById(10L)).thenReturn(Optional.of(cuota));

        assertThrows(IllegalArgumentException.class, () -> {
            cierreCuotaService.reabrir(10L, "   ", 1L);
        });
    }

    @Test
    @DisplayName("TC.1: Cerrar cuota inexistente lanza RuntimeException")
    void testCerrarCuota_Inexistente_LanzaExcepcion() {
        when(cuotaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            cierreCuotaService.cerrar(999L, "ADMINISTRATIVO", null, 1L);
        });
    }
}
