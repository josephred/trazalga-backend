package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.ApiApplication;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.security.RateLimitFilter;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.ConsultaPatenteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConsultasCiudadanasIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IConsultaPatenteLogRepository logRepo;
    @Autowired private IDeclaracionMarcaRepository marcaRepo;
    @Autowired private ConfiguracionGeneralService configService;
    @Autowired private ConsultaPatenteService consultaPatenteService;
    @Autowired private RateLimitFilter rateLimitFilter;

    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepo;
    @Autowired private IPerfilRepository perfilRepo;
    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IEmbarcacionRepository embarcacionRepo;
    @Autowired private IBuzoRepository buzoRepo;
    @Autowired private IAmerbRepository amerbRepo;
    @Autowired private IDeclaracionArmadorRepository armadorRepo;
    @Autowired private IDeclaracionAreaRepository areaRepo;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepo;
    @Autowired private IDeclaracionComercializadorRepository comRepo;

    private String origAnomalos;
    private String origFono;
    private String origWhatsapp;
    private String origMuestraComuna;
    private String origRegistraDenuncia;
    private String origRetencionDias;
    private String origOrigenExigePatente;

    @BeforeEach
    void setup() {
        limpiarTodo();
        origAnomalos = configService.getValor("consulta_ciudadana_resultados_anomalos", "VENCIDO,SIN_REGISTROS,RETENIDO");
        origFono = configService.getValor("consulta_publica_fono_denuncia", "");
        origWhatsapp = configService.getValor("consulta_publica_whatsapp", "");
        origMuestraComuna = configService.getValor("consulta_publica_muestra_comuna", "true");
        origRegistraDenuncia = configService.getValor("consulta_publica_registra_denuncia", "true");
        origRetencionDias = configService.getValor("consulta_patente_log_retencion_dias", "365");
        origOrigenExigePatente = configService.getValor("origen_directo_exige_patente", "false");
    }

    @AfterEach
    void cleanup() {
        limpiarTodo();
        configService.updateConfig("consulta_ciudadana_resultados_anomalos", origAnomalos);
        configService.updateConfig("consulta_publica_fono_denuncia", origFono);
        configService.updateConfig("consulta_publica_whatsapp", origWhatsapp);
        configService.updateConfig("consulta_publica_muestra_comuna", origMuestraComuna);
        configService.updateConfig("consulta_publica_registra_denuncia", origRegistraDenuncia);
        configService.updateConfig("consulta_patente_log_retencion_dias", origRetencionDias);
        configService.updateConfig("origen_directo_exige_patente", origOrigenExigePatente);
    }

    private void limpiarTodo() {
        rateLimitFilter.clearAll();
        logRepo.deleteAll();
        jdbc.update("DELETE FROM declaracion_buzos");
        armadorRepo.deleteAll();
        areaRepo.deleteAll();
        recolectorRepo.deleteAll();
        comRepo.deleteAll();
        marcaRepo.deleteAll();
    }

    @Test
    @DisplayName("QC.5: Seguridad de GET /api/reportes/consultas-ciudadanas (401 sin token, 403 USER, 200 roles consola)")
    void seguridadEndpointConsultasCiudadanas() throws Exception {
        mockMvc.perform(get("/api/reportes/consultas-ciudadanas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reportes/consultas-ciudadanas").with(user("pescador").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reportes/consultas-ciudadanas").with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reportes/consultas-ciudadanas").with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reportes/consultas-ciudadanas").with(user("adm").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("QC.5: Agregación por resultado y patente, COUNT(DISTINCT ip_hash) sin exponer ip_hash, corte < fin+1 día y 0 marcas")
    void agregacionRangoTimestampYSinIpHashNiMarcas() throws Exception {
        long marcasAntes = marcaRepo.count();

        // 2026-11-10 (dentro del rango startDate=2026-11-10, endDate=2026-11-10)
        logRepo.save(log("AA1111", "hash_ip_1", fechaChile(2026, 11, 10, 0, 5, 0), "VENCIDO"));
        logRepo.save(log("AA1111", "hash_ip_1", fechaChile(2026, 11, 10, 12, 0, 0), "VENCIDO"));
        logRepo.save(log("AA1111", "hash_ip_2", fechaChile(2026, 11, 10, 23, 55, 0), "RETENIDO"));
        logRepo.save(log("BB2222", "hash_ip_1", fechaChile(2026, 11, 10, 14, 0, 0), "SIN_REGISTROS"));
        logRepo.save(log("CC3333", "hash_ip_1", fechaChile(2026, 11, 10, 10, 0, 0), "AUTORIZADO"));
        logRepo.save(log("CC3333", "hash_ip_2", fechaChile(2026, 11, 10, 11, 0, 0), "AUTORIZADO"));

        // 2026-11-11 00:05:00 (fuera de endDate=2026-11-10, dentro de endDate=2026-11-11)
        logRepo.save(log("BB2222", "hash_ip_3", fechaChile(2026, 11, 11, 0, 5, 0), "VENCIDO"));

        MvcResult resDia10 = mockMvc.perform(get("/api/reportes/consultas-ciudadanas")
                        .param("startDate", "2026-11-10")
                        .param("endDate", "2026-11-10")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();

        String bodyDia10 = resDia10.getResponse().getContentAsString();
        assertFalse(bodyDia10.contains("ip_hash"), "Nunca debe exponer ip_hash");
        assertFalse(bodyDia10.contains("ipHash"), "Nunca debe exponer ipHash");
        assertFalse(bodyDia10.contains("hash_ip_1"), "Nunca debe exponer hashes reales");
        assertFalse(bodyDia10.contains("hash_ip_2"), "Nunca debe exponer hashes reales");

        JsonNode root = objectMapper.readTree(bodyDia10);
        assertEquals(6L, root.path("totalConsultas").asLong());
        assertEquals(4L, root.path("totalAnomalas").asLong());

        JsonNode totales = root.path("totalesPorResultado");
        assertEquals(2L, totales.path("AUTORIZADO").asLong());
        assertEquals(2L, totales.path("VENCIDO").asLong());
        assertEquals(1L, totales.path("RETENIDO").asLong());
        assertEquals(1L, totales.path("SIN_REGISTROS").asLong());
        assertEquals(0L, totales.path("FINALIZADO").asLong());

        JsonNode anomalas = root.path("patentesAnomalas");
        assertEquals(2, anomalas.size(), "Solo patentes con resultados anómalos en el día 10");
        assertEquals("AA1111", anomalas.get(0).path("patente").asText());
        assertEquals(3L, anomalas.get(0).path("consultas").asLong());
        assertEquals(2L, anomalas.get(0).path("origenesDistintos").asLong());
        assertEquals("RETENIDO", anomalas.get(0).path("ultimoResultado").asText());
        assertNotNull(anomalas.get(0).path("primeraConsulta").asText(null));
        assertNotNull(anomalas.get(0).path("ultimaConsulta").asText(null));

        assertEquals("BB2222", anomalas.get(1).path("patente").asText());
        assertEquals(1L, anomalas.get(1).path("consultas").asLong());
        assertEquals(1L, anomalas.get(1).path("origenesDistintos").asLong());
        assertEquals("SIN_REGISTROS", anomalas.get(1).path("ultimoResultado").asText());

        // Al incluir el día 11, entra BB2222 de las 00:05:00
        MvcResult resDia10a11 = mockMvc.perform(get("/api/reportes/consultas-ciudadanas")
                        .param("startDate", "2026-11-10")
                        .param("endDate", "2026-11-11")
                        .with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root2 = objectMapper.readTree(resDia10a11.getResponse().getContentAsString());
        assertEquals(7L, root2.path("totalConsultas").asLong());
        assertEquals(5L, root2.path("totalAnomalas").asLong());
        assertEquals(2L, root2.path("patentesAnomalas").get(1).path("consultas").asLong());
        assertEquals(2L, root2.path("patentesAnomalas").get(1).path("origenesDistintos").asLong());
        assertEquals("VENCIDO", root2.path("patentesAnomalas").get(1).path("ultimoResultado").asText());

        // Parámetro configurable de resultados anómalos
        configService.updateConfig("consulta_ciudadana_resultados_anomalos", "AUTORIZADO");
        MvcResult resSoloAut = mockMvc.perform(get("/api/reportes/consultas-ciudadanas")
                        .param("startDate", "2026-11-10")
                        .param("endDate", "2026-11-10")
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rootAut = objectMapper.readTree(resSoloAut.getResponse().getContentAsString());
        assertEquals(1, rootAut.path("patentesAnomalas").size());
        assertEquals("CC3333", rootAut.path("patentesAnomalas").get(0).path("patente").asText());
        assertEquals(2L, rootAut.path("patentesAnomalas").get(0).path("consultas").asLong());

        assertEquals(marcasAntes, marcaRepo.count(), "La bandeja ciudadana nunca debe crear marcas automáticas");
    }

    @Test
    @DisplayName("QC.5: Endpoint público /api/public/patentes/{patente} respeta lista blanca con canales vacíos y poblados")
    void consultaPublicaListaBlancaE2E() throws Exception {
        configService.updateConfig("consulta_publica_fono_denuncia", "");
        configService.updateConfig("consulta_publica_whatsapp", "");

        MvcResult sinCanales = mockMvc.perform(get("/api/public/patentes/ZZ9988"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonSin = objectMapper.readTree(sinCanales.getResponse().getContentAsString());
        assertEquals("SIN_AUTORIZACION_VIGENTE", jsonSin.path("resultado").asText());
        assertEquals(Set.of("patente", "resultado", "texto", "consultadoEn"), fieldNames(jsonSin));

        configService.updateConfig("consulta_publica_fono_denuncia", "+56 800 000 001");
        configService.updateConfig("consulta_publica_whatsapp", "56900000001");

        MvcResult conCanales = mockMvc.perform(get("/api/public/patentes/ZZ9988"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonCon = objectMapper.readTree(conCanales.getResponse().getContentAsString());
        assertEquals("SIN_AUTORIZACION_VIGENTE", jsonCon.path("resultado").asText());
        assertEquals("+56 800 000 001", jsonCon.path("fonoDenuncia").asText());
        assertEquals("56900000001", jsonCon.path("whatsappDenuncia").asText());
        assertEquals(
                Set.of("patente", "resultado", "texto", "consultadoEn", "fonoDenuncia", "whatsappDenuncia"),
                fieldNames(jsonCon));
    }

    private ConsultaPatenteLogModel log(String patente, String ipHash, Date fecha, String resultado) {
        return ConsultaPatenteLogModel.builder()
                .patente(patente)
                .ipHash(ipHash)
                .fecha(fecha)
                .resultado(resultado)
                .build();
    }

    private Date fechaChile(int y, int m, int d, int hh, int mm, int ss) {
        return Date.from(LocalDateTime.of(y, m, d, hh, mm, ss).atZone(ApiApplication.ZONA_CHILE).toInstant());
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }

    @Test
    @DisplayName("ZC.1: Patentes inválidas (HOLA!, 25 chars, vacía, /) responden 400 sin guardar log; rutas reservadas responden 404")
    void rechazaPatentesInvalidasSinRegistrarLog() throws Exception {
        long antes = logRepo.count();

        for (String ruta : List.of(
                "/api/public/patentes/HOLA!",
                "/api/public/patentes/A123456789012345678901234",
                "/api/public/patentes/",
                "/api/public/patentes/AB12",
                "/api/public/patentes/AB/CD")) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Patente no válida"));
        }

        mockMvc.perform(get("/api/public/patentes/comerciantes")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/patentes/camiones-comerciantes")).andExpect(status().isNotFound());

        assertEquals(antes, logRepo.count(), "Las patentes inválidas y rutas reservadas no deben registrar logs");
    }

    @Test
    @DisplayName("ZC.5: Intención de denuncia FONO/WHATSAPP (204 + log + resumen), respeto a parámetro apagado y purga por retención")
    void registraIntencionDenunciaYDepuraRetencion() throws Exception {
        configService.updateConfig("consulta_publica_registra_denuncia", "true");

        mockMvc.perform(post("/api/public/patentes/ZZ7777/denuncia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"FONO\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/public/patentes/ZZ7777/denuncia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"WHATSAPP\"}"))
                .andExpect(status().isNoContent());

        List<ConsultaPatenteLogModel> logs = logRepo.findAll();
        assertTrue(logs.stream().anyMatch(l -> "DENUNCIA_FONO".equals(l.getResultado()) && "ZZ7777".equals(l.getPatente())));
        assertTrue(logs.stream().anyMatch(l -> "DENUNCIA_WHATSAPP".equals(l.getResultado()) && "ZZ7777".equals(l.getPatente())));

        String hoy = LocalDate.now(ApiApplication.ZONA_CHILE).toString();
        MvcResult summary = mockMvc.perform(get("/api/reportes/consultas-ciudadanas")
                        .param("startDate", hoy)
                        .param("endDate", hoy)
                        .with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(summary.getResponse().getContentAsString());
        assertEquals(2L, root.path("intentosDenuncia").asLong());

        // Con registro desactivado: sigue 204 pero no inserta filas nuevas
        long totalAntesApagado = logRepo.count();
        configService.updateConfig("consulta_publica_registra_denuncia", "false");
        mockMvc.perform(post("/api/public/patentes/ZZ7777/denuncia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"FONO\"}"))
                .andExpect(status().isNoContent());
        assertEquals(totalAntesApagado, logRepo.count());

        // Retención: > 365 días se purga; vacío no borra nada
        Date viejo400d = Date.from(LocalDateTime.now(ApiApplication.ZONA_CHILE).minusDays(400)
                .atZone(ApiApplication.ZONA_CHILE).toInstant());
        logRepo.save(log("OL0400", "h_old", viejo400d, "SIN_REGISTROS"));
        long countPrePurga = logRepo.count();

        configService.updateConfig("consulta_patente_log_retencion_dias", "");
        assertEquals(0, consultaPatenteService.depurarLogsAntiguos());
        assertEquals(countPrePurga, logRepo.count());

        configService.updateConfig("consulta_patente_log_retencion_dias", "365");
        assertEquals(1, consultaPatenteService.depurarLogsAntiguos());
        assertEquals(countPrePurga - 1, logRepo.count());
    }

    @Test
    @DisplayName("ZC.2: Comercializador rechaza con 422 fechaTraslado +5 días y persiste recibidaEn cuando es válida")
    void comercializadorValidaFechaTrasladoYRegistraRecibidaEn() throws Exception {
        FixtureHito1 fx = crearFixture("ZC2");
        LocalDate hoy = LocalDate.now(ApiApplication.ZONA_CHILE).minusDays(1);

        Map<String, Object> invalido = Map.ofEntries(
                Map.entry("usuario", Map.of("id", fx.comercializador.getId())),
                Map.entry("usuarioDestinatario", Map.of("id", fx.planta.getId())),
                Map.entry("codigoDestinatario", fx.planta.getRut()),
                Map.entry("nombreDestinatario", fx.planta.getNombres()),
                Map.entry("especie", Map.of("id", fx.especie.getId())),
                Map.entry("humedadEstado", Map.of("id", fx.humedad.getId())),
                Map.entry("folioOrigen", "FOL-ZC2-INV"),
                Map.entry("folioDesembarqueAc", "DC-ZC2-INV"),
                Map.entry("fechaDeclaracion", hoy.toString()),
                Map.entry("fechaTraslado", hoy.plusDays(5).toString()),
                Map.entry("hora", "10:00:00"),
                Map.entry("cantidad", new BigDecimal("100.00")),
                Map.entry("placaPatente", "CM5555"),
                Map.entry("estado", "ACTIVA")
        );
        mockMvc.perform(post("/declaracioncomercializador")
                        .with(user(fx.comercializador.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> valido = Map.ofEntries(
                Map.entry("usuario", Map.of("id", fx.comercializador.getId())),
                Map.entry("usuarioDestinatario", Map.of("id", fx.planta.getId())),
                Map.entry("codigoDestinatario", fx.planta.getRut()),
                Map.entry("nombreDestinatario", fx.planta.getNombres()),
                Map.entry("especie", Map.of("id", fx.especie.getId())),
                Map.entry("humedadEstado", Map.of("id", fx.humedad.getId())),
                Map.entry("folioOrigen", "FOL-ZC2-OK"),
                Map.entry("folioDesembarqueAc", "DC-ZC2-OK"),
                Map.entry("fechaDeclaracion", hoy.toString()),
                Map.entry("fechaTraslado", hoy.plusDays(1).toString()),
                Map.entry("hora", "10:00:00"),
                Map.entry("cantidad", new BigDecimal("100.00")),
                Map.entry("placaPatente", "CM5556"),
                Map.entry("estado", "ACTIVA")
        );
        MvcResult okRes = mockMvc.perform(post("/declaracioncomercializador")
                        .with(user(fx.comercializador.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valido)))
                .andExpect(status().isOk())
                .andReturn();
        long savedId = objectMapper.readTree(okRes.getResponse().getContentAsString()).path("id").asLong();
        DeclaracionComercializadorModel saved = comRepo.findById(savedId).orElseThrow();
        assertNotNull(saved.getRecibidaEn(), "El servidor debe registrar recibidaEn al crear la declaración");
    }

    @Test
    @DisplayName("ZC.9: Traslados directos a planta en Recolector/Armador/Area exigen patente si parámetro activo y publican consulta ciudadana")
    void origenDirectoPlantaExigePatenteYPublica() throws Exception {
        FixtureHito1 fx = crearFixture("ZC9");
        configService.updateConfig("origen_directo_exige_patente", "true");
        String hoy = LocalDate.now(ApiApplication.ZONA_CHILE).minusDays(1).toString();

        // 1) Armador directo a planta sin patente -> 422; con patente -> 200 y visible en consulta pública y fiscalizador
        Map<String, Object> armSinPatente = Map.ofEntries(
                Map.entry("usuario", Map.of("id", fx.armador.getId())),
                Map.entry("usuarioDestinatario", Map.of("id", fx.planta.getId())),
                Map.entry("tipoDestinatario", "planta"),
                Map.entry("embarcacion", Map.of("id", fx.embarcacion.getId())),
                Map.entry("buzo", Map.of("id", fx.buzo.getId())),
                Map.entry("caleta", Map.of("id", fx.caleta.getId())),
                Map.entry("especie", Map.of("id", fx.especie.getId())),
                Map.entry("extraccionTipo", Map.of("id", fx.extraccionTipo.getId())),
                Map.entry("humedadEstado", Map.of("id", fx.humedad.getId())),
                Map.entry("folioOrigen", "FOL-ARM-NP"),
                Map.entry("folioDesembarqueDa", "DA-ZC9-NP"),
                Map.entry("fechaExtraccion", hoy),
                Map.entry("fechaDeclaracion", hoy),
                Map.entry("hora", "08:00:00"),
                Map.entry("desembarque", new BigDecimal("50.00"))
        );
        mockMvc.perform(post("/declaracionarmador")
                        .with(user(fx.armador.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(armSinPatente)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> armConPatente = new java.util.HashMap<>(armSinPatente);
        armConPatente.put("folioOrigen", "FOL-ARM-OK");
        armConPatente.put("folioDesembarqueDa", "DA-ZC9-OK");
        armConPatente.put("placaPatente", "AM9012");
        mockMvc.perform(post("/declaracionarmador")
                        .with(user(fx.armador.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(armConPatente)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/patentes/AM9012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("AUTORIZADO"));

        mockMvc.perform(get("/api/consultas/patentes/AM9012").with(user("fisc").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual.estado").value("AUTORIZADO"))
                .andExpect(jsonPath("$.movimientos[0].tipo").value("ARMADOR"));

        // 2) Area directo a planta sin patente -> 422; con patente -> 200 y AUTORIZADO
        Map<String, Object> areaSinPatente = Map.ofEntries(
                Map.entry("usuario", Map.of("id", fx.areaUser.getId())),
                Map.entry("usuarioDestinatario", Map.of("id", fx.planta.getId())),
                Map.entry("tipoDestinatario", "planta"),
                Map.entry("amerb", Map.of("id", fx.amerb.getId())),
                Map.entry("caleta", Map.of("id", fx.caleta.getId())),
                Map.entry("especie", Map.of("id", fx.especie.getId())),
                Map.entry("extraccionTipo", Map.of("id", fx.extraccionTipo.getId())),
                Map.entry("humedadEstado", Map.of("id", fx.humedad.getId())),
                Map.entry("folioOrigen", "FOL-AR-NP"),
                Map.entry("folioDesembarqueAmerb", "AM-ZC9-NP"),
                Map.entry("fechaExtraccion", hoy),
                Map.entry("fechaDeclaracion", hoy),
                Map.entry("hora", "08:00:00"),
                Map.entry("desembarque", new BigDecimal("50.00"))
        );
        mockMvc.perform(post("/declaracionarea")
                        .with(user(fx.areaUser.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(areaSinPatente)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> areaConPatente = new java.util.HashMap<>(areaSinPatente);
        areaConPatente.put("folioOrigen", "FOL-AR-OK");
        areaConPatente.put("folioDesembarqueAmerb", "AM-ZC9-OK");
        areaConPatente.put("placaPatente", "AE9012");
        mockMvc.perform(post("/declaracionarea")
                        .with(user(fx.areaUser.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(areaConPatente)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/patentes/AE9012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("AUTORIZADO"));

        // 3) Recolector directo a planta sin patente -> 422; con patente -> 200 y AUTORIZADO
        Map<String, Object> recSinPatente = Map.ofEntries(
                Map.entry("usuario", Map.of("id", fx.recolector.getId())),
                Map.entry("nombre", fx.recolector.getNombres()),
                Map.entry("codigoSernapesca", "RPA-ZC9"),
                Map.entry("usuarioDestinatario", Map.of("id", fx.planta.getId())),
                Map.entry("codigoDestinatario", fx.planta.getRut()),
                Map.entry("nombreDestinatario", fx.planta.getNombres()),
                Map.entry("caleta", Map.of("id", fx.caleta.getId())),
                Map.entry("comuna", Map.of("id", fx.comuna.getId())),
                Map.entry("especie", Map.of("id", fx.especie.getId())),
                Map.entry("extraccionTipo", Map.of("id", fx.extraccionTipo.getId())),
                Map.entry("humedadEstado", Map.of("id", fx.humedad.getId())),
                Map.entry("folioOrigen", "FOL-RC-NP"),
                Map.entry("folioDesembarqueRo", "RO-ZC9-NP"),
                Map.entry("fechaExtraccion", hoy),
                Map.entry("fechaDeclaracion", hoy),
                Map.entry("hora", "08:00:00"),
                Map.entry("desembarque", new BigDecimal("50.00"))
        );
        mockMvc.perform(post("/declaracionrecolector")
                        .with(user(fx.recolector.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recSinPatente)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> recConPatente = new java.util.HashMap<>(recSinPatente);
        recConPatente.put("folioOrigen", "FOL-RC-OK");
        recConPatente.put("folioDesembarqueRo", "RO-ZC9-OK");
        recConPatente.put("placaPatente", "RC9012");
        mockMvc.perform(post("/declaracionrecolector")
                        .with(user(fx.recolector.getRut()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recConPatente)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/patentes/RC9012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("AUTORIZADO"));
    }

    private static class FixtureHito1 {
        ComunaModel comuna;
        CaletaModel caleta;
        EspecieModel especie;
        HumedadEstadoModel humedad;
        ExtraccionTipoModel extraccionTipo;
        EmbarcacionModel embarcacion;
        BuzoModel buzo;
        AmerbModel amerb;
        UsuarioModel recolector;
        UsuarioModel armador;
        UsuarioModel areaUser;
        UsuarioModel comercializador;
        UsuarioModel planta;
    }

    private FixtureHito1 crearFixture(String tag) {
        String suf = tag + "_" + System.nanoTime();
        FixtureHito1 fx = new FixtureHito1();
        RegionModel reg = regionRepo.save(RegionModel.builder().nombre("Region " + suf).codigo("04").build());
        ProvinciaModel prov = provinciaRepo.save(ProvinciaModel.builder().nombre("Prov " + suf).region(reg).build());
        fx.comuna = comunaRepo.save(ComunaModel.builder().nombre("Comuna " + suf).provincia(prov).region(reg).build());
        fx.caleta = caletaRepo.save(CaletaModel.builder().nombre("Caleta " + suf).comuna(fx.comuna).region(reg).build());
        fx.especie = especieRepo.save(EspecieModel.builder().nombre("Huiro Palo " + suf).activo(true).build());
        fx.humedad = humedadRepo.save(HumedadEstadoModel.builder().nombre("Humedo " + suf).build());
        fx.extraccionTipo = extraccionTipoRepo.save(ExtraccionTipoModel.builder().nombre("Varado " + suf).build());
        fx.embarcacion = embarcacionRepo.save(EmbarcacionModel.builder().nombre("Bote " + suf).codigo("EMB-" + suf).build());
        fx.buzo = buzoRepo.save(BuzoModel.builder().nombre("Buzo " + suf).codigo("BZ-" + suf).build());
        fx.amerb = amerbRepo.save(AmerbModel.builder().nombre("AMERB " + suf).region(reg.getNombre()).estado("Activo").build());

        PerfilModel pRec = asegurarPerfil(10L, "RECOLECTOR");
        PerfilModel pArm = asegurarPerfil(2L, "ARMADOR");
        PerfilModel pArea = asegurarPerfil(3L, "AREA");
        PerfilModel pCom = asegurarPerfil(4L, "COMERCIALIZADOR");
        PerfilModel pPla = asegurarPerfil(5L, "PLANTA");

        String rutSuf = String.valueOf(Math.abs(System.nanoTime()) % 1_000_000_000L);
        fx.recolector = usuarioRepo.save(UsuarioModel.builder().rut("11-" + rutSuf).nombres("Rec " + tag).apellidop("P").apellidom("M").clave("x").perfil(pRec).comuna(fx.comuna).estado("1").build());
        fx.armador = usuarioRepo.save(UsuarioModel.builder().rut("12-" + rutSuf).nombres("Arm " + tag).apellidop("P").apellidom("M").clave("x").perfil(pArm).comuna(fx.comuna).estado("1").build());
        fx.areaUser = usuarioRepo.save(UsuarioModel.builder().rut("13-" + rutSuf).nombres("Area " + tag).apellidop("P").apellidom("M").clave("x").perfil(pArea).comuna(fx.comuna).estado("1").build());
        fx.comercializador = usuarioRepo.save(UsuarioModel.builder().rut("14-" + rutSuf).nombres("Com " + tag).apellidop("P").apellidom("M").clave("x").perfil(pCom).comuna(fx.comuna).estado("1").build());
        fx.planta = usuarioRepo.save(UsuarioModel.builder().rut("15-" + rutSuf).nombres("Planta " + tag).apellidop("P").apellidom("M").clave("x").perfil(pPla).comuna(fx.comuna).estado("1").build());
        return fx;
    }

    private PerfilModel asegurarPerfil(Long id, String nombre) {
        if (!perfilRepo.existsById(id)) {
            jdbc.update("INSERT INTO perfil (id, nombre) VALUES (?, ?)", id, nombre);
        }
        return perfilRepo.findById(id).orElseThrow();
    }
}
