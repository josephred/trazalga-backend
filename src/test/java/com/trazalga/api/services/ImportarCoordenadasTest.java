package com.trazalga.api.services;

import com.trazalga.api.dto.geo.CoberturaCoordenadasDto;
import com.trazalga.api.dto.geo.EstadoFilaCoordenada;
import com.trazalga.api.dto.geo.ResumenImportacionDto;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.geo.ImportarCoordenadasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TG.1 - Pruebas de Carga Verificable de Coordenadas")
class ImportarCoordenadasTest {

    @Mock private ICaletaRepository caletaRepository;
    @Mock private IVaraderoRepository varaderoRepository;
    @Mock private IAmerbRepository amerbRepository;
    @Mock private IRegionRepository regionRepository;
    @Mock private ICoordenadaCambioLogRepository cambioLogRepository;

    @InjectMocks
    private ImportarCoordenadasService service;

    private RegionModel regionCoquimbo;
    private ComunaModel comunaCoquimbo;
    private ComunaModel comunaLaSerena;
    private ComunaModel comunaHuasco;

    private CaletaModel caletaPenuelas;
    private CaletaModel caletaElSoldado1;
    private CaletaModel caletaElSoldado2;
    private CaletaModel caletaGuanaqueros;
    private VaraderoModel varaderoSanPedro;
    private AmerbModel amerbPuntaChoros;

    @BeforeEach
    void setUp() {
        regionCoquimbo = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        comunaCoquimbo = ComunaModel.builder().id(41L).nombre("Coquimbo").region(regionCoquimbo).build();
        comunaLaSerena = ComunaModel.builder().id(42L).nombre("La Serena").region(regionCoquimbo).build();
        comunaHuasco = ComunaModel.builder().id(31L).nombre("Huasco").region(regionCoquimbo).build();

        caletaPenuelas = CaletaModel.builder()
                .id(1L)
                .nombre("Caleta Peñuelas")
                .region(regionCoquimbo)
                .comuna(comunaCoquimbo)
                .latitud(null)
                .longitud(null)
                .fuenteCoordenada(null)
                .build();

        caletaGuanaqueros = CaletaModel.builder()
                .id(2L)
                .nombre("Caleta Guanaqueros")
                .region(regionCoquimbo)
                .comuna(comunaCoquimbo)
                .latitud(-30.198)
                .longitud(-71.424)
                .fuenteCoordenada("ORIGINAL")
                .build();

        // Dos caletas homónimas llamadas "El Soldado" en distintas comunas
        caletaElSoldado1 = CaletaModel.builder()
                .id(3L)
                .nombre("Caleta El Soldado")
                .region(regionCoquimbo)
                .comuna(comunaCoquimbo)
                .build();

        caletaElSoldado2 = CaletaModel.builder()
                .id(4L)
                .nombre("Caleta El Soldado")
                .region(regionCoquimbo)
                .comuna(comunaHuasco)
                .build();

        varaderoSanPedro = VaraderoModel.builder()
                .id(10L)
                .nombre("Varadero San Pedro")
                .comuna(comunaLaSerena)
                .latitud(null)
                .longitud(null)
                .build();

        amerbPuntaChoros = AmerbModel.builder()
                .id(20L)
                .codigoSernapesca("AMERB-04-101")
                .nombre("Punta de Choros")
                .regionModel(regionCoquimbo)
                .comuna(comunaLaSerena)
                .latitud(null)
                .longitud(null)
                .build();
    }

