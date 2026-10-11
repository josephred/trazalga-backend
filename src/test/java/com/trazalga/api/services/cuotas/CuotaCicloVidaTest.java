package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.trazalga.api.events.CuotaCerrada;
import com.trazalga.api.models.CuotaExtraccionEventoModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.ICuotaExtraccionEventoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;

/**
 * TC.2: Pruebas unitarias de ciclo de vida de cuotas, encapsulación de dominio
 * y registro de eventos de auditoría (K2).
 */
@ExtendWith(MockitoExtension.class)
public class CuotaCicloVidaTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CierreCuotaService cierreCuotaService;

    @Test
    @DisplayName("TC.2: Transición ABIERTA -> CERRADA mediante método de dominio cerrar()")
    void testCerrarDominio() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        assertEquals("ABIERTA", cuota.getEstado());
        assertNull(cuota.getFechaCierre());
        assertNull(cuota.getMotivoCierre());

        Date fechaCierre = new Date();
        cuota.cerrar("ADMINISTRATIVO", fechaCierre, 1L);

        assertEquals("CERRADA", cuota.getEstado());
        assertEquals("ADMINISTRATIVO", cuota.getMotivoCierre());
        assertEquals(fechaCierre, cuota.getFechaCierre());
        assertNull(cuota.getFechaCierreAutomatico());
    }

    @Test
    @DisplayName("TC.2: Cierre por AGOTAMIENTO establece fechaCierreAutomatico")
    void testCerrarAgotamientoDominio() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        Date fechaCierre = new Date();
        cuota.cerrar("AGOTAMIENTO", fechaCierre, null);

        assertEquals("CERRADA", cuota.getEstado());
        assertEquals("AGOTAMIENTO", cuota.getMotivoCierre());
        assertEquals(fechaCierre, cuota.getFechaCierreAutomatico());
        assertNull(cuota.getFechaCierre());
    }

    @Test
    @DisplayName("TC.2: Cerrar cuota ya CERRADA lanza IllegalStateException")
    void testCerrarCuotaYaCerradaLanzaExcepcion() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.cerrar("ADMINISTRATIVO", new Date(), null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            cuota.cerrar("ADMINISTRATIVO", new Date(), null);
        });
        assertTrue(ex.getMessage().contains("ya se encuentra cerrada"));
    }

    @Test
    @DisplayName("TC.2: Transición CERRADA -> ABIERTA mediante reabrir() limpia fechas y motivo")
    void testReabrirDominio() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.cerrar("ADMINISTRATIVO", new Date(), null);

        cuota.reabrir("Ajuste por cuota suplementaria", 2L);

        assertEquals("ABIERTA", cuota.getEstado());
        assertNull(cuota.getFechaCierre());
        assertNull(cuota.getFechaCierreAutomatico());
        assertNull(cuota.getMotivoCierre());
    }

    @Test
    @DisplayName("TC.2: Reabrir cuota ya ABIERTA lanza IllegalStateException")
    void testReabrirCuotaYaAbiertaLanzaExcepcion() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            cuota.reabrir("Justificación", 1L);
        });
        assertTrue(ex.getMessage().contains("no está cerrada"));
    }

    @Test
    @DisplayName("TC.2: Reabrir sin motivo o motivo en blanco lanza IllegalArgumentException")
    void testReabrirSinMotivoLanzaExcepcion() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.cerrar("ADMINISTRATIVO", new Date(), null);

        assertThrows(IllegalArgumentException.class, () -> cuota.reabrir(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> cuota.reabrir("   ", 1L));
    }

    @Test
    @DisplayName("TC.2: CierreCuotaService.cerrar persiste evento CERRADA y publica evento de dominio")
    void testCierreCuotaServiceCerrarRegistraEventoYPublica() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(10L);

        when(cuotaRepository.findById(10L)).thenReturn(Optional.of(cuota));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(i -> i.getArgument(0));

        CuotaExtraccionModel cerrada = cierreCuotaService.cerrar(10L, "ADMINISTRATIVO", "Cierre por auditoría", 5L);

        assertEquals("CERRADA", cerrada.getEstado());
        verify(cuotaEventoRepository).save(argThat(ev ->
                "CERRADA".equals(ev.getTipo()) &&
                "ADMINISTRATIVO".equals(ev.getMotivo()) &&
                Long.valueOf(5L).equals(ev.getUsuarioId())
        ));
        verify(eventPublisher).publishEvent(any(CuotaCerrada.class));
    }

    @Test
    @DisplayName("TC.2: CierreCuotaService.reabrir persiste evento REABIERTA")
    void testCierreCuotaServiceReabrirRegistraEvento() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(20L);
        cuota.cerrar("ADMINISTRATIVO", new Date(), 1L);

        when(cuotaRepository.findById(20L)).thenReturn(Optional.of(cuota));
        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(i -> i.getArgument(0));

        CuotaExtraccionModel reabierta = cierreCuotaService.reabrir(20L, "Recálculo de saldo", 8L);

        assertEquals("ABIERTA", reabierta.getEstado());
        verify(cuotaEventoRepository).save(argThat(ev ->
                "REABIERTA".equals(ev.getTipo()) &&
                "Recálculo de saldo".equals(ev.getMotivo()) &&
                Long.valueOf(8L).equals(ev.getUsuarioId())
        ));
    }

    @Test
    @DisplayName("TC.2: Encapsulación de dominio: CuotaExtraccionModel no expone setter público para estado")
    void testEncapsulacionDominioSinSetterEstado() {
        assertThrows(NoSuchMethodException.class, () -> {
            CuotaExtraccionModel.class.getMethod("setEstado", String.class);
        }, "CuotaExtraccionModel no debe exponer setEstado()");

        assertThrows(NoSuchMethodException.class, () -> {
            CuotaExtraccionModel.class.getMethod("setFechaCierre", Date.class);
        }, "CuotaExtraccionModel no debe exponer setFechaCierre()");
    }
}
