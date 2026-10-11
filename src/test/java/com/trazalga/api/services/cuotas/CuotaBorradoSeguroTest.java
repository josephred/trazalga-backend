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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.CuotaExtraccionService;

/**
 * TC.9: Pruebas unitarias para borrado seguro de cuotas de extracción (K16).
 * Verifica que si existen declaraciones o hallazgos asociados se responda 409 Conflict orientativo.
 */
@ExtendWith(MockitoExtension.class)
public class CuotaBorradoSeguroTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private IDeclaracionMarcaRepository declaracionMarcaRepository;

    @Spy
    @InjectMocks
    private CuotaExtraccionService cuotaExtraccionService;

    @Test
    @DisplayName("TC.9: Borrado con declaraciones asociadas lanza 409 Conflict")
    void testBorradoConDeclaracionesAsociadas_Lanza409Conflict() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(50L);

        when(cuotaRepository.findById(50L)).thenReturn(Optional.of(cuota));
        when(declaracionMarcaRepository.countByReglaId(50L)).thenReturn(0L);
        doReturn(5L).when(cuotaExtraccionService).contarDeclaracionesAsociadas(cuota);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            cuotaExtraccionService.delete(50L);
        });

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Tiene 5 declaraciones y 0 hallazgos asociados: desactívela.", ex.getReason());
        verify(cuotaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC.9: Borrado con hallazgos normativos asociados lanza 409 Conflict")
    void testBorradoConHallazgosAsociados_Lanza409Conflict() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(60L);

        when(cuotaRepository.findById(60L)).thenReturn(Optional.of(cuota));
        when(declaracionMarcaRepository.countByReglaId(60L)).thenReturn(3L);
        doReturn(0L).when(cuotaExtraccionService).contarDeclaracionesAsociadas(cuota);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            cuotaExtraccionService.delete(60L);
        });

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Tiene 0 declaraciones y 3 hallazgos asociados: desactívela.", ex.getReason());
        verify(cuotaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("TC.9: Borrado de cuota sin declaraciones ni hallazgos ejecuta deleteById exitosamente")
    void testBorradoSinRelaciones_Exitoso() {
        CuotaExtraccionModel cuota = new CuotaExtraccionModel();
        cuota.setId(70L);

        when(cuotaRepository.findById(70L)).thenReturn(Optional.of(cuota));
        when(declaracionMarcaRepository.countByReglaId(70L)).thenReturn(0L);
        doReturn(0L).when(cuotaExtraccionService).contarDeclaracionesAsociadas(cuota);

        boolean resultado = cuotaExtraccionService.delete(70L);

        assertTrue(resultado);
        verify(cuotaRepository).deleteById(70L);
    }
}
