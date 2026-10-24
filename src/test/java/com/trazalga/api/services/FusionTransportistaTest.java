package com.trazalga.api.services;

import com.trazalga.api.dto.CandidatoFusionDTO;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaFusionLogModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.repositories.*;
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
public class FusionTransportistaTest {

    @Mock
    private ITransportistaRepository transportistaRepository;

    @Mock
    private ITransportistaAliasRepository aliasRepository;

    @Mock
    private ITransportistaFusionLogRepository fusionLogRepository;

    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private FusionTransportistaService fusionService;

    @Test
    @DisplayName("TT.8: Candidatos a fusión -> Jaro-Winkler >= 0.92, mismo nombre con y sin RUT; no se fusionan solos")
    void testCandidatosFusion() {
        TransportistaModel t1 = TransportistaModel.builder()
                .id(1L)
                .nombreCanonico("Juan Carlos Pérez")
                .nombreNormalizado("JUAN CARLOS PEREZ")
                .rut("12345678-5")
                .estado(TransportistaModel.ESTADO_ACTIVO)
                .build();

        TransportistaModel t2 = TransportistaModel.builder()
                .id(2L)
                .nombreCanonico("Juan Carlo Pérez")
                .nombreNormalizado("JUAN CARLO PEREZ")
                .rut(null)
                .estado(TransportistaModel.ESTADO_REVISAR)
                .build();

        TransportistaModel t3 = TransportistaModel.builder()
                .id(3L)
                .nombreCanonico("Pedro Soto")
                .nombreNormalizado("PEDRO SOTO")
                .rut(null)
                .estado(TransportistaModel.ESTADO_REVISAR)
                .build();

        TransportistaModel t4 = TransportistaModel.builder()
                .id(4L)
                .nombreCanonico("Pedro Soto")
                .nombreNormalizado("PEDRO SOTO")
                .rut("11111111-1")
                .estado(TransportistaModel.ESTADO_ACTIVO)
                .build();

        when(transportistaRepository.findAllActivos()).thenReturn(List.of(t1, t2, t3, t4));
        when(configService.getDouble(eq("transportista_similitud_min"), anyDouble())).thenReturn(0.92);

        List<CandidatoFusionDTO> candidatos = fusionService.obtenerCandidatos(null);

        assertNotNull(candidatos);
        assertFalse(candidatos.isEmpty(), "Debe detectar candidatos");

        // Verifica que Pedro Soto (con y sin RUT) es detectado
        boolean encontradoPedroSoto = candidatos.stream()
                .anyMatch(c -> "MISMO_NOMBRE_CON_Y_SIN_RUT".equals(c.getMotivoCandidatura())
                        && "Pedro Soto".equalsIgnoreCase(c.getNombreCanonicoA()));
        assertTrue(encontradoPedroSoto, "Pedro Soto con y sin RUT debe ser candidato");

        // Verifica que NUNCA se fusionó automáticamente
        verify(transportistaRepository, never()).save(any(TransportistaModel.class));
        verify(fusionLogRepository, never()).save(any(TransportistaFusionLogModel.class));
    }

    @Test
    @DisplayName("TT.8: Fusión -> fusionar B en A reasigna llaves, deja B en FUSIONADO, registra log y no toca texto declarado")
    void testFusionManual() {
        TransportistaModel destinoA = TransportistaModel.builder()
                .id(1L)
                .nombreCanonico("Juan Pérez")
                .nombreNormalizado("JUAN PEREZ")
                .rut("12345678-5")
                .rutValido(true)
                .estado(TransportistaModel.ESTADO_ACTIVO)
                .build();

        TransportistaModel origenB = TransportistaModel.builder()
                .id(2L)
                .nombreCanonico("Juan Peres")
                .nombreNormalizado("JUAN PERES")
                .rut(null)
                .rutValido(false)
                .estado(TransportistaModel.ESTADO_REVISAR)
                .build();

        when(transportistaRepository.findById(1L)).thenReturn(Optional.of(destinoA));
        when(transportistaRepository.findById(2L)).thenReturn(Optional.of(origenB));

        // Declaración existente con texto original declarado
        DeclaracionComercializadorModel dc = DeclaracionComercializadorModel.builder()
                .id(100L)
                .choferTransporte("Juan Peres") // Texto declarado inmutable
                .transportista(origenB)
                .build();

        when(comercializadorRepository.findAll()).thenReturn(List.of(dc));
        when(plantaRepository.findAll()).thenReturn(Collections.emptyList());

        TransportistaAliasModel aliasB = TransportistaAliasModel.builder()
                .id(20L)
                .transportista(origenB)
                .nombreDeclarado("Juan Peres")
                .nombreNormalizado("JUAN PERES")
                .veces(3)
                .primeraVez(new Date())
                .ultimaVez(new Date())
                .build();

        when(aliasRepository.findByTransportistaId(2L)).thenReturn(List.of(aliasB));
        when(aliasRepository.findByTransportistaIdAndNombreNormalizado(1L, "JUAN PERES"))
                .thenReturn(Optional.empty());
        when(aliasRepository.findByTransportistaIdOrderByVecesDesc(1L))
                .thenReturn(List.of(aliasB));

        when(transportistaRepository.save(any(TransportistaModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransportistaModel resultado = fusionService.fusionar(2L, 1L, "Mismo chofer confirmado por fiscalizador", 99L);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());

        // B queda en FUSIONADO con fusionadoEnId = 1
        assertEquals(TransportistaModel.ESTADO_FUSIONADO, origenB.getEstado());
        assertEquals(1L, origenB.getFusionadoEnId());

        // La declaración ahora apunta al transportista destino
        assertEquals(destinoA, dc.getTransportista());
        // El texto declarado se mantiene exactamente igual (inmutable)
        assertEquals("Juan Peres", dc.getChoferTransporte());

        // Se registra en el log de auditoría
        verify(fusionLogRepository).save(argThat(log ->
                log.getOrigenId().equals(2L) &&
                log.getDestinoId().equals(1L) &&
                log.getUsuarioId().equals(99L) &&
                log.getMotivo().contains("Mismo chofer")
        ));
    }
}
