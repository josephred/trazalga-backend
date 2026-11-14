package com.trazalga.api.services.indicadores;

import com.trazalga.api.dto.TrazabilidadEdgeDTO;
import com.trazalga.api.dto.TrazabilidadNodoDTO;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.CadenaCustodiaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapturaOrigenProduccionTest {

    @Mock private CadenaCustodiaService cadenaCustodiaService;
    @Mock private IDeclaracionRecolectorRepository recolectorRepository;
    @Mock private IDeclaracionArmadorRepository armadorRepository;
    @Mock private IDeclaracionAreaRepository areaRepository;
    @Mock private IDeclaracionComercializadorRepository comercializadorRepository;

    private CapturaOrigenProduccion service;

    @BeforeEach
    void setup() {
        service = new CapturaOrigenProduccion(
                cadenaCustodiaService,
                recolectorRepository,
                armadorRepository,
                areaRepository,
                comercializadorRepository
        );
    }

    @Test
    @DisplayName("QR.6: dos lotes de producción suman captura única de recolectores, armadores y áreas")
    void dosLotes_sumaCapturasUnicasDeOrigen() {
        DeclaracionPlantaAbastecimientoModel lote1 = DeclaracionPlantaAbastecimientoModel.builder()
                .id(101L)
                .declaracionesSeleccionadas("RECOLECTOR:11,ARMADOR:21")
                .build();
        DeclaracionPlantaAbastecimientoModel lote2 = DeclaracionPlantaAbastecimientoModel.builder()
                .id(102L)
                .declaracionesSeleccionadas("COMERCIALIZADOR:31")
                .build();

        CadenaCustodiaService.Cadena cad1 = CadenaCustodiaService.Cadena.builder()
                .origenes(List.of(
                        CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(11L).build(),
                        CadenaCustodiaService.OrigenRef.builder().tipo("ARMADOR").id(21L).build()))
                .nodos(List.of())
                .enlaces(List.of())
                .build();

        CadenaCustodiaService.Cadena cad2 = CadenaCustodiaService.Cadena.builder()
                .origenes(List.of(
                        CadenaCustodiaService.OrigenRef.builder().tipo("AREA").id(41L).build()))
                .nodos(List.of(
                        TrazabilidadNodoDTO.builder()
                                .tipoNodo("COMERCIALIZADOR").idDeclaracion(31L)
                                .declaracionesSeleccionadas("AREA:41").build()))
                .enlaces(List.of(
                        TrazabilidadEdgeDTO.builder().source("AREA:41").target("COMERCIALIZADOR:31").build()))
                .build();

        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 101L)).thenReturn(cad1);
        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 102L)).thenReturn(cad2);

        when(recolectorRepository.findAllById(Set.of(11L))).thenReturn(List.of(
                DeclaracionRecolectorModel.builder().id(11L).captura(new BigDecimal("4000.00")).build()));
        when(armadorRepository.findAllById(Set.of(21L))).thenReturn(List.of(
                DeclaracionArmadorModel.builder().id(21L).captura(3500.0).build()));
        when(areaRepository.findAllById(Set.of(41L))).thenReturn(List.of(
                DeclaracionAreaModel.builder().id(41L).captura(2500.0).build()));

        CapturaOrigenProduccion.ResultadoCapturaOrigen res = service.resolverParaLotes(List.of(lote1, lote2));

        assertTrue(res.completa());
        assertEquals(new BigDecimal("10000.00"), res.capturaOrigenKg());
        assertEquals(3, res.totalOrigenes());
        assertEquals(List.of("RECOLECTOR:11", "ARMADOR:21", "AREA:41"), res.clavesOrigen());
        assertNull(res.motivoIncompleta());
    }

    @Test
    @DisplayName("QR.6: origen compartido vía comercializador + directo se deduplica antes de sumar captura")
    void origenCompartido_viaComercializadorYDirecto_seCuentaUnaSolaVez() {
        DeclaracionPlantaAbastecimientoModel loteDirecto = DeclaracionPlantaAbastecimientoModel.builder()
                .id(201L)
                .declaracionesSeleccionadas("RECOLECTOR:10,ARMADOR:20")
                .build();
        DeclaracionPlantaAbastecimientoModel loteCom = DeclaracionPlantaAbastecimientoModel.builder()
                .id(202L)
                .declaracionesSeleccionadas("COMERCIALIZADOR:300")
                .build();

        CadenaCustodiaService.Cadena cadDirecto = CadenaCustodiaService.Cadena.builder()
                .origenes(List.of(
                        CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(10L).build(),
                        CadenaCustodiaService.OrigenRef.builder().tipo("ARMADOR").id(20L).build()))
                .build();
        CadenaCustodiaService.Cadena cadCom = CadenaCustodiaService.Cadena.builder()
                .origenes(List.of(
                        CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(10L).build()))
                .nodos(List.of(
                        TrazabilidadNodoDTO.builder()
                                .tipoNodo("COMERCIALIZADOR").idDeclaracion(300L)
                                .declaracionesSeleccionadas("RECOLECTOR:10").build()))
                .enlaces(List.of(
                        TrazabilidadEdgeDTO.builder().source("RECOLECTOR:10").target("COMERCIALIZADOR:300").build()))
                .build();

        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 201L)).thenReturn(cadDirecto);
        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 202L)).thenReturn(cadCom);

        when(recolectorRepository.findAllById(Set.of(10L))).thenReturn(List.of(
                DeclaracionRecolectorModel.builder().id(10L).captura(new BigDecimal("6000.00")).build()));
        when(armadorRepository.findAllById(Set.of(20L))).thenReturn(List.of(
                DeclaracionArmadorModel.builder().id(20L).captura(4000.0).build()));

        CapturaOrigenProduccion.ResultadoCapturaOrigen res = service.resolverParaLotes(List.of(loteDirecto, loteCom));

        assertTrue(res.completa());
        // RECOLECTOR:10 (6000) aparece en ambos lotes, debe contarse solo una vez -> 6000 + 4000 = 10000
        assertEquals(new BigDecimal("10000.00"), res.capturaOrigenKg());
        assertEquals(2, res.totalOrigenes());
        assertEquals(List.of("RECOLECTOR:10", "ARMADOR:20"), res.clavesOrigen());
    }

    @Test
    @DisplayName("QR.6: lote sin declaraciones_seleccionadas o cero orígenes marca completa=false con suma parcial")
    void loteSinSeleccionadasYSinOrigenes_marcaIncompletaConSumaParcial() {
        DeclaracionPlantaAbastecimientoModel loteOk = DeclaracionPlantaAbastecimientoModel.builder()
                .id(301L).declaracionesSeleccionadas("RECOLECTOR:50").build();
        DeclaracionPlantaAbastecimientoModel loteVacio = DeclaracionPlantaAbastecimientoModel.builder()
                .id(302L).declaracionesSeleccionadas(null).build();

        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 301L)).thenReturn(
                CadenaCustodiaService.Cadena.builder()
                        .origenes(List.of(CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(50L).build()))
                        .build());
        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 302L)).thenReturn(
                CadenaCustodiaService.Cadena.builder().origenes(List.of()).build());

        when(recolectorRepository.findAllById(anyIterable())).thenReturn(List.of(
                DeclaracionRecolectorModel.builder().id(50L).captura(new BigDecimal("4200.00")).build()));

        CapturaOrigenProduccion.ResultadoCapturaOrigen res = service.resolverParaLotes(List.of(loteOk, loteVacio));

        assertFalse(res.completa());
        assertEquals(new BigDecimal("4200.00"), res.capturaOrigenKg());
        assertEquals(1, res.totalOrigenes());
        assertNotNull(res.motivoIncompleta());
        assertTrue(res.motivoIncompleta().contains(CapturaOrigenProduccion.MOTIVO_LOTE_SIN_SELECCIONADAS));
        assertTrue(res.motivoIncompleta().contains(CapturaOrigenProduccion.MOTIVO_LOTE_SIN_ORIGENES));
    }

    @Test
    @DisplayName("QR.6: token LEGACY ambiguo o huérfano ('9,10022') marca completa=false con motivo LEGACY_NO_ATRIBUIBLE")
    void tokensLegacyNoAtribuibles_marcaIncompleta() {
        DeclaracionPlantaAbastecimientoModel loteLegacy = DeclaracionPlantaAbastecimientoModel.builder()
                .id(401L)
                .declaracionesSeleccionadas("9,10022")
                .build();

        // 9 coincide en 2 tablas (ambiguo) y 10022 en 0 tablas (huérfano) -> probar ambos casos por separado
        when(recolectorRepository.existsById(9L)).thenReturn(true);
        when(armadorRepository.existsById(9L)).thenReturn(true);
        lenient().when(areaRepository.existsById(9L)).thenReturn(false);
        lenient().when(comercializadorRepository.existsById(9L)).thenReturn(false);

        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 401L)).thenReturn(
                CadenaCustodiaService.Cadena.builder()
                        .origenes(List.of(CadenaCustodiaService.OrigenRef.builder().tipo("RECOLECTOR").id(9L).build()))
                        .build());
        when(recolectorRepository.findAllById(Set.of(9L))).thenReturn(List.of(
                DeclaracionRecolectorModel.builder().id(9L).captura(new BigDecimal("1500.00")).build()));

        CapturaOrigenProduccion.ResultadoCapturaOrigen resAmbiguo = service.resolverParaLotes(List.of(loteLegacy));
        assertFalse(resAmbiguo.completa());
        assertEquals(new BigDecimal("1500.00"), resAmbiguo.capturaOrigenKg());
        assertTrue(resAmbiguo.motivoIncompleta().contains(CapturaOrigenProduccion.MOTIVO_LEGACY_NO_ATRIBUIBLE));

        // Token huérfano 10022 (0 coincidencias) también marca completa=false
        DeclaracionPlantaAbastecimientoModel loteHuerfano = DeclaracionPlantaAbastecimientoModel.builder()
                .id(402L)
                .declaracionesSeleccionadas("10022")
                .build();
        when(recolectorRepository.existsById(10022L)).thenReturn(false);
        when(armadorRepository.existsById(10022L)).thenReturn(false);
        when(areaRepository.existsById(10022L)).thenReturn(false);
        when(comercializadorRepository.existsById(10022L)).thenReturn(false);
        when(cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", 402L)).thenReturn(
                CadenaCustodiaService.Cadena.builder().origenes(List.of()).build());

        CapturaOrigenProduccion.ResultadoCapturaOrigen resHuerfano = service.resolverParaLotes(List.of(loteHuerfano));
        assertFalse(resHuerfano.completa());
        assertTrue(resHuerfano.motivoIncompleta().contains(CapturaOrigenProduccion.MOTIVO_LEGACY_NO_ATRIBUIBLE));
    }
}
