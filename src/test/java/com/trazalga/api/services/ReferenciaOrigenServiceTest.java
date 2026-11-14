package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.geo.PuntoReferencia;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IVaraderoRepository;
import com.trazalga.api.services.geo.ReferenciaOrigenService;

@ExtendWith(MockitoExtension.class)
public class ReferenciaOrigenServiceTest {

    @Mock
    private ICaletaRepository caletaRepository;

    @Mock
    private IVaraderoRepository varaderoRepository;

    @Mock
    private IAmerbRepository amerbRepository;

    @Mock
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @InjectMocks
    private ReferenciaOrigenService referenciaOrigenService;

    private CaletaModel caletaChoros;
    private VaraderoModel varaderoChoros;
    private AmerbModel amerbChoros;
    private ComunaModel comunaLaHiguera;

    @BeforeEach
    void setUp() {
        comunaLaHiguera = ComunaModel.builder()
                .id(10L)
                .nombre("La Higuera")
                .build();

        varaderoChoros = VaraderoModel.builder()
                .id(100L)
                .nombre("Varadero Los Choros")
                .latitud(-29.255)
                .longitud(-71.455)
                .comuna(comunaLaHiguera)
                .build();

        caletaChoros = CaletaModel.builder()
                .id(1L)
                .nombre("Caleta Los Choros")
                .latitud(-29.250)
                .longitud(-71.450)
                .comuna(comunaLaHiguera)
                .varadero(varaderoChoros)
                .build();

        amerbChoros = AmerbModel.builder()
                .id(200L)
                .nombre("AMERB Punta de Choros Sector A")
                .latitud(-29.260)
                .longitud(-71.460)
                .comuna(comunaLaHiguera)
                .build();

        when(caletaRepository.findAll()).thenReturn(List.of(caletaChoros));
        when(varaderoRepository.findAll()).thenReturn(List.of(varaderoChoros));
        when(amerbRepository.findAll()).thenReturn(List.of(amerbChoros));

        referenciaOrigenService.invalidarCache();
    }

    @Test
    void testResolverRecolector_ConVaraderoDirecto() {
        DeclaracionRecolectorModel dr = DeclaracionRecolectorModel.builder()
                .id(1L)
                .varaderoRelacion(varaderoChoros)
                .caleta(caletaChoros)
                .build();

        PuntoReferencia punto = referenciaOrigenService.resolverRecolector(dr);

        assertNotNull(punto);
        assertEquals("VARADERO", punto.getTipo());
        assertEquals(varaderoChoros.getId(), punto.getId());
        assertEquals(-29.255, punto.getLat(), 0.0001);
        assertEquals(-71.455, punto.getLon(), 0.0001);
        assertEquals("VARADERO_DECLARADO", punto.getFuente());
    }

    @Test
    void testResolverRecolector_ConVaraderoTextoCalzado() {
        DeclaracionRecolectorModel dr = DeclaracionRecolectorModel.builder()
                .id(2L)
                .varadero("VARADERO LOS CHOROS")
                .comuna(comunaLaHiguera)
                .build();

        PuntoReferencia punto = referenciaOrigenService.resolverRecolector(dr);

        assertNotNull(punto);
        assertEquals("VARADERO", punto.getTipo());
        assertEquals("Varadero Los Choros", punto.getNombre());
        assertEquals("VARADERO_DECLARADO", punto.getFuente());
    }

    @Test
    void testResolverRecolector_FallbackCaleta() {
        DeclaracionRecolectorModel dr = DeclaracionRecolectorModel.builder()
                .id(3L)
                .caleta(caletaChoros)
                .build();

        PuntoReferencia punto = referenciaOrigenService.resolverRecolector(dr);

        assertNotNull(punto);
        assertEquals("CALETA", punto.getTipo());
        assertEquals(caletaChoros.getId(), punto.getId());
        assertEquals(-29.250, punto.getLat(), 0.0001);
        assertEquals("CALETA", punto.getFuente());
    }

    @Test
    void testResolverArmador_CaletaDesembarque() {
        DeclaracionArmadorModel da = DeclaracionArmadorModel.builder()
                .id(4L)
                .caleta(caletaChoros)
                .build();

        PuntoReferencia punto = referenciaOrigenService.resolverArmador(da);

        assertNotNull(punto);
        assertEquals("CALETA", punto.getTipo());
        assertEquals(-29.250, punto.getLat(), 0.0001);
        assertEquals(-71.450, punto.getLon(), 0.0001);
    }

    @Test
    void testResolverArea_ConAmerb() {
        DeclaracionAreaModel dam = DeclaracionAreaModel.builder()
                .id(5L)
                .amerb(amerbChoros)
                .build();

        PuntoReferencia punto = referenciaOrigenService.resolverArea(dam);

        assertNotNull(punto);
        assertEquals("AMERB", punto.getTipo());
        assertEquals(amerbChoros.getId(), punto.getId());
        assertEquals(-29.260, punto.getLat(), 0.0001);
    }

    @Test
    void testCalzarVaradero_ConTextoNormalizadoYEspacios() {
        VaraderoModel match = referenciaOrigenService.calzarVaradero("  varadero   los   choros  ", 10L);
        assertNotNull(match);
        assertEquals(100L, match.getId());
    }

