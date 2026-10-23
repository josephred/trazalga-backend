package com.trazalga.api.services;

import com.trazalga.api.dto.FichaTrazabilidadDTO;
import com.trazalga.api.dto.TrazabilidadNodoDTO;
import com.trazalga.api.dto.geo.GeoDeclaracionDTO;
import com.trazalga.api.dto.geo.PuntoReferencia;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.geo.ReferenciaOrigenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeoDeclaracionDTOTest {

    @Mock private ICaletaRepository caletaRepository;
    @Mock private IVaraderoRepository varaderoRepository;
    @Mock private IAmerbRepository amerbRepository;
    @Mock private IDeclaracionRecolectorRepository recolectorRepository;
    @Mock private IDeclaracionArmadorRepository armadorRepository;
    @Mock private IDeclaracionAreaRepository decAreaRepository;
    @Mock private IDeclaracionComercializadorRepository comercializadorRepository;
    @Mock private IDeclaracionPlantaAbastecimientoRepository plantaRepository;
    @Mock private DeclaracionMarcaService declaracionMarcaService;
    @Mock private BloqueoCargaService bloqueoCargaService;
    @Mock private EntityManager entityManager;
    @Mock private ConfiguracionGeneralService configService;

    private ReferenciaOrigenService referenciaOrigenService;

    @BeforeEach
    void setUp() {
        referenciaOrigenService = new ReferenciaOrigenService(
                caletaRepository, varaderoRepository, amerbRepository, recolectorRepository
        );
        when(configService.getDouble("origen_geo_distancia_max_km", 30.0)).thenReturn(30.0);
        when(configService.getDouble("origen_geo_precision_max_m", 500.0)).thenReturn(500.0);
        when(configService.getInt("retencion_humedo_max_horas", 24)).thenReturn(24);
        when(configService.getInt("retencion_semihumedo_max_horas", 72)).thenReturn(72);
        when(configService.getInt("retencion_semiseco_max_horas", 216)).thenReturn(216);
        when(configService.getInt("retencion_preaviso_pct", 80)).thenReturn(80);
    }

    @Test
    @DisplayName("TG.3: Evaluaciones de GeoDeclaracionDTO (COHERENTE, LEJANA, PRECISION_BAJA, SIN_GPS, SIN_REFERENCIA)")
    void testEvaluacionesGeoDeclaracion() {
        PuntoReferencia ref = new PuntoReferencia("VARADERO", 10L, "Playa Grande", -29.95, -71.34, "VARADERO_DECLARADO");
        String ts = "2026-10-15T10:30:00";

        // 1. COHERENTE (distancia < 30 km, precision <= 500 m)
        GeoDeclaracionDTO coherente = ReferenciaOrigenService.construirGeoDeclaracion(
                "RECOLECTOR", -29.96, -71.35, 15.0, ts, false, ref, 30.0, 500.0
        );
        assertEquals("COHERENTE", coherente.getEvaluacion());
        assertNotNull(coherente.getGps());
        assertNotNull(coherente.getReferencia());
        assertNotNull(coherente.getDistanciaKm());
        assertTrue(coherente.getDistanciaKm() < 5.0);
        assertEquals(30.0, coherente.getUmbralKm());

        // 2. LEJANA (distancia > 30 km)
        GeoDeclaracionDTO lejana = ReferenciaOrigenService.construirGeoDeclaracion(
                "RECOLECTOR", -33.45, -70.66, 12.0, ts, false, ref, 30.0, 500.0
        );
        assertEquals("LEJANA", lejana.getEvaluacion());
        assertNotNull(lejana.getDistanciaKm());
        assertTrue(lejana.getDistanciaKm() > 300.0);

        // 3. PRECISION_BAJA (precision > 500 m)
        GeoDeclaracionDTO precisionBaja = ReferenciaOrigenService.construirGeoDeclaracion(
                "RECOLECTOR", -29.96, -71.35, 650.0, ts, true, ref, 30.0, 500.0
        );
        assertEquals("PRECISION_BAJA", precisionBaja.getEvaluacion());
        assertTrue(precisionBaja.getGps().getOffline());

        // 4. SIN_GPS
        GeoDeclaracionDTO sinGps = ReferenciaOrigenService.construirGeoDeclaracion(
                "RECOLECTOR", null, null, null, null, false, ref, 30.0, 500.0
        );
        assertEquals("SIN_GPS", sinGps.getEvaluacion());
        assertNull(sinGps.getGps());
        assertNotNull(sinGps.getReferencia());
        assertNull(sinGps.getDistanciaKm());

        // 5. SIN_REFERENCIA
        GeoDeclaracionDTO sinRef = ReferenciaOrigenService.construirGeoDeclaracion(
                "RECOLECTOR", -29.96, -71.35, 10.0, ts, false, null, 30.0, 500.0
        );
        assertEquals("SIN_REFERENCIA", sinRef.getEvaluacion());
        assertNotNull(sinRef.getGps());
        assertNull(sinRef.getReferencia());
        assertNull(sinRef.getDistanciaKm());
    }

    @Test
    @DisplayName("TG.3/TG.6: CadenaCustodiaService y FichaTrazabilidadService incluyen objeto geo en nodos y origenes")
    void testCadenaCustodiaYFichaIncluyenGeo() {
        VaraderoModel varadero = new VaraderoModel();
        varadero.setId(77L);
        varadero.setNombre("Varadero Totoralillo");
        varadero.setLatitud(-30.071);
        varadero.setLongitud(-71.375);
        when(varaderoRepository.findById(77L)).thenReturn(Optional.of(varadero));
        when(varaderoRepository.findAll()).thenReturn(Collections.singletonList(varadero));
        when(caletaRepository.findAll()).thenReturn(Collections.emptyList());
        when(amerbRepository.findAll()).thenReturn(Collections.emptyList());
        referenciaOrigenService.inicializarCache();

        CadenaCustodiaService cadenaService = new CadenaCustodiaService(entityManager, configService);
        cadenaService.setReferenciaOrigenService(referenciaOrigenService);

        Object[] rowRec = new Object[47];
        rowRec[0] = 10L;
        rowRec[1] = "Juan";
        rowRec[2] = "Perez";
        rowRec[3] = "11.111.111-1";
        rowRec[4] = new Date();
        rowRec[5] = new BigDecimal("500.00");
        rowRec[6] = "RO-010";
        rowRec[7] = null;
        rowRec[8] = null;
        rowRec[10] = "09:00:00";
        rowRec[12] = 1L;
        rowRec[13] = "Huiro Negro";
        rowRec[14] = 1L;
        rowRec[15] = "HÚMEDO";
        rowRec[20] = "Varadero Totoralillo"; // varNom
        rowRec[22] = -30.072; // lat
        rowRec[23] = -71.376; // lon
        rowRec[41] = 77L;     // varadero_id
        rowRec[43] = 18.0;    // precision_m
        rowRec[44] = "2026-10-15T09:00:00"; // gps_capturado_en
        rowRec[45] = false;   // envio_offline

        Query qRec = mock(Query.class);
        when(qRec.setParameter(eq("ids"), any())).thenReturn(qRec);
        when(qRec.getResultList()).thenReturn(Collections.singletonList(rowRec));
        when(entityManager.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);

        CadenaCustodiaService.Cadena cadena = cadenaService.resolver("RECOLECTOR", 10L);
        assertEquals(1, cadena.getNodos().size());
        TrazabilidadNodoDTO nodo = cadena.getNodos().get(0);
        assertNotNull(nodo.getGeo(), "El nodo de la traza debe incluir el objeto geo");
        assertEquals("COHERENTE", nodo.getGeo().getEvaluacion());
        assertEquals("VARADERO", nodo.getGeo().getReferencia().getTipo());
        assertEquals("Varadero Totoralillo", nodo.getGeo().getReferencia().getNombre());

        // Verificar FichaTrazabilidadService
        DeclaracionRecolectorModel recModel = new DeclaracionRecolectorModel();
        recModel.setId(10L);
        recModel.setFolioOrigen("RO-010");
        recModel.setFechaDeclaracion(new Date());
        recModel.setDesembarque(new BigDecimal("500.00"));
        recModel.setLatitud(-30.072);
        recModel.setLongitud(-71.376);
        recModel.setPrecisionGpsM(18.0);
        recModel.setGpsCapturadoEn(new Date());
        recModel.setEnvioOffline(false);
        recModel.setVaraderoRelacion(varadero);
        recModel.setVaradero("Varadero Totoralillo");
        when(recolectorRepository.findById(10L)).thenReturn(Optional.of(recModel));
        when(recolectorRepository.findAllById(any())).thenReturn(Collections.singletonList(recModel));

        FichaTrazabilidadService fichaService = new FichaTrazabilidadService(
                cadenaService,
                declaracionMarcaService,
                bloqueoCargaService,
                recolectorRepository,
                armadorRepository,
                decAreaRepository,
                comercializadorRepository,
                plantaRepository,
                null,
                null,
                null
        );
        fichaService.setReferenciaOrigenService(referenciaOrigenService);
        fichaService.setConfigService(configService);

        FichaTrazabilidadDTO ficha = fichaService.obtenerFicha("RECOLECTOR", 10L);
        assertNotNull(ficha);
        assertEquals(1, ficha.getOrigenes().size());
        assertNotNull(ficha.getOrigenes().get(0).getGeo(), "El origen de la ficha debe incluir el objeto geo");
        assertEquals("COHERENTE", ficha.getOrigenes().get(0).getGeo().getEvaluacion());
        assertEquals("VARADERO_DECLARADO", ficha.getOrigenes().get(0).getGeo().getReferencia().getFuente());
    }
}
