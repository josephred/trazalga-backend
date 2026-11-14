package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.ApiApplication;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.ConsultaPatenteLogModel;
import com.trazalga.api.repositories.IConsultaPatenteLogRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ConsultasCiudadanasIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IConsultaPatenteLogRepository logRepo;
    @Autowired private IDeclaracionMarcaRepository marcaRepo;
    @Autowired private ConfiguracionGeneralService configService;

    private String origAnomalos;
    private String origFono;
    private String origWhatsapp;
    private String origMuestraComuna;

    @BeforeEach
    void setup() {
        logRepo.deleteAll();
        origAnomalos = configService.getValor("consulta_ciudadana_resultados_anomalos", "VENCIDO,SIN_REGISTROS,RETENIDO");
        origFono = configService.getValor("consulta_publica_fono_denuncia", "");
        origWhatsapp = configService.getValor("consulta_publica_whatsapp", "");
        origMuestraComuna = configService.getValor("consulta_publica_muestra_comuna", "true");
    }

    @AfterEach
    void cleanup() {
        logRepo.deleteAll();
        configService.updateConfig("consulta_ciudadana_resultados_anomalos", origAnomalos);
        configService.updateConfig("consulta_publica_fono_denuncia", origFono);
        configService.updateConfig("consulta_publica_whatsapp", origWhatsapp);
        configService.updateConfig("consulta_publica_muestra_comuna", origMuestraComuna);
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
}