    @Test
    void testCalzarVaradero_ComunaInexistente_DevuelveNull() {
        VaraderoModel match = referenciaOrigenService.calzarVaradero("Varadero Los Choros", 999L);
        assertNull(match);
    }

    @Test
    void testVincularVaraderosHistoricos() {
        DeclaracionRecolectorModel drPendiente = DeclaracionRecolectorModel.builder()
                .id(10L)
                .varadero("Varadero Los Choros")
                .comuna(comunaLaHiguera)
                .build();

        when(declaracionRecolectorRepository.findAll()).thenReturn(List.of(drPendiente));

        int vinculados = referenciaOrigenService.vincularVaraderosHistoricos();

        assertEquals(1, vinculados);
        assertNotNull(drPendiente.getVaraderoRelacion());
        assertEquals(100L, drPendiente.getVaraderoRelacion().getId());
        verify(declaracionRecolectorRepository, times(1)).save(drPendiente);
    }

    @Test
    void testResolverPlantaOficial_PrioridadCodigoYReglasFallback() {
        com.trazalga.api.repositories.IPlantaRepository plantaRepo =
                org.mockito.Mockito.mock(com.trazalga.api.repositories.IPlantaRepository.class);

        com.trazalga.api.models.PlantaModel pCodigo = new com.trazalga.api.models.PlantaModel();
        pCodigo.setId(501L);
        pCodigo.setCodigo(9901);
        pCodigo.setRut("76.123.456-7");
        pCodigo.setNombre("Planta Caldera Oficial");
        pCodigo.setLatitud(-27.0667);
        pCodigo.setLongitud(-70.8206);
        pCodigo.setFuenteCoordenada("SERNAPESCA_2026_10");

        com.trazalga.api.models.PlantaModel pSinCoords = new com.trazalga.api.models.PlantaModel();
        pSinCoords.setId(502L);
        pSinCoords.setCodigo(9902);
        pSinCoords.setRut("76.123.456-7");
        pSinCoords.setNombre("Planta Sin Coords");

        com.trazalga.api.models.PlantaModel pUnicoRut = new com.trazalga.api.models.PlantaModel();
        pUnicoRut.setId(503L);
        pUnicoRut.setCodigo(9903);
        pUnicoRut.setRut("76555444-3");
        pUnicoRut.setNombre("Planta Unico Rut");
        pUnicoRut.setLatitud(-29.9500);
        pUnicoRut.setLongitud(-71.3400);

        com.trazalga.api.models.PlantaModel pMultiA = new com.trazalga.api.models.PlantaModel();
        pMultiA.setId(504L);
        pMultiA.setCodigo(9904);
        pMultiA.setRut("76.999.888-k");
        pMultiA.setNombre("Planta Norte");
        pMultiA.setLatitud(-25.4000);
        pMultiA.setLongitud(-70.4800);
        pMultiA.setFuenteCoordenada("MANUAL");

        com.trazalga.api.models.PlantaModel pMultiB = new com.trazalga.api.models.PlantaModel();
        pMultiB.setId(505L);
        pMultiB.setCodigo(9905);
        pMultiB.setRut("76999888-K");
        pMultiB.setNombre("Planta Sur");
        pMultiB.setLatitud(-30.1000);
        pMultiB.setLongitud(-71.4000);
        pMultiB.setFuenteCoordenada("SEMILLA");

        when(plantaRepo.findAll()).thenReturn(List.of(pCodigo, pSinCoords, pUnicoRut, pMultiA, pMultiB));
        referenciaOrigenService.setPlantaRepository(plantaRepo);
        referenciaOrigenService.invalidarCache();

        // 1. Prioridad codigo_sernapesca == planta.codigo
        PuntoReferencia refCod = referenciaOrigenService.resolverPlantaOficial(" 9901 ", "76123456-7", null);
        assertNotNull(refCod);
        assertEquals("PLANTA", refCod.getTipo());
        assertEquals(501L, refCod.getId());
        assertEquals(-27.0667, refCod.getLat(), 0.0001);
        assertEquals("SERNAPESCA_2026_10", refCod.getFuente());

        // 2. Si el codigo matched corresponde a planta sin coords, no salta a otro registro del mismo RUT
        assertNull(referenciaOrigenService.resolverPlantaOficial("9902", "76.123.456-7", null));

        // 3. Fallback por RUT único normalizado y fuente por defecto MAESTRO_PLANTA
        PuntoReferencia refRut = referenciaOrigenService.resolverPlantaOficial(null, "76.555.444-3", null);
        assertNotNull(refRut);
        assertEquals(503L, refRut.getId());
        assertEquals("MAESTRO_PLANTA", refRut.getFuente());

        // 4. RUT compartido entre plantas: nulo sin desambiguar, resuelto si coincide nombrePlanta normalizado
        assertNull(referenciaOrigenService.resolverPlantaOficial(null, "76.999.888-k", null));
        PuntoReferencia refDesamb = referenciaOrigenService.resolverPlantaOficial(null, "76.999.888-k", "  planta   sur ");
        assertNotNull(refDesamb);
        assertEquals(505L, refDesamb.getId());
        assertEquals("SEMILLA", refDesamb.getFuente());
    }
}
