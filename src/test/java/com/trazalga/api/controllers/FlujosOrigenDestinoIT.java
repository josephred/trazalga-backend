package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.config.CacheConfig;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PlantaModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICoordenadaCambioLogRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IPlantaRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.IVaraderoRepository;
import com.trazalga.api.dto.geo.EstadoFilaCoordenada;
import com.trazalga.api.dto.geo.ResumenImportacionDto;
import com.trazalga.api.services.ReportService;
import com.trazalga.api.services.geo.ImportarCoordenadasService;
import com.trazalga.api.services.geo.ReferenciaOrigenService;
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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FlujosOrigenDestinoIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CacheManager cacheManager;
    @Autowired private ReferenciaOrigenService referenciaOrigenService;

    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private IVaraderoRepository varaderoRepo;
    @Autowired private IPlantaRepository plantaRepo;
    @Autowired private ICoordenadaCambioLogRepository cambioLogRepo;
    @Autowired private ImportarCoordenadasService importarCoordenadasService;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepo;
    @Autowired private IEmbarcacionRepository embarcacionRepo;
    @Autowired private IBuzoRepository buzoRepo;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepo;
    @Autowired private IDeclaracionArmadorRepository armadorRepo;
    @Autowired private IDeclaracionComercializadorRepository comercializadorRepo;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastRepo;

    @BeforeEach
    void limpiarYCargarEsquema() {
        jdbc.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        limpiarCacheDetalle();
    }

    @AfterEach
    void limpiarDespues() {
        abastRepo.deleteAll();
        comercializadorRepo.deleteAll();
        recolectorRepo.deleteAll();
        armadorRepo.deleteAll();
        cambioLogRepo.deleteAll();
        caletaRepo.deleteAll();
        varaderoRepo.deleteAll();
        plantaRepo.deleteAll();
        referenciaOrigenService.invalidarCache();
        limpiarCacheDetalle();
    }

    private void limpiarCacheDetalle() {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_DETALLE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("QM.4: Seguridad de GET /api/reportes/flujos-origen-destino (401 sin sesion, 403 USER, 200 roles consola)")
    void seguridadEndpointFlujos() throws Exception {
        mockMvc.perform(get("/api/reportes/flujos-origen-destino"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reportes/flujos-origen-destino").with(user("pescador").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reportes/flujos-origen-destino").with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reportes/flujos-origen-destino").with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reportes/flujos-origen-destino").with(user("adm").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("QM.4: 2 origenes con coordenadas -> 1 planta y 1 flujo sin coordenadas, filtros por region y especie")
    void flujosOrigenDestinoConCoordsYSinCoordsYFiltros() throws Exception {
        RegionModel regCoq = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo QM4").codigo("04").build());
        RegionModel regAta = regionRepo.saveAndFlush(RegionModel.builder().nombre("Atacama QM4").codigo("03").build());

        ProvinciaModel provCoq = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui QM4").region(regCoq).build());
        ProvinciaModel provAta = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Copiapo QM4").region(regAta).build());

        ComunaModel comCoq = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Coquimbo Com QM4").region(regCoq).provincia(provCoq).build());
        ComunaModel comAta = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Caldera Com QM4").region(regAta).provincia(provAta).build());

        CaletaModel calConCoords = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Chungungo QM4").region(regCoq).comuna(comCoq)
                .latitud(-29.451132).longitud(-71.314621).fuenteCoordenada("SERNAPESCA_2026_10").build());
        CaletaModel calSinCoordsAtacama = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Caldera GPS QM4").region(regAta).comuna(comAta).build());
        CaletaModel calSinCoordsCoq = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Sin Ubicacion QM4").region(regCoq).comuna(comCoq).build());

        EspecieModel espNegro = especieRepo.saveAndFlush(EspecieModel.builder().nombre("Huiro Negro QM4").activo(true).build());
        EspecieModel espPalo = especieRepo.saveAndFlush(EspecieModel.builder().nombre("Huiro Palo QM4").activo(true).build());
        HumedadEstadoModel hum = humedadRepo.saveAndFlush(HumedadEstadoModel.builder().nombre("Humedo QM4").rangoInicio(60).rangoFin(90).build());
        ExtraccionTipoModel metodo = extraccionTipoRepo.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado QM4").build());
        EmbarcacionModel emb = embarcacionRepo.saveAndFlush(EmbarcacionModel.builder().nombre("Bote QM4").codigo("QM4-EMB").build());
        BuzoModel buzo = buzoRepo.saveAndFlush(BuzoModel.builder().nombre("Buzo QM4").codigo("QM4-BUZ").build());

        UsuarioModel uRec = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("61.111.111-1").nombres("Recolector").apellidop("QM4").comuna(comCoq).estado("ACTIVO").build());
        UsuarioModel uArm = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("62.222.222-2").nombres("Armador").apellidop("QM4").comuna(comAta).estado("ACTIVO").build());
        UsuarioModel uCom = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("63.333.333-3").nombres("Comercializador").apellidop("QM4").comuna(comCoq).estado("ACTIVO").build());
        UsuarioModel uPlanta = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("64.444.444-4").nombres("Planta").apellidop("Oficial QM4").comuna(comCoq).estado("ACTIVO").build());

        plantaRepo.saveAndFlush(PlantaModel.builder()
                .id(7701L)
                .codigo(7701)
                .rut("64.444.444-4")
                .nombre("Planta Proceso Coquimbo Oficial")
                .latitud(-29.9533)
                .longitud(-71.3395)
                .fuenteCoordenada("MAESTRO_PLANTA")
                .build());
        referenciaOrigenService.invalidarCache();

        Date fechaOrig = Date.valueOf("2026-10-08");
        Date fechaRec = Date.valueOf("2026-10-10");

        // Origen 1 (Coquimbo): Referencia oficial en Caleta + via Comercializador -> Planta 7701 (con coords oficiales)
        DeclaracionRecolectorModel orig1 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-QM4-1").fechaExtraccion(fechaOrig).fechaDeclaracion(fechaOrig).hora("08:00:00")
                .nombre("Recolector 1").codigoSernapesca("RPA-QM4-1").caleta(calConCoords).comuna(comCoq)
                .especie(espNegro).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("500.00")).captura(new BigDecimal("540.00"))
                .codigoDestinatario(uCom.getRut()).nombreDestinatario("Comercializador QM4")
                .usuarioDestinatario(uCom).build());

        DeclaracionComercializadorModel com1 = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(uCom).folioOrigen("DC-QM4-1").fechaDeclaracion(fechaOrig).hora("10:00:00")
                .especie(espNegro).humedadEstado(hum).cantidad(new BigDecimal("500.00"))
                .codigoDestinatario(uPlanta.getRut()).nombreDestinatario("Planta Proceso Coquimbo")
                .usuarioDestinatario(uPlanta).declaracionesSeleccionadas("RECOLECTOR:" + orig1.getId()).build());

        // Origen 2 (Atacama): Sin ref oficial de caleta, pero con GPS declarado -> directo a Planta 7701 por FK
        DeclaracionArmadorModel orig2 = armadorRepo.saveAndFlush(DeclaracionArmadorModel.builder()
                .usuario(uArm).folioOrigen("DA-QM4-2").fechaExtraccion(fechaOrig).fechaDeclaracion(fechaOrig).hora("09:00:00")
                .embarcacion(emb).buzo(buzo).caleta(calSinCoordsAtacama).comuna(comAta)
                .especie(espPalo).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("300.00")).captura(330.0)
                .latitud(-27.0700).longitud(-70.8200)
                .tipoDestinatario("planta").codigoDestinatario(uPlanta.getRut())
                .usuarioDestinatario(uPlanta).build());

        // Origen 3 (Coquimbo): Flujo sin coordenadas (ni caleta con coords ni GPS) -> Planta 7701
        DeclaracionRecolectorModel orig3SinCoords = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-QM4-SIN").fechaExtraccion(fechaOrig).fechaDeclaracion(fechaOrig).hora("11:00:00")
                .nombre("Recolector Sin Coords").codigoSernapesca("RPA-QM4-3").caleta(calSinCoordsCoq).comuna(comCoq)
                .especie(espNegro).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("150.00")).captura(new BigDecimal("160.00"))
                .codigoDestinatario(uPlanta.getRut()).nombreDestinatario("Planta Proceso Coquimbo")
                .usuarioDestinatario(uPlanta).build());

        DeclaracionPlantaAbastecimientoModel dpaNegro = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("APLA-QM4-1").folioDeclaracionAPla("APLA-QM4-1")
                .fechaIngresoPlanta(fechaRec).fechaTraslado(fechaRec).hora("14:00:00")
                .nombrePlanta("Planta Proceso Coquimbo").codigoSernapesca("7701")
                .usuario(uPlanta).usuarioDestinatario(uPlanta)
                .especie(espNegro).humedadEstado(hum).humedadEstadoRecepcion(hum)
                .cantidad(new BigDecimal("650.00")).pesoRomanaKg(new BigDecimal("640.00"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + com1.getId() + ",RECOLECTOR:" + orig3SinCoords.getId())
                .build());

        DeclaracionPlantaAbastecimientoModel dpaPalo = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("APLA-QM4-2").folioDeclaracionAPla("APLA-QM4-2")
                .fechaIngresoPlanta(fechaRec).fechaTraslado(fechaRec).hora("15:00:00")
                .nombrePlanta("Planta Proceso Coquimbo").codigoSernapesca("7701")
                .usuario(uPlanta).usuarioDestinatario(uPlanta)
                .especie(espPalo).humedadEstado(hum).humedadEstadoRecepcion(hum)
                .cantidad(new BigDecimal("300.00")).pesoRomanaKg(new BigDecimal("295.00"))
                .build());

        orig2.setDeclaracionDestinatario(dpaPalo.getId());
        orig2.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        armadorRepo.saveAndFlush(orig2);

        // 1) Consulta global del periodo: 2 pares en mapa + 1 par sin coordenadas + 3 declaraciones
        MvcResult resAll = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonAll = objectMapper.readTree(resAll.getResponse().getContentAsString());
        assertEquals(3, jsonAll.path("resumen").path("totalPares").asInt());
        assertEquals(2, jsonAll.path("resumen").path("paresConCoordenadas").asInt());
        assertEquals(1, jsonAll.path("resumen").path("paresSinCoordenadas").asInt());
        assertEquals(3, jsonAll.path("resumen").path("totalDeclaraciones").asInt());
        assertEquals(950.0, jsonAll.path("resumen").path("desembarqueTotalKg").asDouble(), 0.01);
        assertEquals(2, jsonAll.path("flujosMapa").size());
        assertEquals(1, jsonAll.path("flujosSinCoordenadas").size());

        JsonNode sinCoords = jsonAll.path("flujosSinCoordenadas").get(0);
        assertFalse(sinCoords.path("motivoSinCoordenadas").asText().isBlank());
        assertEquals("SIN_COORDENADAS", sinCoords.path("origen").path("fuente").asText());

        boolean vioComercializadorFolio = false;
        boolean vioOrigenGps = false;
        for (JsonNode decl : jsonAll.path("declaraciones")) {
            if ("RO-QM4-1".equals(decl.path("origenFolio").asText())) {
                vioComercializadorFolio = "DC-QM4-1".equals(decl.path("comercializadorFolio").asText())
                        && "REFERENCIA".equals(decl.path("origenFuente").asText())
                        && "REFERENCIA".equals(decl.path("plantaFuente").asText())
                        && "APLA-QM4-1".equals(decl.path("recepcionFolio").asText());
            }
            if ("DA-QM4-2".equals(decl.path("origenFolio").asText())) {
                vioOrigenGps = "GPS".equals(decl.path("origenFuente").asText())
                        && Math.abs(decl.path("origenLatitud").asDouble() - (-27.0700)) < 1e-4;
            }
        }
        assertTrue(vioComercializadorFolio, "Debe incluir folio comercializador y fuentes REFERENCIA");
        assertTrue(vioOrigenGps, "Debe usar GPS declarado como respaldo en origen 2");

        // 2) Filtro por regionId (Atacama): debe aislar solo el par de Atacama (DA-QM4-2)
        MvcResult resAta = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .param("regionId", String.valueOf(regAta.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonAta = objectMapper.readTree(resAta.getResponse().getContentAsString());
        assertEquals(1, jsonAta.path("flujosMapa").size());
        assertEquals(0, jsonAta.path("flujosSinCoordenadas").size());
        assertEquals(1, jsonAta.path("declaraciones").size());
        assertEquals("DA-QM4-2", jsonAta.path("declaraciones").get(0).path("origenFolio").asText());

        // 3) Filtro por especieId (Huiro Negro): debe excluir DA-QM4-2 (Huiro Palo) y retornar 1 mapa + 1 sin coords
        MvcResult resNegro = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .param("especieId", String.valueOf(espNegro.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonNegro = objectMapper.readTree(resNegro.getResponse().getContentAsString());
        assertEquals(1, jsonNegro.path("flujosMapa").size());
        assertEquals(1, jsonNegro.path("flujosSinCoordenadas").size());
        assertEquals("Caleta Chungungo QM4", jsonNegro.path("flujosMapa").get(0).path("origen").path("nombre").asText());
        assertNotNull(dpaNegro.getId());
    }

    @Test
    @DisplayName("ZM.6: Capas de referencia (seguridad, vacio 0 de 0, filtro por region, sin RUT, invalida fuera de Chile)")
    void capasGeorreferenciaSeguridadCoberturaSinRutYMaestrosVacios() throws Exception {
        // 1) Seguridad del endpoint
        mockMvc.perform(get("/api/reportes/capas-georreferencia"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reportes/capas-georreferencia").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());

        // 2) Maestro vacio -> 0 de 0 sin error
        caletaRepo.deleteAll();
        varaderoRepo.deleteAll();
        plantaRepo.deleteAll();

        MvcResult resVacio = mockMvc.perform(get("/api/reportes/capas-georreferencia")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonVacio = objectMapper.readTree(resVacio.getResponse().getContentAsString());
        assertEquals(0, jsonVacio.path("caletas").size());
        assertEquals(0, jsonVacio.path("varaderos").size());
        assertEquals(0, jsonVacio.path("plantas").size());
        assertEquals(0, jsonVacio.path("cobertura").path("caletas").path("total").asInt());
        assertEquals(0, jsonVacio.path("cobertura").path("caletas").path("conCoordenadas").asInt());
        assertEquals(0, jsonVacio.path("cobertura").path("varaderos").path("total").asInt());
        assertEquals(0, jsonVacio.path("cobertura").path("plantas").path("total").asInt());

        // 3) Poblar dos regiones con coords validas e invalidas ((0,0), lat 95, invertidas)
        RegionModel regA = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo ZM6").codigo("04Z").build());
        RegionModel regB = regionRepo.saveAndFlush(RegionModel.builder().nombre("Biobio ZM6").codigo("08Z").build());
        ProvinciaModel provA = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui ZM6").region(regA).build());
        ProvinciaModel provB = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Concepcion ZM6").region(regB).build());
        ComunaModel comA = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("La Serena ZM6").region(regA).provincia(provA).build());
        ComunaModel comB = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Talcahuano ZM6").region(regB).provincia(provB).build());

        caletaRepo.saveAndFlush(CaletaModel.builder().nombre("Caleta Valida A").region(regA).comuna(comA)
                .latitud(-29.91).longitud(-71.26).fuenteCoordenada("MAESTRO").build());
        caletaRepo.saveAndFlush(CaletaModel.builder().nombre("Caleta Cero A").region(regA).comuna(comA)
                .latitud(0.0).longitud(0.0).fuenteCoordenada("INVALIDA").build());
        caletaRepo.saveAndFlush(CaletaModel.builder().nombre("Caleta Invertida B").region(regB).comuna(comB)
                .latitud(-73.11).longitud(-36.72).fuenteCoordenada("INVERTIDA").build());

        varaderoRepo.saveAndFlush(VaraderoModel.builder().nombre("Varadero Valido A").comuna(comA)
                .latitud(-29.93).longitud(-71.28).fuenteCoordenada("SERNAPESCA").build());
        varaderoRepo.saveAndFlush(VaraderoModel.builder().nombre("Varadero Lat95 B").comuna(comB)
                .latitud(95.0).longitud(-73.10).fuenteCoordenada("ERR").build());

        plantaRepo.saveAndFlush(PlantaModel.builder().id(8801L).codigo(8801).rut("76.111.222-3")
                .nombre("Planta Valida A").comuna(comA).tipo("PROCESO")
                .latitud(-29.95).longitud(-71.33).fuenteCoordenada("MAESTRO").build());
        plantaRepo.saveAndFlush(PlantaModel.builder().id(8802L).codigo(8802).rut("76.222.333-4")
                .nombre("Planta Sin Coords A").comuna(comA).tipo("SECADO").build());
        plantaRepo.saveAndFlush(PlantaModel.builder().id(8803L).codigo(8803).rut("76.333.444-5")
                .nombre("Planta Valida B").comuna(comB).tipo("PROCESO")
                .latitud(-36.72).longitud(-73.11).fuenteCoordenada("MAESTRO").build());
        plantaRepo.saveAndFlush(PlantaModel.builder().id(8804L).codigo(8804).rut("76.444.555-6")
                .nombre("Planta Sin Comuna").tipo("PROCESO")
                .latitud(-27.06).longitud(-70.82).fuenteCoordenada("MAESTRO").build());

        // Global: todas las plantas validas (2 de 4), pero sin RUT en la respuesta
        MvcResult resGlobal = mockMvc.perform(get("/api/reportes/capas-georreferencia")
                        .with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonGlobal = objectMapper.readTree(resGlobal.getResponse().getContentAsString());
        assertEquals(1, jsonGlobal.path("caletas").size());
        assertEquals(1, jsonGlobal.path("varaderos").size());
        assertEquals(3, jsonGlobal.path("plantas").size());
        assertEquals(3, jsonGlobal.path("cobertura").path("caletas").path("total").asInt());
        assertEquals(1, jsonGlobal.path("cobertura").path("caletas").path("conCoordenadas").asInt());
        assertEquals(4, jsonGlobal.path("cobertura").path("plantas").path("total").asInt());
        assertEquals(3, jsonGlobal.path("cobertura").path("plantas").path("conCoordenadas").asInt());
        for (JsonNode pl : jsonGlobal.path("plantas")) {
            assertFalse(pl.has("rut"), "Las capas publicadas no deben exponer RUT de planta");
        }

        // Filtrado por regionA: excluye Biobio y excluye Planta Sin Comuna
        MvcResult resRegA = mockMvc.perform(get("/api/reportes/capas-georreferencia")
                        .with(user("adm").roles("ADMIN"))
                        .param("regionId", String.valueOf(regA.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonRegA = objectMapper.readTree(resRegA.getResponse().getContentAsString());
        assertEquals(1, jsonRegA.path("caletas").size());
        assertEquals(1, jsonRegA.path("varaderos").size());
        assertEquals(1, jsonRegA.path("plantas").size());
        assertEquals("Planta Valida A", jsonRegA.path("plantas").get(0).path("nombre").asText());
        assertEquals(2, jsonRegA.path("cobertura").path("plantas").path("total").asInt());
        assertEquals(1, jsonRegA.path("cobertura").path("plantas").path("conCoordenadas").asInt());
    }

    @Test
    @DisplayName("ZM.6: Multi-comercializador cronologico, validacion coords Chile, filtro regionDestinoId, importador PLANTA y GET /planta LAZY")
    void flujosFiltroRegionDestinoYMultiComercializadorYPlantaController() throws Exception {
        // Verificar rechazo de coordenadas invalidas (nula, 0/0, lat 95, invertida)
        assertTrue(ReportService.esCoordenadaChileValida(-29.95, -71.33));
        assertFalse(ReportService.esCoordenadaChileValida(null, -71.33));
        assertFalse(ReportService.esCoordenadaChileValida(0.0, 0.0));
        assertFalse(ReportService.esCoordenadaChileValida(95.0, -71.33));
        assertFalse(ReportService.esCoordenadaChileValida(-71.33, -29.95));

        RegionModel regOri = regionRepo.saveAndFlush(RegionModel.builder().nombre("Atacama DestTest").codigo("03D").build());
        RegionModel regDest1 = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo DestTest").codigo("04D").build());
        RegionModel regDest2 = regionRepo.saveAndFlush(RegionModel.builder().nombre("Valparaiso DestTest").codigo("05D").build());

        ProvinciaModel pOri = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Huasco D").region(regOri).build());
        ProvinciaModel pDest1 = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui D").region(regDest1).build());
        ProvinciaModel pDest2 = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("San Antonio D").region(regDest2).build());

        ComunaModel comOri = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Huasco Com D").region(regOri).provincia(pOri).build());
        ComunaModel comDest1 = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Coquimbo Com D").region(regDest1).provincia(pDest1).build());
        ComunaModel comDest2 = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Cartagena Com D").region(regDest2).provincia(pDest2).build());

        CaletaModel calOri = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Carrizal Bajo D").region(regOri).comuna(comOri)
                .latitud(-28.0811).longitud(-71.1489).fuenteCoordenada("MAESTRO").build());

        EspecieModel esp = especieRepo.saveAndFlush(EspecieModel.builder().nombre("Lessonia ZM6").activo(true).build());
        HumedadEstadoModel hum = humedadRepo.saveAndFlush(HumedadEstadoModel.builder().nombre("Humedo ZM6").rangoInicio(50).rangoFin(80).build());
        ExtraccionTipoModel metodo = extraccionTipoRepo.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado ZM6").build());

        UsuarioModel uRec = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("71.111.111-1").nombres("Rec").apellidop("ZM6").comuna(comOri).estado("ACTIVO").build());
        UsuarioModel uCom1 = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("72.222.222-2").nombres("Com1").apellidop("ZM6").comuna(comOri).estado("ACTIVO").build());
        UsuarioModel uCom2 = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("73.333.333-3").nombres("Com2").apellidop("ZM6").comuna(comDest1).estado("ACTIVO").build());
        UsuarioModel uCom3 = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("74.444.444-4").nombres("Com3").apellidop("ZM6").comuna(comDest1).estado("ACTIVO").build());
        UsuarioModel uPla1 = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("75.555.555-5").nombres("Planta1").apellidop("ZM6").comuna(comDest1).estado("ACTIVO").build());
        UsuarioModel uPla2 = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("76.666.666-6").nombres("Planta2").apellidop("ZM6").comuna(comDest2).estado("ACTIVO").build());
        UsuarioModel uPlaSinCom = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("77.777.777-7").nombres("PlantaSinCom").apellidop("ZM6").estado("ACTIVO").build());

        PlantaModel planta1 = plantaRepo.saveAndFlush(PlantaModel.builder()
                .id(9101L).codigo(9101).rut("75.555.555-5").nombre("Planta Destino 1").comuna(comDest1).tipo("PROCESO")
                .latitud(-29.9610).longitud(-71.3410).fuenteCoordenada("MAESTRO").build());
        plantaRepo.saveAndFlush(PlantaModel.builder()
                .id(9102L).codigo(9102).rut("76.666.666-6").nombre("Planta Destino 2").comuna(comDest2).tipo("PROCESO")
                .latitud(-33.5500).longitud(-71.6000).fuenteCoordenada("MAESTRO").build());
        PlantaModel plantaSinCom = plantaRepo.saveAndFlush(PlantaModel.builder()
                .id(9103L).codigo(9103).rut("77.777.777-7").nombre("Planta Pendiente Comuna").tipo("SECADO").build());

        Date f1 = Date.valueOf("2026-09-10");
        Date f2 = Date.valueOf("2026-09-11");
        Date f3 = Date.valueOf("2026-09-12");

        // Cadena 1: orig1 -> com1 (GPS valido) -> com2 (sin GPS) -> com3 (GPS invalido lat 95) -> dpa1 (Planta Destino 1)
        DeclaracionRecolectorModel orig1 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-ZM6-1").fechaExtraccion(f1).fechaDeclaracion(f1).hora("08:00:00")
                .nombre("Rec ZM6-1").codigoSernapesca("RPA-ZM6-1").caleta(calOri).comuna(comOri)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("400.00")).captura(new BigDecimal("420.00"))
                .codigoDestinatario(uCom1.getRut()).nombreDestinatario("Com1 ZM6")
                .usuarioDestinatario(uCom1).build());

        DeclaracionComercializadorModel com1 = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(uCom1).folioOrigen("DC-ZM6-1").fechaDeclaracion(f1).hora("10:00:00")
                .nombreComercializador("Com1 ZM6").codigoSernapesca("COM-1")
                .latitud(-28.6000).longitud(-71.2000)
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("400.00"))
                .codigoDestinatario(uCom2.getRut()).nombreDestinatario("Com2 ZM6")
                .usuarioDestinatario(uCom2)
                .declaracionesSeleccionadas("RECOLECTOR:" + orig1.getId())
                .consumidaPorTipo("COMERCIALIZADOR").build());
        orig1.setDeclaracionDestinatario(com1.getId());
        orig1.setConsumidaPorTipo("COMERCIALIZADOR");
        recolectorRepo.saveAndFlush(orig1);

        DeclaracionComercializadorModel com2 = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(uCom2).folioOrigen("DC-ZM6-2").fechaDeclaracion(f2).hora("11:00:00")
                .nombreComercializador("Com2 ZM6").codigoSernapesca("COM-2")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("390.00"))
                .codigoDestinatario(uCom3.getRut()).nombreDestinatario("Com3 ZM6")
                .usuarioDestinatario(uCom3)
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + com1.getId())
                .consumidaPorTipo("COMERCIALIZADOR").build());
        com1.setDeclaracionDestinatario(com2.getId());
        comercializadorRepo.saveAndFlush(com1);

        DeclaracionComercializadorModel com3 = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(uCom3).folioOrigen("DC-ZM6-3").fechaDeclaracion(f2).hora("16:00:00")
                .nombreComercializador("Com3 ZM6").codigoSernapesca("COM-3")
                .latitud(95.0).longitud(-71.3000)
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("390.00"))
                .codigoDestinatario(uPla1.getRut()).nombreDestinatario("Planta Destino 1")
                .usuarioDestinatario(uPla1)
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + com2.getId())
                .consumidaPorTipo("PLANTA_ABASTECIMIENTO").build());
        com2.setDeclaracionDestinatario(com3.getId());
        comercializadorRepo.saveAndFlush(com2);

        DeclaracionPlantaAbastecimientoModel dpa1 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPla1).folioDeclaracionAPla("APLA-ZM6-1").fechaIngresoPlanta(f3).hora("09:00:00")
                .nombrePlanta("Planta Destino 1").codigoSernapesca("9101")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("385.00")).pesoRomanaKg(new BigDecimal("385.00"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + com3.getId()).build());
        com3.setDeclaracionDestinatario(dpa1.getId());
        comercializadorRepo.saveAndFlush(com3);

        // Cadena 2 directa -> Planta Destino 2 (Valparaiso)
        DeclaracionRecolectorModel orig2 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-ZM6-2").fechaExtraccion(f1).fechaDeclaracion(f1).hora("12:00:00")
                .nombre("Rec ZM6-2").codigoSernapesca("RPA-ZM6-2").caleta(calOri).comuna(comOri)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("200.00")).captura(new BigDecimal("210.00"))
                .codigoDestinatario(uPla2.getRut()).nombreDestinatario("Planta Destino 2")
                .usuarioDestinatario(uPla2).build());
        DeclaracionPlantaAbastecimientoModel dpa2 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPla2).folioDeclaracionAPla("APLA-ZM6-2").fechaIngresoPlanta(f3).hora("10:00:00")
                .nombrePlanta("Planta Destino 2").codigoSernapesca("9102")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("198.00")).pesoRomanaKg(new BigDecimal("198.00"))
                .declaracionesSeleccionadas("RECOLECTOR:" + orig2.getId()).build());
        orig2.setDeclaracionDestinatario(dpa2.getId());
        orig2.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        recolectorRepo.saveAndFlush(orig2);

        // Cadena 3 directa -> Planta Pendiente Comuna (sin comuna inicial)
        DeclaracionRecolectorModel orig3 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-ZM6-3").fechaExtraccion(f1).fechaDeclaracion(f1).hora("13:00:00")
                .nombre("Rec ZM6-3").codigoSernapesca("RPA-ZM6-3").caleta(calOri).comuna(comOri)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("150.00")).captura(new BigDecimal("160.00"))
                .codigoDestinatario(uPlaSinCom.getRut()).nombreDestinatario("Planta Pendiente Comuna")
                .usuarioDestinatario(uPlaSinCom).build());
        DeclaracionPlantaAbastecimientoModel dpa3 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPlaSinCom).folioDeclaracionAPla("APLA-ZM6-3").fechaIngresoPlanta(f3).hora("11:00:00")
                .nombrePlanta("Planta Pendiente Comuna").codigoSernapesca("9103")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("148.00")).pesoRomanaKg(new BigDecimal("148.00"))
                .declaracionesSeleccionadas("RECOLECTOR:" + orig3.getId()).build());
        orig3.setDeclaracionDestinatario(dpa3.getId());
        orig3.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        recolectorRepo.saveAndFlush(orig3);
        referenciaOrigenService.invalidarCache();

        // A) Filtro por regionDestinoId = regDest1 -> solo flujo a Planta Destino 1 y 3 hops cronologicos
        MvcResult resDest1 = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30")
                        .param("regionDestinoId", String.valueOf(regDest1.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonDest1 = objectMapper.readTree(resDest1.getResponse().getContentAsString());
        assertEquals(1, jsonDest1.path("declaraciones").size());
        assertEquals(1, jsonDest1.path("flujosMapa").size());

        JsonNode decl1 = jsonDest1.path("declaraciones").get(0);
        assertEquals("RO-ZM6-1", decl1.path("origenFolio").asText());
        assertEquals("Coquimbo Com D", decl1.path("plantaComuna").asText());
        assertEquals(regDest1.getId().longValue(), decl1.path("plantaRegionId").asLong());
        assertEquals(3, decl1.path("comercializadores").size());
        assertEquals("DC-ZM6-1", decl1.path("comercializadores").get(0).path("folio").asText());
        assertEquals("GPS_DECLARACION", decl1.path("comercializadores").get(0).path("fuente").asText());
        assertEquals("DC-ZM6-2", decl1.path("comercializadores").get(1).path("folio").asText());
        assertEquals("SIN_COORDENADAS", decl1.path("comercializadores").get(1).path("fuente").asText());
        assertEquals("DC-ZM6-3", decl1.path("comercializadores").get(2).path("folio").asText());
        assertEquals("SIN_COORDENADAS", decl1.path("comercializadores").get(2).path("fuente").asText());
        assertTrue(decl1.path("comercializadores").get(2).path("latitud").isNull(), "Latitud 95 debe descartarse");

        JsonNode flujo1 = jsonDest1.path("flujosMapa").get(0);
        assertTrue(flujo1.path("algunComercializadorSinCoordenadas").asBoolean());
        assertEquals(3, flujo1.path("comercializadores").size());

        // B) ImportarCoordenadasService asigna comuna y coordenadas a Planta Pendiente Comuna (ZM.4)
        String csvPlanta = "tipo;codigo;nombre;comuna;region;latitud;longitud;datum;fuente\n"
                + "PLANTA;9103;Planta Pendiente Comuna;Coquimbo Com D;Coquimbo DestTest;-29.9700;-71.3500;WGS84;PLANILLA_PLANTA\n";
        ResumenImportacionDto impRes = importarCoordenadasService.procesarPlanilla(
                new ByteArrayInputStream(csvPlanta.getBytes(StandardCharsets.UTF_8)), true, false, "zm6-it");
        assertEquals(1, impRes.getCalzadas());
        assertEquals(1, impRes.getAplicadas());
        assertEquals(EstadoFilaCoordenada.CALZADA, impRes.getFilas().get(0).getEstado());

        PlantaModel actualizada = plantaRepo.findById(plantaSinCom.getId()).orElseThrow();
        assertNotNull(actualizada.getComuna());
        assertEquals(comDest1.getId(), actualizada.getComuna().getId());
        assertEquals(-29.9700, actualizada.getLatitud(), 1e-4);

        // C) GET /planta bajo open-in-view=false no lanza LazyInitializationException y serializa comuna
        MvcResult resPlantas = mockMvc.perform(get("/planta").with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonPlantas = objectMapper.readTree(resPlantas.getResponse().getContentAsString());
        assertTrue(jsonPlantas.isArray() && jsonPlantas.size() >= 2);
        boolean encontroPlanta1ConComuna = false;
        for (JsonNode p : jsonPlantas) {
            if (planta1.getId().equals(p.path("id").asLong())) {
                encontroPlanta1ConComuna = "Coquimbo Com D".equals(p.path("comuna").path("nombre").asText());
            }
        }
        assertTrue(encontroPlanta1ConComuna, "GET /planta debe serializar comuna sin LazyInitializationException");

        // Reconsultar regionDestinoId = regDest1 ahora incluye Planta Destino 1 y Planta Pendiente Comuna
        referenciaOrigenService.invalidarCache();
        MvcResult resDest1PostImp = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30")
                        .param("regionDestinoId", String.valueOf(regDest1.getId())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonDest1Post = objectMapper.readTree(resDest1PostImp.getResponse().getContentAsString());
        assertEquals(2, jsonDest1Post.path("declaraciones").size());
    }

    @Test
    @DisplayName("ZE.4: Exportacion Excel SIG (4 hojas, campos <=10 chars, celdas numericas, seguridad 403) y ejemplo")
    void exportarFlujosExcelSigYSeguridad() throws Exception {
        mockMvc.perform(get("/api/reportes/flujos-origen-destino/exportar"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reportes/flujos-origen-destino/exportar").with(user("pescador").roles("USER")))
                .andExpect(status().isForbidden());

        RegionModel regCoq = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo ZE4").codigo("04E").build());
        ProvinciaModel provCoq = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui ZE4").region(regCoq).build());
        ComunaModel comCoq = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("Coquimbo Com ZE4").region(regCoq).provincia(provCoq).build());

        CaletaModel calConCoords = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Chungungo ZE4").region(regCoq).comuna(comCoq)
                .latitud(-29.4511).longitud(-71.3146).fuenteCoordenada("MAESTRO").build());
        CaletaModel calSinCoords = caletaRepo.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Sin Coord ZE4").region(regCoq).comuna(comCoq).build());

        EspecieModel esp = especieRepo.saveAndFlush(EspecieModel.builder().nombre("Huiro Negro ZE4").activo(true).build());
        HumedadEstadoModel hum = humedadRepo.saveAndFlush(HumedadEstadoModel.builder().nombre("Humedo ZE4").rangoInicio(60).rangoFin(85).build());
        ExtraccionTipoModel metodo = extraccionTipoRepo.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado ZE4").build());

        UsuarioModel uRec = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("81.111.111-1").nombres("Rec").apellidop("ZE4").comuna(comCoq).estado("ACTIVO").build());
        UsuarioModel uCom = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("82.222.222-2").nombres("Com").apellidop("ZE4").comuna(comCoq).estado("ACTIVO").build());
        UsuarioModel uPla = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut("83.333.333-3").nombres("Planta").apellidop("ZE4").comuna(comCoq).estado("ACTIVO").build());

        plantaRepo.saveAndFlush(PlantaModel.builder()
                .id(9201L).codigo(9201).rut("83.333.333-3").nombre("Planta Proceso Coquimbo ZE4")
                .comuna(comCoq).tipo("PROCESO").latitud(-29.9533).longitud(-71.3395)
                .fuenteCoordenada("MAESTRO").build());

        Date fOrig = Date.valueOf("2026-08-10");
        Date fRec = Date.valueOf("2026-08-11");

        // Caso 1: Origen con coords -> Comercializador con GPS -> Planta (genera ORI-COM y COM-PLA)
        DeclaracionRecolectorModel ori1 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-ZE4-1").fechaExtraccion(fOrig).fechaDeclaracion(fOrig).hora("08:30:00")
                .nombre("Rec 1 ZE4").codigoSernapesca("RPA-ZE4-1").caleta(calConCoords).comuna(comCoq)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("600.00")).captura(new BigDecimal("650.00"))
                .codigoDestinatario(uCom.getRut()).nombreDestinatario("Com ZE4")
                .usuarioDestinatario(uCom).build());
        DeclaracionComercializadorModel com1 = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(uCom).folioOrigen("DC-ZE4-1").fechaDeclaracion(fOrig).hora("11:00:00")
                .nombreComercializador("Com ZE4").codigoSernapesca("COM-ZE4-1")
                .latitud(-29.6500).longitud(-71.2900)
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("590.00"))
                .codigoDestinatario(uPla.getRut()).nombreDestinatario("Planta Proceso Coquimbo ZE4")
                .usuarioDestinatario(uPla)
                .declaracionesSeleccionadas("RECOLECTOR:" + ori1.getId())
                .consumidaPorTipo("PLANTA_ABASTECIMIENTO").build());
        ori1.setDeclaracionDestinatario(com1.getId());
        ori1.setConsumidaPorTipo("COMERCIALIZADOR");
        recolectorRepo.saveAndFlush(ori1);
        DeclaracionPlantaAbastecimientoModel dpa1 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPla).folioDeclaracionAPla("APLA-ZE4-1").fechaIngresoPlanta(fRec).hora("09:00:00")
                .nombrePlanta("Planta Proceso Coquimbo ZE4").codigoSernapesca("9201")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("585.00")).pesoRomanaKg(new BigDecimal("585.00"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + com1.getId()).build());
        com1.setDeclaracionDestinatario(dpa1.getId());
        comercializadorRepo.saveAndFlush(com1);

        // Caso 2: Origen con coords -> Comercializador SIN GPS -> Planta (genera ORI-PLA directo + entrada en SIN_COORD)
        DeclaracionRecolectorModel ori2 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-ZE4-2").fechaExtraccion(fOrig).fechaDeclaracion(fOrig).hora("09:15:00")
                .nombre("Rec 2 ZE4").codigoSernapesca("RPA-ZE4-2").caleta(calConCoords).comuna(comCoq)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("350.00")).captura(new BigDecimal("360.00"))
                .codigoDestinatario(uCom.getRut()).nombreDestinatario("Com ZE4")
                .usuarioDestinatario(uCom).build());
        DeclaracionComercializadorModel comSinGps = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(uCom).folioOrigen("DC-ZE4-SINGPS").fechaDeclaracion(fOrig).hora("13:00:00")
                .nombreComercializador("Com ZE4").codigoSernapesca("COM-ZE4-2")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("345.00"))
                .codigoDestinatario(uPla.getRut()).nombreDestinatario("Planta Proceso Coquimbo ZE4")
                .usuarioDestinatario(uPla)
                .declaracionesSeleccionadas("RECOLECTOR:" + ori2.getId())
                .consumidaPorTipo("PLANTA_ABASTECIMIENTO").build());
        ori2.setDeclaracionDestinatario(comSinGps.getId());
        ori2.setConsumidaPorTipo("COMERCIALIZADOR");
        recolectorRepo.saveAndFlush(ori2);
        DeclaracionPlantaAbastecimientoModel dpa2 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPla).folioDeclaracionAPla("APLA-ZE4-2").fechaIngresoPlanta(fRec).hora("10:00:00")
                .nombrePlanta("Planta Proceso Coquimbo ZE4").codigoSernapesca("9201")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("340.00")).pesoRomanaKg(new BigDecimal("340.00"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + comSinGps.getId()).build());
        comSinGps.setDeclaracionDestinatario(dpa2.getId());
        comercializadorRepo.saveAndFlush(comSinGps);

        // Caso 3: Origen SIN coords ni GPS -> Planta (sin tramos, listado en DECLARACIONES y en SIN_COORD)
        DeclaracionRecolectorModel ori3 = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(uRec).folioOrigen("RO-ZE4-3").fechaExtraccion(fOrig).fechaDeclaracion(fOrig).hora("10:45:00")
                .nombre("Rec 3 ZE4").codigoSernapesca("RPA-ZE4-3").caleta(calSinCoords).comuna(comCoq)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("220.00")).captura(new BigDecimal("230.00"))
                .codigoDestinatario(uPla.getRut()).nombreDestinatario("Planta Proceso Coquimbo ZE4")
                .usuarioDestinatario(uPla).build());
        DeclaracionPlantaAbastecimientoModel dpa3 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(uPla).folioDeclaracionAPla("APLA-ZE4-3").fechaIngresoPlanta(fRec).hora("11:30:00")
                .nombrePlanta("Planta Proceso Coquimbo ZE4").codigoSernapesca("9201")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("218.00")).pesoRomanaKg(new BigDecimal("218.00"))
                .declaracionesSeleccionadas("RECOLECTOR:" + ori3.getId()).build());
        ori3.setDeclaracionDestinatario(dpa3.getId());
        ori3.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        recolectorRepo.saveAndFlush(ori3);
        referenciaOrigenService.invalidarCache();

        // Comparar cantidad contra JSON del endpoint de flujos
        MvcResult resJson = mockMvc.perform(get("/api/reportes/flujos-origen-destino")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("regionDestinoId", String.valueOf(regCoq.getId())))
                .andExpect(status().isOk())
                .andReturn();
        int totalDeclEndpoint = objectMapper.readTree(resJson.getResponse().getContentAsString())
                .path("declaraciones").size();
        assertEquals(3, totalDeclEndpoint);

        // Descargar Excel (.xlsx)
        MvcResult resXlsx = mockMvc.perform(get("/api/reportes/flujos-origen-destino/exportar")
                        .with(user("fisc").roles("FISCALIZADOR"))
                        .param("formato", "xlsx")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31")
                        .param("regionDestinoId", String.valueOf(regCoq.getId())))
                .andExpect(status().isOk())
                .andReturn();
        byte[] xlsxBytes = resXlsx.getResponse().getContentAsByteArray();
        assertTrue(xlsxBytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertEquals("DECLARACIONES", wb.getSheetName(0));
            assertEquals("TRAMOS", wb.getSheetName(1));
            assertEquals("SIN_COORD", wb.getSheetName(2));
            assertEquals("LEEME", wb.getSheetName(3));

            for (String sheetName : new String[]{"DECLARACIONES", "TRAMOS", "SIN_COORD"}) {
                Sheet sh = wb.getSheet(sheetName);
                Row hdr = sh.getRow(0);
                assertNotNull(hdr);
                Set<String> seen = new HashSet<>();
                for (int c = 0; c < hdr.getLastCellNum(); c++) {
                    String name = hdr.getCell(c).getStringCellValue();
                    assertTrue(name.matches("^[A-Z0-9_]{1,10}$"),
                            "Encabezado SIG invalido en " + sheetName + ": " + name);
                    assertTrue(seen.add(name), "Encabezado duplicado en " + sheetName + ": " + name);
                }
            }

            Sheet shDecl = wb.getSheet("DECLARACIONES");
            assertEquals(totalDeclEndpoint, shDecl.getLastRowNum());
            boolean verificoOri1Numerico = false;
            boolean verificoOri3SinCoords = false;
            for (int r = 1; r <= shDecl.getLastRowNum(); r++) {
                Row row = shDecl.getRow(r);
                String folioOri = row.getCell(0).getStringCellValue();
                assertEquals(CellType.STRING, row.getCell(2).getCellType()); // FECHA_ORI
                assertTrue(row.getCell(2).getStringCellValue().matches("\\d{4}-\\d{2}-\\d{2}"));
                assertEquals(CellType.NUMERIC, row.getCell(4).getCellType()); // DESEMB_KG
                assertEquals(CellType.NUMERIC, row.getCell(22).getCellType()); // ROMANA_KG
                if ("RO-ZE4-1".equals(folioOri)) {
                    assertEquals(CellType.NUMERIC, row.getCell(7).getCellType()); // LAT_ORI
                    assertEquals(CellType.NUMERIC, row.getCell(8).getCellType()); // LON_ORI
                    assertEquals(CellType.NUMERIC, row.getCell(11).getCellType()); // LAT_COM
                    assertEquals(CellType.NUMERIC, row.getCell(12).getCellType()); // LON_COM
                    assertEquals(CellType.NUMERIC, row.getCell(17).getCellType()); // LAT_PLA
                    assertEquals(CellType.NUMERIC, row.getCell(18).getCellType()); // LON_PLA
                    verificoOri1Numerico = true;
                } else if ("RO-ZE4-3".equals(folioOri)) {
                    assertNull(row.getCell(7)); // LAT_ORI sin coordenadas -> celda vacia
                    assertNull(row.getCell(8)); // LON_ORI sin coordenadas -> celda vacia
                    verificoOri3SinCoords = true;
                }
            }
            assertTrue(verificoOri1Numerico, "Debe verificar celdas numericas de RO-ZE4-1");
            assertTrue(verificoOri3SinCoords, "Debe verificar ausencia de coordenadas en RO-ZE4-3");

            Sheet shTramos = wb.getSheet("TRAMOS");
            // 2 tramos para Caso 1 (ORI-COM + COM-PLA) + 1 tramo para Caso 2 (ORI-PLA) = 3 tramos
            assertEquals(3, shTramos.getLastRowNum());
            Set<String> tiposTramo = new HashSet<>();
            for (int r = 1; r <= shTramos.getLastRowNum(); r++) {
                Row row = shTramos.getRow(r);
                tiposTramo.add(row.getCell(3).getStringCellValue());
                assertEquals(CellType.NUMERIC, row.getCell(0).getCellType()); // ID_TRAMO
                assertEquals(CellType.NUMERIC, row.getCell(6).getCellType()); // X_INI
                assertEquals(CellType.NUMERIC, row.getCell(7).getCellType()); // Y_INI
                assertEquals(CellType.NUMERIC, row.getCell(8).getCellType()); // X_FIN
                assertEquals(CellType.NUMERIC, row.getCell(9).getCellType()); // Y_FIN
                assertEquals(CellType.NUMERIC, row.getCell(11).getCellType()); // KG
            }
            assertTrue(tiposTramo.contains("ORI-COM"));
            assertTrue(tiposTramo.contains("COM-PLA"));
            assertTrue(tiposTramo.contains("ORI-PLA"));

            Sheet shSin = wb.getSheet("SIN_COORD");
            assertEquals(2, shSin.getLastRowNum()); // comSinGps y ori3

            Sheet shLeeme = wb.getSheet("LEEME");
            boolean encontroEpsg = false;
            for (int r = 0; r <= shLeeme.getLastRowNum(); r++) {
                Row row = shLeeme.getRow(r);
                if (row != null) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains("EPSG:4326")) {
                            encontroEpsg = true;
                        }
                    }
                }
            }
            assertTrue(encontroEpsg, "LEEME debe documentar EPSG:4326");
        }

        // Guardar archivo de ejemplo para validacion externa en ArcGIS (ZE.2 / ZE.4)
        Path ejemploDestino = Path.of("..", "Planes", "REF20261116", "ejemplo_flujos_origen_destino.xlsx");
        if (Files.isDirectory(ejemploDestino.getParent())) {
            Files.write(ejemploDestino, xlsxBytes);
        }

        // Tambien verificar formato=csv
        MvcResult resCsv = mockMvc.perform(get("/api/reportes/flujos-origen-destino/exportar")
                        .with(user("aud").roles("AUDITOR"))
                        .param("formato", "csv")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andReturn();
        String csvTxt = resCsv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csvTxt.contains("FOLIO_ORI"));
        assertTrue(csvTxt.contains("ROMANA_KG"));
    }
}
