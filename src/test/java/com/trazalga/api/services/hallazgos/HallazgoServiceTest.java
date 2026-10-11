package com.trazalga.api.services.hallazgos;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.trazalga.api.events.HallazgoRegistrado;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;

@ExtendWith(MockitoExtension.class)
class HallazgoServiceTest {

    @Mock
    private IDeclaracionMarcaRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private HallazgoFactory factory;

    @InjectMocks
    private HallazgoService service;

    private CriterioHallazgo criterioKilos;

    @BeforeEach
    void setUp() {
        criterioKilos = CriterioHallazgo.deKilos("desembarque_umbral_atipico_kg", 5000, 6200);
    }

    @Test
    @DisplayName("TA.3 - Registro nuevo persiste con clave TIPO:ID:MARCA:REGLA y emite evento")
    void registrarNuevoGuardaYEmiteEvento() {
        when(repository.findByClaveIdempotencia("RECOLECTOR:101:DESEMBARQUE_ATIPICO:REGLA_1"))
                .thenReturn(Optional.empty());

        DeclaracionMarcaModel guardado = DeclaracionMarcaModel.builder()
                .id(1L)
                .declaracionTipo("RECOLECTOR")
                .declaracionId(101L)
                .marca("DESEMBARQUE_ATIPICO")
                .claveIdempotencia("RECOLECTOR:101:DESEMBARQUE_ATIPICO:REGLA_1")
                .detalle("6.200 kg supera el umbral de 5.000 kg")
                .build();

        when(repository.save(any(DeclaracionMarcaModel.class))).thenReturn(guardado);

        DeclaracionMarcaModel res = service.registrar(
                "RECOLECTOR", 101L, "DESEMBARQUE_ATIPICO", "REGLA_1",
                criterioKilos, null, "VALIDACION", 5L, 4L
        );

        assertNotNull(res);
        assertEquals("RECOLECTOR:101:DESEMBARQUE_ATIPICO:REGLA_1", res.getClaveIdempotencia());
        verify(repository, times(1)).save(any(DeclaracionMarcaModel.class));
        verify(eventPublisher, times(1)).publishEvent(any(HallazgoRegistrado.class));
    }

    @Test
    @DisplayName("TA.3 - Registro repetido es idempotente: no vuelve a guardar ni re-notificar")
    void registrarRepetidoEsIdempotente() {
        DeclaracionMarcaModel existente = DeclaracionMarcaModel.builder()
                .id(1L)
                .declaracionTipo("RECOLECTOR")
                .declaracionId(101L)
                .marca("DESEMBARQUE_ATIPICO")
                .claveIdempotencia("RECOLECTOR:101:DESEMBARQUE_ATIPICO:REGLA_1")
                .detalle("6.200 kg supera el umbral de 5.000 kg")
                .build();

        when(repository.findByClaveIdempotencia("RECOLECTOR:101:DESEMBARQUE_ATIPICO:REGLA_1"))
                .thenReturn(Optional.of(existente));

        DeclaracionMarcaModel res = service.registrar(
                "RECOLECTOR", 101L, "DESEMBARQUE_ATIPICO", "REGLA_1",
                criterioKilos, null, "VALIDACION", 5L, 4L
        );

        assertSame(existente, res);
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("TA.3 / TR.3 - Clave para retención en bodega usa formato TIPO:ID:RETENCION_EXCEDIDA")
    void retencionBodegaUsaClaveEspecial() {
        String clave = factory.generarClaveIdempotencia("COMERCIALIZADOR", 202L, "RETENCION_EXCEDIDA", null);
        assertEquals("COMERCIALIZADOR:202:RETENCION_EXCEDIDA", clave);
    }
}
