package com.trazalga.api.tasks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.services.TareaProgramadaService;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

/**
 * Pruebas unitarias para RetencionProgramadaTask (TR.3 / TA.1 / TA.6).
 * Verifica la delegación a RetencionCustodiaService y el registro en tarea_programada_ejecucion.
 */
@ExtendWith(MockitoExtension.class)
public class RetencionProgramadaTaskTest {

    @Mock
    private RetencionCustodiaService retencionCustodiaService;

    @Mock
    private TareaProgramadaService tareaProgramadaService;

    @InjectMocks
    private RetencionProgramadaTask task;

    @Test
    @DisplayName("Ejecución periódica exitosa: registra inicio, barrido y éxito")
    void testEjecutarBarridoRetencion_Exito() {
        when(tareaProgramadaService.registrarInicio("BARRIDO_RETENCION_BODEGA")).thenReturn(10L);
        when(retencionCustodiaService.barrer(any(LocalDateTime.class)))
                .thenReturn(new RetencionCustodiaService.ResumenBarridoRetencion(5, 2, 1));

        task.ejecutarBarridoRetencion();

        verify(tareaProgramadaService).registrarInicio("BARRIDO_RETENCION_BODEGA");
        verify(retencionCustodiaService).barrer(any(LocalDateTime.class));
        verify(tareaProgramadaService).registrarExito(eq(10L), eq(5), contains("Barrido completado: 5 evaluados, 2 bloqueos, 1 preavisos"));
    }

    @Test
    @DisplayName("Aislamiento de fallos: excepción en barrido registra error en auditoría de tareas")
    void testEjecutarBarridoRetencion_Error() {
        when(tareaProgramadaService.registrarInicio("BARRIDO_RETENCION_BODEGA")).thenReturn(11L);
        when(retencionCustodiaService.barrer(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Simulated error in barrido"));

        task.ejecutarBarridoRetencion();

        verify(tareaProgramadaService).registrarInicio("BARRIDO_RETENCION_BODEGA");
        verify(tareaProgramadaService).registrarError(eq(11L), contains("Simulated error in barrido"));
    }
}
