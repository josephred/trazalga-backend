package com.trazalga.api.services.geo;

import com.trazalga.api.dto.geo.GeoDeclaracionDTO;
import com.trazalga.api.dto.geo.PuntoReferencia;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.ReportService;
import com.trazalga.api.services.geo.CorregirCoordenadasVaraderosService.PropuestaVaradero;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * YV.5 · Pruebas unitarias de la regla de corrección de varaderos (YV.1/YV.2)
 * con filas reales de Y0.2 y de validación estricta en rango de Chile (YV.3).
 */
class CorregirCoordenadasVaraderosReglaTest {

    @Test
    @DisplayName("YV.5: Varaderos reales de Y0.2 quedan dentro de Chile y a menos de 50 m del DMS reordenado")
    void varaderosRealesY02_quedanDentroDeChileYAmenosDe50mDeDms() {
        // id=1 LENGUA DE VACA (Coquimbo)
        VaraderoModel lenguaDeVaca = VaraderoModel.builder()
                .id(1L).nombre("LENGUA DE VACA")
                .latGra(30).latMin(14).latSeg(16.707)
                .longGra(37).longMin(37).longSeg(-30.237974)
                .latitud(71.0).longitud(-71.627042)
                .build();

        // id=76 LA VERONICA (Ovalle) — caso con error de punto decimal en long_seg (-42.237222 vs DMS -30.828087)
        VaraderoModel laVeronica = VaraderoModel.builder()
                .id(76L).nombre("LA VERONICA")
                .latGra(30).latMin(49).latSeg(41.114)
                .longGra(41).longMin(45).longSeg(-42.237222)
                .latitud(71.0).longitud(-71.695781)
                .build();

        // id=149 LOS RIPIOS (mayor diferencia de longitud por truncamiento entero de long_min: ~13.3 m)
        VaraderoModel losRipios = VaraderoModel.builder()
                .id(149L).nombre("LOS RIPIOS")
                .latGra(30).latMin(32).latSeg(27.64)
                .longGra(41).longMin(39).longSeg(-30.541011)
                .latitud(71.0).longitud(-71.694028)
                .build();

        // id=274 CALETA HORNOS
        VaraderoModel caletaHornos = VaraderoModel.builder()
                .id(274L).nombre("CALETA HORNOS")
                .latGra(29).latMin(37).latSeg(20.831)
                .longGra(17).longMin(29).longSeg(-29.622453)
                .latitud(71.0).longitud(-71.29148)
                .build();

        PropuestaVaradero p1 = CorregirCoordenadasVaraderosService.evaluarVaradero(lenguaDeVaca);
        assertEquals(CorregirCoordenadasVaraderosService.REGLA_DESPLAZAMIENTO_DMS, p1.regla());
        assertTrue(p1.dentroDeChile());
        assertTrue(p1.requiereCambio());
        assertEquals(-30.237974, p1.latPropuesta(), 1e-6);
        assertEquals(-71.627042, p1.lonPropuesta(), 1e-6);
        assertEquals(71, p1.longGraPropuesto());
        assertEquals(37, p1.longMinPropuesto());
        assertEquals(37.0, p1.longSegPropuesto());
        assertNotNull(p1.diferenciaDmsMetros());
        assertTrue(p1.diferenciaDmsMetros() < 50.0, "Diferencia contra DMS debe ser < 50 m: " + p1.diferenciaDmsMetros());

        PropuestaVaradero p76 = CorregirCoordenadasVaraderosService.evaluarVaradero(laVeronica);
        assertEquals(CorregirCoordenadasVaraderosService.REGLA_DESPLAZAMIENTO_DMS, p76.regla());
        assertTrue(p76.dentroDeChile());
        assertEquals(-30.828087, p76.latPropuesta(), 1e-6, "LA VERONICA debe quedar en Ovalle (-30.828087), no en -42.237222");
        assertEquals(-71.695781, p76.lonPropuesta(), 1e-6);
        assertTrue(p76.diferenciaDmsMetros() < 50.0);

        for (VaraderoModel v : List.of(losRipios, caletaHornos)) {
            PropuestaVaradero p = CorregirCoordenadasVaraderosService.evaluarVaradero(v);
            assertEquals(CorregirCoordenadasVaraderosService.REGLA_DESPLAZAMIENTO_DMS, p.regla());
            assertTrue(p.dentroDeChile());
            assertTrue(CoordenadaNormalizer.estaEnRangoChile(p.latPropuesta(), p.lonPropuesta()));
            assertNotNull(p.diferenciaDmsMetros());
            assertTrue(p.diferenciaDmsMetros() < 50.0, v.getNombre() + " diff=" + p.diferenciaDmsMetros());
        }
    }

    @Test
    @DisplayName("YV.5: Regla de signo positivo como respaldo y rechazo de coordenadas no corregibles")
    void reglaSignoYNoCorregible() {
        VaraderoModel signoPositivo = VaraderoModel.builder()
                .id(500L).nombre("VARADERO SIGNO POSITIVO")
                .latitud(29.95).longitud(71.34)
                .build();
        PropuestaVaradero pSigno = CorregirCoordenadasVaraderosService.evaluarVaradero(signoPositivo);
        assertEquals(CorregirCoordenadasVaraderosService.REGLA_SIGNO, pSigno.regla());
        assertTrue(pSigno.dentroDeChile());
        assertEquals(-29.95, pSigno.latPropuesta(), 1e-6);
        assertEquals(-71.34, pSigno.lonPropuesta(), 1e-6);

        VaraderoModel noCorregible = VaraderoModel.builder()
                .id(501L).nombre("VARADERO FUERA DE CHILE")
                .latitud(5.0).longitud(5.0)
                .build();
        PropuestaVaradero pNo = CorregirCoordenadasVaraderosService.evaluarVaradero(noCorregible);
        assertEquals(CorregirCoordenadasVaraderosService.REGLA_NO_CORREGIBLE, pNo.regla());
        assertFalse(pNo.dentroDeChile());
        assertFalse(pNo.requiereCambio());
        assertNull(pNo.latPropuesta());
    }

