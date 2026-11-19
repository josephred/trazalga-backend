package com.trazalga.api.services.geo;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICoordenadaCambioLogRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IVaraderoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * YV.5 · Prueba de integración de {@code POST /api/admin/coordenadas/varaderos/corregir}:
 * simulación sin escritura, aplicación con respaldo y log, idempotencia en segunda corrida,
 * protección de filas no corregibles y de caletas cargadas desde planilla oficial, y reflejo
 * inmediato en {@code GET /api/reportes/capas-georreferencia}.
 */
@AutoConfigureMockMvc
class CorregirCoordenadasVaraderosIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private IVaraderoRepository varaderoRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private ICoordenadaCambioLogRepository cambioLogRepo;
    @Autowired private ReferenciaOrigenService referenciaOrigenService;

    private VaraderoModel vLengua;
    private VaraderoModel vVeronica;
    private VaraderoModel vIncorregible;
    private CaletaModel cHeredada;
    private CaletaModel cPlanillaSergio;

    @BeforeEach
    void prepararEscenario() {
        cambioLogRepo.deleteAll();
        caletaRepo.deleteAll();
        varaderoRepo.deleteAll();
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + CorregirCoordenadasVaraderosService.TABLA_RESPALDO);

        RegionModel reg = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo YV5").codigo("04YV").build());
        ProvinciaModel prov = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui YV5").region(reg).build());
        ComunaModel com = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Coquimbo YV5").region(reg).provincia(prov).build());

        vLengua = varaderoRepo.saveAndFlush(VaraderoModel.builder()
                .nombre("LENGUA DE VACA").comuna(com)
                .latGra(30).latMin(14).latSeg(16.707)
                .longGra(37).longMin(37).longSeg(-30.237974)
                .latitud(71.0).longitud(-71.627042)
                .build());

        vVeronica = varaderoRepo.saveAndFlush(VaraderoModel.builder()
                .nombre("LA VERONICA").comuna(com)
                .latGra(30).latMin(49).latSeg(41.114)
                .longGra(41).longMin(45).longSeg(-42.237222)
                .latitud(71.0).longitud(-71.695781)
                .build());

        vIncorregible = varaderoRepo.saveAndFlush(VaraderoModel.builder()
                .nombre("VARADERO SIN SOLUCION").comuna(com)
                .latitud(0.0).longitud(0.0)
                .build());

        // Caleta que heredó coordenada inválida de vLengua (sin fuente de planilla oficial)
        cHeredada = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Heredada YV5").region(reg).comuna(com).varadero(vLengua)
                .latitud(71.0).longitud(-71.627042)
                .build());

        // Caleta oficial de la planilla de Sergio (QG) -> nunca se toca
        cPlanillaSergio = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Oficial QG").region(reg).comuna(com).varadero(vLengua)
                .latitud(-29.96).longitud(-71.34)
                .fuenteCoordenada("Sernapesca - Trazalga_Caletas_IVRegion.xlt (13-11-2026)")
                .build());

        referenciaOrigenService.invalidarCache();
    }

    @Test
    @DisplayName("YV.5: Simulación no escribe; aplicar respalda, registra log, actualiza capas y segunda corrida es idempotente")
    void flujoCompletoCorreccionVaraderos() throws Exception {
        // 0) Seguridad: USER recibe 403
        mockMvc.perform(post("/api/admin/coordenadas/varaderos/corregir")
                        .param("aplicar", "false")
                        .with(user("recolector").roles("USER")))
                .andExpect(status().isForbidden());

        // 1) Simulación (aplicar=false): reporta propuestas pero no escribe
        mockMvc.perform(post("/api/admin/coordenadas/varaderos/corregir")
                        .param("aplicar", "false")
                        .with(user("admin_yv5").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.totalVaraderos").value(3))
                .andExpect(jsonPath("$.corregibles").value(2))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.noCorregiblesCount").value(1))
                .andExpect(jsonPath("$.caletasHeredadasCorregibles").value(1))
                .andExpect(jsonPath("$.caletasProtegidasPlanilla").value(1))
                .andExpect(jsonPath("$.noCorregibles[0].nombre").value("VARADERO SIN SOLUCION"));

        assertEquals(71.0, varaderoRepo.findById(vLengua.getId()).orElseThrow().getLatitud());
        assertEquals(0L, cambioLogRepo.count());

        // Antes de aplicar: capas-georreferencia reporta 0/3 varaderos válidos
        mockMvc.perform(get("/api/reportes/capas-georreferencia")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobertura.varaderos.total").value(3))
                .andExpect(jsonPath("$.cobertura.varaderos.conCoordenadas").value(0));

        // 2) Aplicar (aplicar=true): respalda, actualiza los 2 corregibles + caleta heredada, deja intacto el incorregible y QG
        mockMvc.perform(post("/api/admin/coordenadas/varaderos/corregir")
                        .param("aplicar", "true")
                        .with(user("admin_yv5").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.actualizados").value(2))
                .andExpect(jsonPath("$.caletasHeredadasActualizadas").value(1))
                .andExpect(jsonPath("$.noCorregiblesCount").value(1));

        VaraderoModel vLenguaPost = varaderoRepo.findById(vLengua.getId()).orElseThrow();
        assertEquals(-30.237974, vLenguaPost.getLatitud(), 1e-6);
        assertEquals(-71.627042, vLenguaPost.getLongitud(), 1e-6);
        assertEquals(71, vLenguaPost.getLongGra());
        assertEquals(37, vLenguaPost.getLongMin());
        assertEquals(37.0, vLenguaPost.getLongSeg());
        assertEquals(CorregirCoordenadasVaraderosService.REGLA_DESPLAZAMIENTO_DMS, vLenguaPost.getFuenteCoordenada());

        VaraderoModel vVeronicaPost = varaderoRepo.findById(vVeronica.getId()).orElseThrow();
        assertEquals(-30.828087, vVeronicaPost.getLatitud(), 1e-6);
        assertEquals(-71.695781, vVeronicaPost.getLongitud(), 1e-6);

        VaraderoModel vIncorrPost = varaderoRepo.findById(vIncorregible.getId()).orElseThrow();
        assertEquals(0.0, vIncorrPost.getLatitud(), 1e-6);
        assertNull(vIncorrPost.getFuenteCoordenada());

        CaletaModel cHeredadaPost = caletaRepo.findById(cHeredada.getId()).orElseThrow();
        assertEquals(-30.237974, cHeredadaPost.getLatitud(), 1e-6);
        assertEquals(CorregirCoordenadasVaraderosService.FUENTE_CALETA_HEREDADA, cHeredadaPost.getFuenteCoordenada());

        CaletaModel cQgPost = caletaRepo.findById(cPlanillaSergio.getId()).orElseThrow();
        assertEquals(-29.96, cQgPost.getLatitud(), 1e-6);
        assertEquals("Sernapesca - Trazalga_Caletas_IVRegion.xlt (13-11-2026)", cQgPost.getFuenteCoordenada());

        // Respaldo y log de auditoría
        Integer countRespaldoVar = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + CorregirCoordenadasVaraderosService.TABLA_RESPALDO + " WHERE entidad_tipo = 'VARADERO'",
                Integer.class);
        Integer countRespaldoCal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + CorregirCoordenadasVaraderosService.TABLA_RESPALDO + " WHERE entidad_tipo = 'CALETA'",
                Integer.class);
        assertEquals(2, countRespaldoVar);
        assertEquals(1, countRespaldoCal);
        assertEquals(3L, cambioLogRepo.count());

        // Después de aplicar: capas-georreferencia reporta 2/3 varaderos válidos y caché actualizada
        mockMvc.perform(get("/api/reportes/capas-georreferencia")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobertura.varaderos.total").value(3))
                .andExpect(jsonPath("$.cobertura.varaderos.conCoordenadas").value(2))
                .andExpect(jsonPath("$.varaderos", hasSize(2)));

        assertNotNull(referenciaOrigenService.resolverPorIds("RECOLECTOR", null, null, vLengua.getId(), null, null));

        // 3) Segunda corrida (idempotencia): no cambia nada ni agrega logs
        mockMvc.perform(post("/api/admin/coordenadas/varaderos/corregir")
                        .param("aplicar", "true")
                        .with(user("admin_yv5").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yaValidos").value(2))
                .andExpect(jsonPath("$.corregibles").value(0))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.caletasHeredadasActualizadas").value(0));

        assertEquals(3L, cambioLogRepo.count());
    }
}
