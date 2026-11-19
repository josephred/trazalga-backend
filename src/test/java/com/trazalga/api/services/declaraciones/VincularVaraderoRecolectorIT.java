package com.trazalga.api.services.declaraciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.config.CacheConfig;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.geo.ReferenciaOrigenService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * YR.4 · Pruebas de integración de:
 * <ul>
 *   <li>{@code POST /api/admin/declaraciones/recolector/vincular-varadero}: simulación sin escritura,
 *       aplicación sólo de calces únicos con respaldo y log, textos ambiguos nulos, idempotencia y
 *       cero marcas creadas;</li>
 *   <li>{@code GET /api/reportes/flujos-origen-destino}, exportación SIG ({@code REF_ORI}) y ficha de
 *       trazabilidad: un recolector con varadero ligado dibuja su origen en el varadero con fuente
 *       {@code VARADERO}.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class VincularVaraderoRecolectorIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheManager cacheManager;
    @Autowired private ReferenciaOrigenService referenciaOrigenService;

    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private IVaraderoRepository varaderoRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepo;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastRepo;
    @Autowired private IDeclaracionMarcaRepository marcaRepo;

    private VaraderoModel varLengua;
    private CaletaModel calCoquimbo;
    private UsuarioModel uRec;
    private UsuarioModel uPlanta;
    private EspecieModel especie;
    private ExtraccionTipoModel metodo;
    private HumedadEstadoModel humedad;
    private DeclaracionRecolectorModel drUnico;
    private DeclaracionRecolectorModel drAmbiguo;
    private DeclaracionRecolectorModel drSinCalce;
    private DeclaracionRecolectorModel drSinTexto;

    @BeforeEach
    void preparar() {
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        limpiarDatos();

        RegionModel reg = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo YR4").codigo("04YR").build());
        ProvinciaModel prov = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui YR4").region(reg).build());
        ComunaModel comCoquimbo = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Coquimbo YR4").region(reg).provincia(prov).build());
        ComunaModel comOvalle = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Ovalle YR4").region(reg).provincia(prov).build());

        calCoquimbo = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Coquimbo YR4").region(reg).comuna(comCoquimbo)
                .latitud(-29.96).longitud(-71.34).build());
        CaletaModel calOvalle = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Ovalle YR4").region(reg).comuna(comOvalle).build());

        varLengua = varaderoRepo.saveAndFlush(VaraderoModel.builder()
                .nombre("LENGUA DE VACA").comuna(comCoquimbo)
                .latitud(-30.237974).longitud(-71.627042)
                .fuenteCoordenada("CORRECCION_DESPLAZAMIENTO_DMS_20261119").build());

        // Dos varaderos con el mismo nombre en la misma comuna -> ambiguo
        varaderoRepo.saveAndFlush(VaraderoModel.builder()
                .nombre("LA LOBERA").comuna(comOvalle).latitud(-30.50).longitud(-71.68).build());
        varaderoRepo.saveAndFlush(VaraderoModel.builder()
                .nombre("LA LOBERA").comuna(comOvalle).latitud(-30.52).longitud(-71.69).build());

        especie = especieRepo.saveAndFlush(EspecieModel.builder().nombre("Huiro Negro YR4").activo(true).build());
        metodo = extraccionTipoRepo.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado YR4").build());
        humedad = humedadRepo.saveAndFlush(HumedadEstadoModel.builder().nombre("Humedo YR4").rangoInicio(60).rangoFin(85).build());

        String suf = String.valueOf(System.nanoTime() % 100000);
        uRec = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("12." + suf + "-9").nombres("Recolector").apellidop("YR4").comuna(comCoquimbo).estado("ACTIVO").build());
        uPlanta = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("76." + suf + "-K").nombres("Planta").apellidop("YR4").comuna(comCoquimbo).estado("ACTIVO").build());

        Date fecha = Date.valueOf("2026-10-15");

        drUnico = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-YR4-1").fechaExtraccion(fecha).fechaDeclaracion(fecha).hora("09:00:00")
                .nombre("Recolector YR4").codigoSernapesca("RPA-YR4-1")
                .caleta(calCoquimbo).comuna(comCoquimbo).varadero("lengua de vaca")
                .latitud(-30.10).longitud(-71.50)
                .especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(new BigDecimal("120.00")).captura(new BigDecimal("130.00"))
                .codigoDestinatario(uPlanta.getRut()).nombreDestinatario("Planta YR4")
                .usuarioDestinatario(uPlanta).build());

        drAmbiguo = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-YR4-2").fechaExtraccion(fecha).fechaDeclaracion(fecha).hora("09:30:00")
                .nombre("Recolector YR4").codigoSernapesca("RPA-YR4-2")
                .caleta(calOvalle).comuna(comOvalle).varadero("LA LOBERA")
                .especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(new BigDecimal("80.00")).captura(new BigDecimal("85.00"))
                .codigoDestinatario(uPlanta.getRut()).nombreDestinatario("Planta YR4")
                .usuarioDestinatario(uPlanta).build());

        drSinCalce = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-YR4-3").fechaExtraccion(fecha).fechaDeclaracion(fecha).hora("10:00:00")
                .nombre("Recolector YR4").codigoSernapesca("RPA-YR4-3")
                .caleta(calCoquimbo).comuna(comCoquimbo).varadero("VARADERO INEXISTENTE")
                .especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(new BigDecimal("50.00")).captura(new BigDecimal("55.00"))
                .codigoDestinatario(uPlanta.getRut()).nombreDestinatario("Planta YR4")
                .usuarioDestinatario(uPlanta).build());

        drSinTexto = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-YR4-4").fechaExtraccion(fecha).fechaDeclaracion(fecha).hora("10:30:00")
                .nombre("Recolector YR4").codigoSernapesca("RPA-YR4-4")
                .caleta(calCoquimbo).comuna(comCoquimbo).varadero(null)
                .especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(new BigDecimal("40.00")).captura(new BigDecimal("42.00"))
                .codigoDestinatario(uPlanta.getRut()).nombreDestinatario("Planta YR4")
                .usuarioDestinatario(uPlanta).build());

        referenciaOrigenService.invalidarCache();
        limpiarCacheDetalle();
    }

    @AfterEach
    void limpiarDatos() {
        abastRepo.deleteAll();
        recolectorRepo.deleteAll();
        marcaRepo.deleteAll();
        caletaRepo.deleteAll();
        varaderoRepo.deleteAll();
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + VincularVaraderoRecolectorService.TABLA_RESPALDO);
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + VincularVaraderoRecolectorService.TABLA_LOG);
        limpiarCacheDetalle();
    }

    private void limpiarCacheDetalle() {
        Cache c = cacheManager.getCache(CacheConfig.CACHE_DETALLE);
        if (c != null) c.clear();
    }

    @Test
    @DisplayName("YR.4: Simulación sin escritura, aplicación de calces únicos con respaldo, ambigüedad nula, idempotencia y cero marcas")
    void vincularVaradero_simulacionAplicacionIdempotenciaYSinMarcas() throws Exception {
        // 0) Seguridad: USER recibe 403
        mockMvc.perform(post("/api/admin/declaraciones/recolector/vincular-varadero")
                        .param("aplicar", "false")
                        .with(user("recolector").roles("USER")))
                .andExpect(status().isForbidden());

        // 1) Simulación: totales exactos y cero escrituras
        mockMvc.perform(post("/api/admin/declaraciones/recolector/vincular-varadero")
                        .param("aplicar", "false")
                        .with(user("admin_yr4").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.candidatasEvaluadas").value(3))
                .andExpect(jsonPath("$.calceUnico").value(1))
                .andExpect(jsonPath("$.ambiguo").value(1))
                .andExpect(jsonPath("$.sinCalce").value(1))
                .andExpect(jsonPath("$.actualizadas").value(0))
                .andExpect(jsonPath("$.ambiguos[0].texto").value("LA LOBERA"))
                .andExpect(jsonPath("$.ambiguos[0].candidatos").value(2))
                .andExpect(jsonPath("$.ambiguos[0].frecuencia").value(1))
                .andExpect(jsonPath("$.sinCalceDetalle[0].texto").value("VARADERO INEXISTENTE"))
                .andExpect(jsonPath("$.sinCalceDetalle[0].frecuencia").value(1));

        assertNull(jdbcTemplate.queryForObject(
                "SELECT varadero_id FROM declaracion_recolector WHERE id = ?", Long.class, drUnico.getId()));

        // 2) Aplicar: liga sólo drUnico, deja drAmbiguo y drSinCalce en NULL, respalda, registra log y no crea marcas
        mockMvc.perform(post("/api/admin/declaraciones/recolector/vincular-varadero")
                        .param("aplicar", "true")
                        .with(user("admin_yr4").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.actualizadas").value(1))
                .andExpect(jsonPath("$.lotesProcesados").value(1));

        assertEquals(varLengua.getId(), jdbcTemplate.queryForObject(
                "SELECT varadero_id FROM declaracion_recolector WHERE id = ?", Long.class, drUnico.getId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT varadero_id FROM declaracion_recolector WHERE id = ?", Long.class, drAmbiguo.getId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT varadero_id FROM declaracion_recolector WHERE id = ?", Long.class, drSinCalce.getId()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT varadero_id FROM declaracion_recolector WHERE id = ?", Long.class, drSinTexto.getId()));

        Integer respaldoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + VincularVaraderoRecolectorService.TABLA_RESPALDO, Integer.class);
        Integer logCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + VincularVaraderoRecolectorService.TABLA_LOG + " WHERE actualizadas = 1", Integer.class);
        assertEquals(1, respaldoCount);
        assertEquals(1, logCount);
        assertEquals(0L, marcaRepo.count(), "YR.2 no debe reevaluar ni crear marcas");

        // 3) Segunda corrida: idempotente, no cambia nada
        mockMvc.perform(post("/api/admin/declaraciones/recolector/vincular-varadero")
                        .param("aplicar", "true")
                        .with(user("admin_yr4").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidatasEvaluadas").value(2))
                .andExpect(jsonPath("$.calceUnico").value(0))
                .andExpect(jsonPath("$.actualizadas").value(0));

        assertEquals(0L, marcaRepo.count());
    }

    @Test
    @DisplayName("YR.3 / YR.4: Recolector con varadero ligado dibuja origen en el varadero con fuente VARADERO y REF_ORI=VARADERO")
    void flujoYFicha_recolectorConVaraderoLigadoUsaVaraderoYRefOri() throws Exception {
        // Aplicar vinculación primero para ligar drUnico -> varLengua
        mockMvc.perform(post("/api/admin/declaraciones/recolector/vincular-varadero")
                        .param("aplicar", "true")
                        .with(user("admin_yr4").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizadas").value(1));

        // Conectar drUnico a una planta de abastecimiento con GPS válido
        DeclaracionPlantaAbastecimientoModel dpa = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPlanta).folioDeclaracionAPla("APLA-YR4-1").fechaIngresoPlanta(Date.valueOf("2026-10-16")).hora("14:00:00")
                .nombrePlanta("Planta YR4").codigoSernapesca("9904").latitud(-29.95).longitud(-71.33)
                .especie(especie).humedadEstado(humedad)
                .cantidad(new BigDecimal("120.00")).pesoRomanaKg(new BigDecimal("118.00"))
                .declaracionesSeleccionadas("RECOLECTOR:" + drUnico.getId()).build());
        jdbcTemplate.update(
                "UPDATE declaracion_recolector SET declaracion_destinatario_id = ?, consumida_por_tipo = 'PLANTA_ABASTECIMIENTO' WHERE id = ?",
                dpa.getId(), drUnico.getId());
        limpiarCacheDetalle();

        // 1) Endpoint de flujos: origen en varadero (-30.237974, -71.627042) y fuente VARADERO (no la caleta -29.96)
        MvcResult resFlujos = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(resFlujos.getResponse().getContentAsString());
        assertEquals(1, root.path("flujosMapa").size());
        JsonNode origen = root.path("flujosMapa").get(0).path("origen");
        assertEquals("VARADERO", origen.path("tipo").asText());
        assertEquals("VARADERO", origen.path("fuenteReferencia").asText());
        assertEquals("LENGUA DE VACA", origen.path("nombre").asText());
        assertEquals(-30.237974, origen.path("latitud").asDouble(), 1e-6);
        assertEquals(-71.627042, origen.path("longitud").asDouble(), 1e-6);

        JsonNode decl = root.path("declaraciones").get(0);
        assertEquals("VARADERO", decl.path("origenReferenciaFuente").asText());

        // 2) Exportación Excel (ZE.1 / YR.3): columna REF_ORI <= 10 chars con valor VARADERO
        MvcResult resXlsx = mockMvc.perform(get("/api/reportes/flujos-origen-destino/exportar")
                        .param("formato", "xlsx")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resXlsx.getResponse().getContentAsByteArray()))) {
            Sheet sh = wb.getSheet("DECLARACIONES");
            Row hdr = sh.getRow(0);
            int idxRefOri = -1;
            for (int c = 0; c < hdr.getLastCellNum(); c++) {
                String h = hdr.getCell(c).getStringCellValue();
                assertTrue(h.length() <= 10, "Encabezado > 10 chars: " + h);
                if ("REF_ORI".equals(h)) idxRefOri = c;
            }
            assertTrue(idxRefOri >= 0, "Debe existir la columna REF_ORI en DECLARACIONES");
            assertEquals("VARADERO", sh.getRow(1).getCell(idxRefOri).getStringCellValue());
        }

        // 3) Exportación CSV (ZE.1 / YR.3): incluye REF_ORI y VARADERO
        MvcResult resCsv = mockMvc.perform(get("/api/reportes/flujos-origen-destino/exportar")
                        .param("formato", "csv")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        String csv = new String(resCsv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains(";\"REF_ORI\"\r\n"), "CSV debe incluir columna REF_ORI");
        assertTrue(csv.contains(";\"VARADERO\"\r\n"), "CSV debe reportar VARADERO en REF_ORI");

        // 4) Ficha de trazabilidad: referencia de origen es el varadero ligado
        MvcResult resFicha = mockMvc.perform(get("/api/consultas/ficha/RECOLECTOR/" + drUnico.getId())
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ficha = objectMapper.readTree(resFicha.getResponse().getContentAsString());
        assertTrue(ficha.path("origenes").isArray() && ficha.path("origenes").size() >= 1);
        JsonNode refFicha = ficha.path("origenes").get(0).path("geo").path("referencia");
        assertEquals("VARADERO", refFicha.path("tipo").asText());
        assertEquals("LENGUA DE VACA", refFicha.path("nombre").asText());
        assertEquals(-30.237974, refFicha.path("lat").asDouble(), 1e-6);
        assertEquals(-71.627042, refFicha.path("lon").asDouble(), 1e-6);
    }
}
