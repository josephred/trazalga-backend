package com.trazalga.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.dto.CuotaLoteDTO;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.services.CuotaExtraccionService;

@ExtendWith(MockitoExtension.class)
public class CuotaExtraccionControllerMockMvcTest {

    private MockMvc mockMvc;

    @Mock
    private CuotaExtraccionService cuotaService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setDateFormat(new java.text.SimpleDateFormat("yyyy-MM-dd"));

    @InjectMocks
    private CuotaExtraccionController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("T1.8 MockMvc: POST /api/cuotas/lote con lote anual exitoso devuelve 200 y lista de cuotas")
    void testPostLote_Exitoso_Devuelve200() throws Exception {
        List<CuotaExtraccionModel> mockCreadas = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            mockCreadas.add(CuotaExtraccionModel.builder()
                    .id((long) m)
                    .periodo("MENSUAL")
                    .limiteKg(100_000.0)
                    .fechaInicio(Date.valueOf(LocalDate.of(2026, m, 1)))
                    .fechaFin(Date.valueOf(LocalDate.of(2026, m, 28)))
                    .build());
        }

        when(cuotaService.saveLote(any(CuotaLoteDTO.class))).thenReturn(mockCreadas);

        String jsonPayload = """
        {
            "anio": 2026,
            "ambito": "AREA_LIBRE",
            "nivelAgregacion": "COMUNA",
            "regionId": 4,
            "comunaIds": [4101, 4102],
            "especieId": 1,
            "extraccionTipoId": 10,
            "metrica": "CAPTURA",
            "modoAccion": "SOLO_ALERTA",
            "meses": [
                { "mes": 1, "limiteKg": 100000, "activo": true },
                { "mes": 2, "limiteKg": 100000, "activo": true },
                { "mes": 3, "limiteKg": 100000, "activo": true }
            ]
        }
        """;

        mockMvc.perform(post("/api/cuotas/lote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].limiteKg").value(100000.0));
    }

    @Test
    @DisplayName("T1.8 MockMvc: POST /api/cuotas/lote cuando marzo choca devuelve 422 señalando el mes")
    void testPostLote_ConflictoMarzo_Devuelve422ConMensaje() throws Exception {
        when(cuotaService.saveLote(any(CuotaLoteDTO.class))).thenThrow(
                new IllegalArgumentException("Error en mes Marzo: La comuna La Serena ya tiene una cuota activa de Huiro negro (Varado) del 01-03-2026 al 31-03-2026 (cuota #999).")
        );

        String jsonPayload = """
        {
            "anio": 2026,
            "especieId": 1,
            "extraccionTipoId": 10,
            "comunaIds": [4101],
            "meses": [
                { "mes": 1, "limiteKg": 100000, "activo": true },
                { "mes": 3, "limiteKg": 100000, "activo": true }
            ]
        }
        """;

        mockMvc.perform(post("/api/cuotas/lote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "Error en mes Marzo: La comuna La Serena ya tiene una cuota activa de Huiro negro (Varado) del 01-03-2026 al 31-03-2026 (cuota #999)."
                ));
    }

    @Test
    @DisplayName("T1.4 MockMvc: POST /api/cuotas con fecha 2026-03-01 se procesa sin desfase")
    void testPostCuota_FechaSinDesfase() throws Exception {
        CuotaExtraccionModel guardada = CuotaExtraccionModel.builder()
                .id(50L)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-03-01"))
                .fechaFin(Date.valueOf("2026-03-31"))
                .limiteKg(100_000.0)
                .build();

        when(cuotaService.save(any(com.trazalga.api.dto.CuotaRequestDTO.class))).thenReturn(guardada);

        String payload = """
        {
            "especieId": 1,
            "extraccionTipoId": 10,
            "ambito": "AREA_LIBRE",
            "nivelAgregacion": "COMUNA",
            "periodo": "MENSUAL",
            "fechaInicio": "2026-03-01",
            "fechaFin": "2026-03-31",
            "limiteKg": 100000
        }
        """;

        mockMvc.perform(post("/api/cuotas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.fechaInicio").value("2026-03-01"));
    }

    @Test
    @DisplayName("TC.2 MockMvc: PUT /api/cuotas/{id}/cerrar acepta cuerpo opcional")
    void testCerrarCuota_CuerpoOpcional() throws Exception {
        CuotaExtraccionModel cerrada = CuotaExtraccionModel.builder()
                .id(10L)
                .estado("CERRADA")
                .build();

        when(cuotaService.cerrarCuota(eq(10L), eq("ADMINISTRATIVO"), eq("Cierre de prueba"))).thenReturn(cerrada);

        String payload = """
        {
            "motivo": "ADMINISTRATIVO",
            "observacion": "Cierre de prueba"
        }
        """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/cuotas/10/cerrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.estado").value("CERRADA"));
    }

    @Test
    @DisplayName("TC.2 MockMvc: PUT /api/cuotas/{id}/reabrir sin motivo devuelve 422")
    void testReabrirCuota_SinMotivoDevuelve422() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/cuotas/10/reabrir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("TC.2 MockMvc: GET /api/cuotas/{id}/eventos devuelve lista")
    void testGetEventos_DevuelveLista() throws Exception {
        com.trazalga.api.models.CuotaExtraccionEventoModel ev = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                .id(1L)
                .tipo("CREADA")
                .motivo("INICIO")
                .build();

        when(cuotaService.getEventos(10L)).thenReturn(List.of(ev));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/cuotas/10/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("CREADA"));
    }
}
