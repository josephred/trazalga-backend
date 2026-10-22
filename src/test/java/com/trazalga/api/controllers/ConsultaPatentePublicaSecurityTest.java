package com.trazalga.api.controllers;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.services.ConsultaPatenteService;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public class ConsultaPatentePublicaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConsultaPatenteService patenteService;

    @Test
    @DisplayName("TU.1: GET /api/public/patentes/comerciantes sin token responde 404 Not Found")
    void testListadoComerciantes_Responde404() throws Exception {
        mockMvc.perform(get("/api/public/patentes/comerciantes"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TU.1: GET /api/public/patentes/camiones-comerciantes sin token responde 404 Not Found")
    void testListadoCamionesComerciantes_Responde404() throws Exception {
        mockMvc.perform(get("/api/public/patentes/camiones-comerciantes"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TU.1: GET /api/public/patentes/{patente} no contiene datos personales ni operacionales restringidos")
    void testConsultaPatente_NoExponeDatosPersonales() throws Exception {
        Map<String, Object> mockRespuesta = new LinkedHashMap<>();
        mockRespuesta.put("patente", "ABCD12");
        mockRespuesta.put("vigente", true);
        mockRespuesta.put("horasTranscurridas", 12.5);
        mockRespuesta.put("fechaHoraMovimiento", "2026-10-22T08:00:00");
        mockRespuesta.put("tipoMovimiento", "COMERCIALIZADOR_A_PLANTA");
        mockRespuesta.put("especie", "Huiro Negro");
        mockRespuesta.put("horaInformada", true);

        when(patenteService.consultar(eq("ABCD12"), any())).thenReturn(mockRespuesta);

        String jsonResponse = mockMvc.perform(get("/api/public/patentes/ABCD12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("ABCD12"))
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.especie").value("Huiro Negro"))
                .andExpect(jsonPath("$.folio").doesNotExist())
                .andExpect(jsonPath("$.kg").doesNotExist())
                .andExpect(jsonPath("$.destino").doesNotExist())
                .andExpect(jsonPath("$.comunaOrigen").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> map = objectMapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});

        for (String key : map.keySet()) {
            String lower = key.toLowerCase();
            assertFalse(lower.startsWith("rut"), "No debe contener clave con prefijo 'rut': " + key);
            assertFalse(lower.startsWith("chofer"), "No debe contener clave con prefijo 'chofer': " + key);
            assertFalse(lower.startsWith("comerciante"), "No debe contener clave con prefijo 'comerciante': " + key);
            assertFalse(lower.equals("folio"), "No debe contener 'folio'");
            assertFalse(lower.equals("kg"), "No debe contener 'kg'");
            assertFalse(lower.equals("destino"), "No debe contener 'destino'");
            assertFalse(lower.equals("comunaorigen"), "No debe contener 'comunaOrigen'");
        }
    }
}
