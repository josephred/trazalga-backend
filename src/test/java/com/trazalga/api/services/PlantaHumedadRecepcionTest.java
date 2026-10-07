package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;

@ExtendWith(MockitoExtension.class)
public class PlantaHumedadRecepcionTest {

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository repository;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @InjectMocks
    private DeclaracionPlantaAbastecimientoService service;

    private DeclaracionPlantaAbastecimientoModel decl;
    private HumedadEstadoModel humOrigen;
    private HumedadEstadoModel humRecepcion;

    @BeforeEach
    void setUp() {
        humOrigen = new HumedadEstadoModel().setId(1L).setNombre("Húmedo").setRangoInicio(0).setRangoFin(25);
        humRecepcion = new HumedadEstadoModel().setId(2L).setNombre("Semiseco").setRangoInicio(26).setRangoFin(45);

        UsuarioModel usuario = new UsuarioModel();
        usuario.setId(10L);

        decl = new DeclaracionPlantaAbastecimientoModel();
        decl.setId(100L);
        decl.setFolioOrigen("RO-12345");
        decl.setFolioDeclaracionAPla("DAPLA-999");
        decl.setFechaIngresoPlanta(new Date());
        decl.setUsuario(usuario);
        decl.setHumedadEstado(humOrigen);
    }

    @Test
    @DisplayName("Con planta_exige_humedad_recepcion=false, guarda exitosamente aunque humedadEstadoRecepcion sea null (compatibilidad transición)")
    void testGuardarSinHumedadRecepcionCuandoNoEsExigida() {
        when(configuracionGeneralService.getBoolean(eq("variacion_peso_exige_voucher"), anyBoolean())).thenReturn(false);
        when(configuracionGeneralService.getBoolean(eq("planta_exige_humedad_recepcion"), anyBoolean())).thenReturn(false);
        when(repository.save(any(DeclaracionPlantaAbastecimientoModel.class))).thenAnswer(i -> i.getArgument(0));

        decl.setHumedadEstadoRecepcion(null);

        DeclaracionPlantaAbastecimientoModel saved = service.save(decl);

        assertNotNull(saved);
        assertNull(saved.getHumedadEstadoRecepcion());
        assertEquals("Húmedo", saved.getHumedadEstado().getNombre());
        verify(repository).save(decl);
    }

    @Test
    @DisplayName("Con planta_exige_humedad_recepcion=true, lanza excepción si humedadEstadoRecepcion es null")
    void testErrorAlGuardarSinHumedadRecepcionCuandoEsExigida() {
        when(configuracionGeneralService.getBoolean(eq("variacion_peso_exige_voucher"), anyBoolean())).thenReturn(false);
        when(configuracionGeneralService.getBoolean(eq("planta_exige_humedad_recepcion"), anyBoolean())).thenReturn(true);

        decl.setHumedadEstadoRecepcion(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.save(decl);
        });

        assertTrue(ex.getMessage().contains("planta_exige_humedad_recepcion=true"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Con planta_exige_humedad_recepcion=true y humedadEstadoRecepcion provista, guarda conservando ambos estados")
    void testGuardarConHumedadRecepcionConservandoAmbosEstados() {
        when(configuracionGeneralService.getBoolean(eq("variacion_peso_exige_voucher"), anyBoolean())).thenReturn(false);
        when(configuracionGeneralService.getBoolean(eq("planta_exige_humedad_recepcion"), anyBoolean())).thenReturn(true);
        when(repository.save(any(DeclaracionPlantaAbastecimientoModel.class))).thenAnswer(i -> i.getArgument(0));

        decl.setHumedadEstadoRecepcion(humRecepcion);

        DeclaracionPlantaAbastecimientoModel saved = service.save(decl);

        assertNotNull(saved);
        assertNotNull(saved.getHumedadEstado());
        assertEquals(1L, saved.getHumedadEstado().getId());
        assertEquals("Húmedo", saved.getHumedadEstado().getNombre());

        assertNotNull(saved.getHumedadEstadoRecepcion());
        assertEquals(2L, saved.getHumedadEstadoRecepcion().getId());
        assertEquals("Semiseco", saved.getHumedadEstadoRecepcion().getNombre());
        verify(repository).save(decl);
    }
}
