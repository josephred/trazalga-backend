package com.trazalga.api.tasks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.services.RevisionVedasService;
import com.trazalga.api.services.TareaProgramadaService;
import com.trazalga.api.services.cuotas.RevisionCuotasService;

/**
 * Pruebas unitarias para AlertaProgramadaTask (TA.1 / TA.6 / TC.6).
 * Verifica la delegación a servicios transaccionales y el registro de ejecución en tarea_programada_ejecucion.
 */
@ExtendWith(MockitoExtension.class)
public class AlertaProgramadaTaskTest {

    @Mock
    private RevisionCuotasService revisionCuotasService;

    @Mock
    private RevisionVedasService revisionVedasService;

    @Mock
    private TareaProgramadaService tareaProgramadaService;

    @InjectMocks
    private AlertaProgramadaTask task;

    @Test
    @DisplayName("Ejecución diaria exitosa: registra inicio y éxito para cuotas y vedas")
    void testEjecutarRevisionDiaria_Exito() {
        when(tareaProgramadaService.registrarInicio("REVISION_CUOTAS")).thenReturn(1L);
        when(tareaProgramadaService.registrarInicio("REVISION_VEDAS")).thenReturn(2L);
        when(revisionCuotasService.revisar(any(LocalDate.class), any(Date.class))).thenReturn(5);
        when(revisionVedasService.revisar(any(LocalDate.class))).thenReturn(3);

        task.ejecutarRevisionDiaria();

        verify(tareaProgramadaService).registrarInicio("REVISION_CUOTAS");
        verify(tareaProgramadaService).registrarExito(eq(1L), eq(5), anyString());
        verify(tareaProgramadaService).registrarInicio("REVISION_VEDAS");
        verify(tareaProgramadaService).registrarExito(eq(2L), eq(3), anyString());
    }

    @Test
    @DisplayName("Aislamiento de fallos: error en cuotas no interrumpe la revisión de vedas")
    void testEjecutarRevisionDiaria_ErrorCuotas_NoImpideVedas() {
        when(tareaProgramadaService.registrarInicio("REVISION_CUOTAS")).thenReturn(1L);
        when(tareaProgramadaService.registrarInicio("REVISION_VEDAS")).thenReturn(2L);
        when(revisionCuotasService.revisar(any(LocalDate.class), any(Date.class)))
                .thenThrow(new RuntimeException("Simulated error in cuotas"));
        when(revisionVedasService.revisar(any(LocalDate.class))).thenReturn(2);

        task.ejecutarRevisionDiaria();

        verify(tareaProgramadaService).registrarError(eq(1L), eq("Simulated error in cuotas"));
        verify(tareaProgramadaService).registrarInicio("REVISION_VEDAS");
        verify(tareaProgramadaService).registrarExito(eq(2L), eq(2), anyString());
    }
}