    @Test
    @DisplayName("Criterio de Aceptación TG.1: CSV con 5 filas (3 calzan, 1 ambigua, 1 fuera de rango)")
    void testAceptacionCincoFilas() {
        // Simular maestros
        when(caletaRepository.findAll()).thenReturn(new ArrayList<>(List.of(caletaPenuelas, caletaGuanaqueros, caletaElSoldado1, caletaElSoldado2)));
        when(varaderoRepository.findAll()).thenReturn(new ArrayList<>(List.of(varaderoSanPedro)));
        when(amerbRepository.findAll()).thenReturn(new ArrayList<>(List.of(amerbPuntaChoros)));

        // CSV con 5 filas según criterio de aceptación TG.1:
        // Fila 1: Caleta Peñuelas (calza)
        // Fila 2: Varadero San Pedro (calza)
        // Fila 3: AMERB Punta de Choros (calza por código)
        // Fila 4: Caleta El Soldado (sin comuna -> ambigua por homónimos)
        // Fila 5: Caleta Guanaqueros (longitud -7.12 por error tipeo -> fuera de rango)
        String csvContent = "tipo;codigo;nombre;comuna;region;latitud;longitud;datum;fuente\n" +
                "CALETA;;Caleta Peñuelas;Coquimbo;Coquimbo;-29.9540;-71.2980;WGS84;PLANILLA_TEST\n" +
                "VARADERO;;Varadero San Pedro;La Serena;Coquimbo;-29.9020;-71.2510;WGS84;PLANILLA_TEST\n" +
                "AMERB;AMERB-04-101;Punta de Choros;La Serena;Coquimbo;-29.2500;-71.4500;WGS84;PLANILLA_TEST\n" +
                "CALETA;;Caleta El Soldado;;Coquimbo;-29.8000;-71.3000;WGS84;PLANILLA_TEST\n" +
                "CALETA;;Caleta Guanaqueros;Coquimbo;Coquimbo;-30.1980;-7.1200;WGS84;PLANILLA_TEST\n";

        // 1. Paso 1: Vista Previa (aplicar = false)
        InputStream streamVistaPrevia = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        ResumenImportacionDto vistaPrevia = service.procesarPlanilla(streamVistaPrevia, false, false, "ADMIN");

        assertNotNull(vistaPrevia);
        assertFalse(vistaPrevia.isAplicado());
        assertEquals(5, vistaPrevia.getTotalFilas());
        assertEquals(3, vistaPrevia.getCalzadas());
        assertEquals(1, vistaPrevia.getAmbiguas());
        assertEquals(1, vistaPrevia.getFueraDeRango());
        assertEquals(0, vistaPrevia.getSinCalce());
        assertEquals(0, vistaPrevia.getEnConflicto());
        assertEquals(0, vistaPrevia.getAplicadas());

        // Verificar estados de cada fila
        assertEquals(EstadoFilaCoordenada.CALZADA, vistaPrevia.getFilas().get(0).getEstado());
        assertEquals(EstadoFilaCoordenada.CALZADA, vistaPrevia.getFilas().get(1).getEstado());
        assertEquals(EstadoFilaCoordenada.CALZADA, vistaPrevia.getFilas().get(2).getEstado());
        assertEquals(EstadoFilaCoordenada.AMBIGUA, vistaPrevia.getFilas().get(3).getEstado());
        assertEquals(EstadoFilaCoordenada.FUERA_DE_RANGO, vistaPrevia.getFilas().get(4).getEstado());

        // Verificar que en vista previa los maestros NO hayan cambiado
        assertNull(caletaPenuelas.getLatitud());
        verify(caletaRepository, never()).saveAll(any());
        verify(cambioLogRepository, never()).saveAll(any());

        // 2. Paso 2: Aplicar (aplicar = true)
        InputStream streamAplicar = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        ResumenImportacionDto resultadoAplicar = service.procesarPlanilla(streamAplicar, true, false, "ADMIN");

        assertTrue(resultadoAplicar.isAplicado());
        assertEquals(3, resultadoAplicar.getAplicadas());

        // Verificar que cambiaron exactamente las 3 calzadas
        assertEquals(-29.9540, caletaPenuelas.getLatitud());
        assertEquals(-71.2980, caletaPenuelas.getLongitud());
        assertEquals("PLANILLA_TEST", caletaPenuelas.getFuenteCoordenada());

        assertEquals(-29.9020, varaderoSanPedro.getLatitud());
        assertEquals(-71.2510, varaderoSanPedro.getLongitud());

        assertEquals(-29.2500, amerbPuntaChoros.getLatitud());
        assertEquals(-71.4500, amerbPuntaChoros.getLongitud());

        // Verificar que Guanaqueros no cambió por estar fuera de rango
        assertEquals(-30.198, caletaGuanaqueros.getLatitud());

        // Verificar que se guardaron los cambios y los 3 logs de auditoría
        verify(caletaRepository, times(1)).saveAll(any());
        verify(varaderoRepository, times(1)).saveAll(any());
        verify(amerbRepository, times(1)).saveAll(any());
        verify(cambioLogRepository, times(1)).saveAll(argThat(logs -> {
            List<?> l = (List<?>) logs;
            return l.size() == 3;
        }));
    }