    @Test
    @DisplayName("YV.3 / YV.5: Varadero con coordenada positiva no se usa como referencia y la cascada sigue con caleta o GPS")
    void yv3_varaderoInvalidoSeDescartaYCascadaContinua() {
        ICaletaRepository caletaRepo = mock(ICaletaRepository.class);
        IVaraderoRepository varaderoRepo = mock(IVaraderoRepository.class);
        IAmerbRepository amerbRepo = mock(IAmerbRepository.class);
        IDeclaracionRecolectorRepository drRepo = mock(IDeclaracionRecolectorRepository.class);
        IPlantaRepository plantaRepo = mock(IPlantaRepository.class);

        VaraderoModel varaderoInvalido = VaraderoModel.builder()
                .id(1L).nombre("LENGUA DE VACA").latitud(71.0).longitud(-71.627042).build();
        CaletaModel caletaSinCoord = CaletaModel.builder()
                .id(10L).nombre("Caleta Sin Coord").varadero(varaderoInvalido).build();
        CaletaModel caletaValida = CaletaModel.builder()
                .id(11L).nombre("Caleta Valida").latitud(-29.96).longitud(-71.34).build();
        PlantaModel plantaInvalida = PlantaModel.builder()
                .id(20L).codigo(999).nombre("Planta Mala").latitud(30.0).longitud(71.0).build();

        when(varaderoRepo.findAll()).thenReturn(List.of(varaderoInvalido));
        when(caletaRepo.findAll()).thenReturn(List.of(caletaSinCoord, caletaValida));
        when(amerbRepo.findAll()).thenReturn(List.of());
        when(plantaRepo.findAll()).thenReturn(List.of(plantaInvalida));

        ReferenciaOrigenService svc = new ReferenciaOrigenService(caletaRepo, varaderoRepo, amerbRepo, drRepo);
        svc.setPlantaRepository(plantaRepo);
        svc.inicializarCache();

        // 1) Recolector con varadero inválido y caleta sin coord -> no usa varadero, retorna null (sin falsa alerta) y cae a GPS
        DeclaracionRecolectorModel drSinCaletaValida = DeclaracionRecolectorModel.builder()
                .id(100L).varaderoRelacion(varaderoInvalido).caleta(caletaSinCoord)
                .latitud(-30.238).longitud(-71.627).build();
        assertNull(svc.resolverRecolector(drSinCaletaValida));
        assertNull(svc.resolverPorIds("RECOLECTOR", 10L, null, 1L, null, null));

        GeoDeclaracionDTO geoGps = svc.armarGeoDeclaracion("RECOLECTOR", drSinCaletaValida, 30.0, 500.0);
        assertNull(geoGps.getReferencia(), "No debe usar un varadero fuera de Chile como referencia");
        assertNotNull(geoGps.getGps(), "Debe conservar el GPS de la declaración");
        assertEquals("SIN_REFERENCIA", geoGps.getEvaluacion(), "No debe marcar LEJANA contra coordenada inválida");

        // 2) Recolector con varadero inválido y caleta válida -> descarta varadero y cae a la caleta válida
        DeclaracionRecolectorModel drConCaletaValida = DeclaracionRecolectorModel.builder()
                .id(101L).varaderoRelacion(varaderoInvalido).caleta(caletaValida)
                .latitud(-29.96).longitud(-71.34).build();
        PuntoReferencia refCaleta = svc.resolverRecolector(drConCaletaValida);
        assertNotNull(refCaleta);
        assertEquals("CALETA", refCaleta.getTipo());
        assertEquals(11L, refCaleta.getId());

        // 3) Planta con coordenada positiva en maestro -> descartada; resolverPlanta cae al GPS válido
        assertNull(svc.resolverPlantaOficial("999", null, "Planta Mala"));
        DeclaracionPlantaAbastecimientoModel dp = DeclaracionPlantaAbastecimientoModel.builder()
                .id(200L).codigoSernapesca("999").nombrePlanta("Planta Mala")
                .latitud(-29.95).longitud(-71.33).build();
        PuntoReferencia refPlanta = svc.resolverPlanta(dp);
        assertNotNull(refPlanta);
        assertEquals("GPS_DECLARACION", refPlanta.getFuente());
    }

    @Test
    @DisplayName("YV.3: ReportService.esCoordenadaChileValida y CoordenadaNormalizer.estaEnRangoChile comparten un solo criterio")
    void criterioUnicoChileEntreReportServiceYCoordenadaNormalizer() {
        // Antes ReportService aceptaba -17.2 (límite -17.0) y -66.2 (límite -66.0); ahora ambos usan -17.4 y -66.4
        assertFalse(CoordenadaNormalizer.estaEnRangoChile(-17.2, -70.0));
        assertFalse(ReportService.esCoordenadaChileValida(-17.2, -70.0));

        assertFalse(CoordenadaNormalizer.estaEnRangoChile(-20.0, -66.2));
        assertFalse(ReportService.esCoordenadaChileValida(-20.0, -66.2));

        assertTrue(CoordenadaNormalizer.estaEnRangoChile(-30.237974, -71.627042));
        assertTrue(ReportService.esCoordenadaChileValida(-30.237974, -71.627042));

        assertFalse(CoordenadaNormalizer.estaEnRangoChile(Double.NaN, -71.0));
        assertFalse(ReportService.esCoordenadaChileValida(Double.NaN, -71.0));
    }
}
