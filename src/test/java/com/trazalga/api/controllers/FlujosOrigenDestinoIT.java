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
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
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

import java.math.BigDecimal;
import java.sql.Date;

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
    @Autowired private IPlantaRepository plantaRepo;
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
}
