package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;

@ExtendWith(MockitoExtension.class)
public class FactorConversionBootstrapTest {

    @Mock
    private IFactorConversionRepository repository;

    @Mock
    private IEspecieRepository especieRepository;

    @Mock
    private IHumedadEstadoRepository humedadEstadoRepository;

    @InjectMocks
    private FactorConversionService factorConversionService;

    private EspecieModel huiroPalo;
    private HumedadEstadoModel estadoHumedo;
    private HumedadEstadoModel estadoSeco;

    @BeforeEach
    void setUp() {
        huiroPalo = EspecieModel.builder().id(1L).nombre("Huiro palo").activo(true).build();
        estadoHumedo = HumedadEstadoModel.builder().id(1L).nombre("Húmedo").rangoInicio(0).rangoFin(30).build();
        estadoSeco = HumedadEstadoModel.builder().id(4L).nombre("Seco").rangoInicio(70).rangoFin(100).build();
    }

    @Test
    void testBootstrapSiembraFactoresOficiales() {
        when(especieRepository.count()).thenReturn(1L);
        when(humedadEstadoRepository.count()).thenReturn(2L);
        when(humedadEstadoRepository.findAll()).thenReturn(List.of(estadoHumedo, estadoSeco));
        when(especieRepository.findAll()).thenReturn(List.of(huiroPalo));
        when(repository.findByEspecieIdAndHumedadEstadoId(1L, 1L)).thenReturn(Collections.emptyList());
        when(repository.findByEspecieIdAndHumedadEstadoId(1L, 4L)).thenReturn(Collections.emptyList());

        factorConversionService.initBootstrap();

        ArgumentCaptor<FactorConversionModel> captor = ArgumentCaptor.forClass(FactorConversionModel.class);
        verify(repository, times(2)).save(captor.capture());

        List<FactorConversionModel> saved = captor.getAllValues();
        assertEquals(2, saved.size());

        FactorConversionModel humedo = saved.stream()
                .filter(f -> f.getHumedadEstado().getId().equals(1L))
                .findFirst()
                .orElse(null);
        assertNotNull(humedo);
        assertEquals(new BigDecimal("1.0000"), humedo.getFactor());

        FactorConversionModel seco = saved.stream()
                .filter(f -> f.getHumedadEstado().getId().equals(4L))
                .findFirst()
                .orElse(null);
        assertNotNull(seco);
        assertEquals(new BigDecimal("3.5800"), seco.getFactor());
    }

    @Test
    void testBootstrapEsIdempotente() {
        when(especieRepository.count()).thenReturn(1L);
        when(humedadEstadoRepository.count()).thenReturn(2L);
        when(humedadEstadoRepository.findAll()).thenReturn(List.of(estadoHumedo, estadoSeco));
        when(especieRepository.findAll()).thenReturn(List.of(huiroPalo));

        FactorConversionModel existenteHumedo = FactorConversionModel.builder()
                .id(100L)
                .especie(huiroPalo)
                .humedadEstado(estadoHumedo)
                .factor(new BigDecimal("1.0000"))
                .vigenciaInicio(new Date(0))
                .activo(true)
                .build();

        FactorConversionModel existenteSeco = FactorConversionModel.builder()
                .id(101L)
                .especie(huiroPalo)
                .humedadEstado(estadoSeco)
                .factor(new BigDecimal("3.5800"))
                .vigenciaInicio(new Date(0))
                .activo(true)
                .build();

        when(repository.findByEspecieIdAndHumedadEstadoId(1L, 1L)).thenReturn(List.of(existenteHumedo));
        when(repository.findByEspecieIdAndHumedadEstadoId(1L, 4L)).thenReturn(List.of(existenteSeco));

        factorConversionService.initBootstrap();

        verify(repository, never()).save(any(FactorConversionModel.class));
    }

    @Test
    void testBootstrapSinCatalogosSaleLimpiamente() {
        when(especieRepository.count()).thenReturn(0L);
        when(humedadEstadoRepository.count()).thenReturn(0L);

        assertDoesNotThrow(() -> factorConversionService.initBootstrap());
        verify(repository, never()).save(any());
    }
}
