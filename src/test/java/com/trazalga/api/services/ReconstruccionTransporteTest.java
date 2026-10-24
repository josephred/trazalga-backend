package com.trazalga.api.services;

import com.trazalga.api.dto.InformeReconstruccionDTO;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.VehiculoModel;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.TransporteResolverService.TransporteResolucionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReconstruccionTransporteTest {

    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Mock
    private TransporteResolverService transporteResolverService;

    @Mock
    private ITransportistaRepository transportistaRepository;

    @Mock
    private ITransportistaAliasRepository aliasRepository;

    @Mock
    private IVehiculoRepository vehiculoRepository;

    @InjectMocks
    private ReconstruccionTransporteService reconstruccionService;

    @Test
    @DisplayName("TT.8: Idempotencia de reconstrucción -> correr dos veces deja exactamente las mismas cifras")
    void testReconstruccionIdempotente() {
        DeclaracionComercializadorModel dc = DeclaracionComercializadorModel.builder()
                .id(1L)
                .choferTransporte("Juan Pérez")
                .rutChofer("12.345.678-5")
                .placaPatente("ABCD12")
                .fechaDeclaracion(new Date())
                .build();

        when(comercializadorRepository.findAll()).thenReturn(List.of(dc));
        when(plantaRepository.findAll()).thenReturn(Collections.emptyList());

        TransportistaModel t = TransportistaModel.builder()
                .id(1L)
                .rut("12345678-5")
                .rutValido(true)
                .nombreCanonico("Juan Pérez")
                .nombreNormalizado("JUAN PEREZ")
                .build();

        VehiculoModel v = VehiculoModel.builder()
                .id(1L)
                .patente("ABCD12")
                .formatoValido(true)
                .usadoComoCamion(true)
                .usadoComoRampla(false)
                .build();

        TransporteResolucionResult res = TransporteResolucionResult.builder()
                .transportista(t)
                .vehiculo(v)
                .rutValido(true)
                .patenteCamionValida(true)
                .build();

        when(transporteResolverService.resolver(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(res);

        when(transportistaRepository.findAll()).thenReturn(List.of(t));
        when(vehiculoRepository.findAll()).thenReturn(List.of(v));
        when(aliasRepository.findByTransportistaId(1L)).thenReturn(List.of(
                TransportistaAliasModel.builder().id(1L).nombreDeclarado("Juan Pérez").nombreNormalizado("JUAN PEREZ").veces(1).build()
        ));

        // Primera ejecución
        InformeReconstruccionDTO informe1 = reconstruccionService.reconstruir();
        assertNotNull(informe1);
        assertEquals(1, informe1.getDeclaracionesComercializadorProcesadas());
        assertEquals(1, informe1.getTransportistasCreadosConRut());
        assertEquals(0, informe1.getTransportistasCreadosSinRut());
        assertEquals(1, informe1.getVehiculosCreados());

        // Segunda ejecución (idempotente)
        InformeReconstruccionDTO informe2 = reconstruccionService.reconstruir();
        assertNotNull(informe2);
        assertEquals(informe1.getDeclaracionesComercializadorProcesadas(), informe2.getDeclaracionesComercializadorProcesadas());
        assertEquals(informe1.getTransportistasCreadosConRut(), informe2.getTransportistasCreadosConRut());
        assertEquals(informe1.getTransportistasCreadosSinRut(), informe2.getTransportistasCreadosSinRut());
        assertEquals(informe1.getVehiculosCreados(), informe2.getVehiculosCreados());
        assertEquals(informe1.getRutInvalidosDetectados(), informe2.getRutInvalidosDetectados());
    }
}
