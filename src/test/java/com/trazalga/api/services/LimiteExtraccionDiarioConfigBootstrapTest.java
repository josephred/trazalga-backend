package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.ILimiteExtraccionDiarioConfigRepository;
import com.trazalga.api.repositories.IRegionRepository;

@ExtendWith(MockitoExtension.class)
public class LimiteExtraccionDiarioConfigBootstrapTest {

    @Mock
    private ILimiteExtraccionDiarioConfigRepository repository;

    @Mock
    private IEspecieRepository especieRepository;

    @Mock
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Mock
    private IRegionRepository regionRepository;

    @InjectMocks
    private LimiteExtraccionDiarioConfigService service;

    private EspecieModel huiroPalo;
    private ExtraccionTipoModel barreteado;

    @BeforeEach
    void setUp() {
        huiroPalo = EspecieModel.builder().id(1L).nombre("Huiro palo").activo(true).build();
        barreteado = ExtraccionTipoModel.builder().id(2L).nombre("Barreteado").descripcion("Extracción con barreta").build();
    }

    @Test
    void testBootstrapSiembraReglaLedOficial() {
        when(especieRepository.count()).thenReturn(1L);
        when(extraccionTipoRepository.count()).thenReturn(1L);
        when(especieRepository.findAll()).thenReturn(List.of(huiroPalo));
        when(extraccionTipoRepository.findAll()).thenReturn(List.of(barreteado));
        when(repository.findAll()).thenReturn(Collections.emptyList());

        service.initBootstrap();

        ArgumentCaptor<LimiteExtraccionDiarioConfigModel> captor = ArgumentCaptor.forClass(LimiteExtraccionDiarioConfigModel.class);
        verify(repository, times(1)).save(captor.capture());

        LimiteExtraccionDiarioConfigModel saved = captor.getValue();
        assertEquals("LED Oficial Huiro Palo Barreteado", saved.getNombreRegla());
        assertEquals("ARMADOR", saved.getPerfilAplicable());
        assertEquals("EMBARCACION", saved.getUnidadAgregacion());
        assertEquals("DESEMBARQUE", saved.getMetrica());
        assertEquals(new BigDecimal("2000.00"), saved.getLimiteKg());
        assertEquals(BigDecimal.ZERO, saved.getMargenToleranciaPct());
        assertEquals("SOLO_ALERTA", saved.getModoAccion());
        assertNull(saved.getRegion()); // Nacional
    }

    @Test
    void testBootstrapEsIdempotenteSiYaExisteRegla() {
        when(especieRepository.count()).thenReturn(1L);
        when(extraccionTipoRepository.count()).thenReturn(1L);
        when(especieRepository.findAll()).thenReturn(List.of(huiroPalo));
        when(extraccionTipoRepository.findAll()).thenReturn(List.of(barreteado));

        LimiteExtraccionDiarioConfigModel existente = LimiteExtraccionDiarioConfigModel.builder()
                .id(1L)
                .nombreRegla("LED Oficial Huiro Palo Barreteado")
                .especie(huiroPalo)
                .extraccionTipo(barreteado)
                .perfilAplicable("ARMADOR")
                .unidadAgregacion("EMBARCACION")
                .metrica("DESEMBARQUE")
                .limiteKg(new BigDecimal("2000.00"))
                .activo(true)
                .build();

        when(repository.findAll()).thenReturn(List.of(existente));

        service.initBootstrap();

        verify(repository, never()).save(any());
    }

    @Test
    void testBootstrapSinCatalogosSaleLimpiamente() {
        when(especieRepository.count()).thenReturn(0L);
        when(extraccionTipoRepository.count()).thenReturn(0L);

        assertDoesNotThrow(() -> service.initBootstrap());
        verify(repository, never()).save(any());
    }
}