    @Test
    @DisplayName("Detección de conflicto con fuente MANUAL y parámetro forzar")
    void testConflictoManualYForzado() {
        // Caleta con coordenada manual previa
        CaletaModel caletaManual = CaletaModel.builder()
                .id(99L)
                .nombre("Caleta Coquimbo")
                .region(regionCoquimbo)
                .comuna(comunaCoquimbo)
                .latitud(-29.9500)
                .longitud(-71.3400)
                .fuenteCoordenada("MANUAL")
                .build();

        when(caletaRepository.findAll()).thenReturn(new ArrayList<>(List.of(caletaManual)));
        when(varaderoRepository.findAll()).thenReturn(new ArrayList<>());
        when(amerbRepository.findAll()).thenReturn(new ArrayList<>());

        // Planilla con coordenada distinta para Caleta Coquimbo
        String csv = "tipo;codigo;nombre;comuna;region;latitud;longitud;datum;fuente\n" +
                "CALETA;;Caleta Coquimbo;Coquimbo;Coquimbo;-29.9800;-71.3600;WGS84;SERNAPESCA\n";

        // Caso A: forzar = false -> debe quedar en EN_CONFLICTO y no aplicarse
        InputStream is1 = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        ResumenImportacionDto resSinForzar = service.procesarPlanilla(is1, true, false, "ADMIN");

        assertEquals(1, resSinForzar.getEnConflicto());
        assertEquals(0, resSinForzar.getAplicadas());
        assertEquals(-29.9500, caletaManual.getLatitud()); // Mantiene valor manual

        // Caso B: forzar = true -> se sobrescribe
        InputStream is2 = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        ResumenImportacionDto resConForzar = service.procesarPlanilla(is2, true, true, "ADMIN");

        assertEquals(1, resConForzar.getEnConflicto());
        assertEquals(1, resConForzar.getAplicadas());
        assertEquals(-29.9800, caletaManual.getLatitud());
        assertEquals("SERNAPESCA", caletaManual.getFuenteCoordenada());
    }

    @Test
    @DisplayName("Obtención de cobertura georreferencial por región y nacional")
    void testObtenerCobertura() {
        when(caletaRepository.findAll()).thenReturn(List.of(caletaPenuelas, caletaGuanaqueros));
        when(varaderoRepository.findAll()).thenReturn(List.of(varaderoSanPedro));
        when(amerbRepository.findAll()).thenReturn(List.of(amerbPuntaChoros));
        when(regionRepository.findAll()).thenReturn(List.of(regionCoquimbo));

        CoberturaCoordenadasDto cob = service.obtenerCobertura();

        assertNotNull(cob);
        assertEquals(2, cob.getTotalCaletas());
        assertEquals(1, cob.getCaletasConGps()); // Guanaqueros tiene GPS, Peñuelas no
        assertEquals(50.0, cob.getPorcentajeCaletas());

        assertEquals(1, cob.getTotalVaraderos());
        assertEquals(0, cob.getVaraderosConGps());

        assertEquals(1, cob.getTotalAmerb());
        assertEquals(0, cob.getAmerbConGps());

        assertEquals(4, cob.getTotalEntidades());
        assertEquals(1, cob.getTotalConGps());
        assertEquals(25.0, cob.getPorcentajeTotal());

        assertEquals(1, cob.getPorRegion().size());
        assertEquals("Coquimbo", cob.getPorRegion().get(0).getRegionNombre());
    }
}
