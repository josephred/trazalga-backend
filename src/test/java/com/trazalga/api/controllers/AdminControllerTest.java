package com.trazalga.api.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.trazalga.api.models.TareaProgramadaEjecucionModel;
import com.trazalga.api.services.TareaProgramadaService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TareaProgramadaService tareaProgramadaService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("TA.6: GET /api/admin/tareas-programadas con rol ADMIN devuelve 200 y listado")
    void testGetTareasProgramadas_ConRolAdmin_Retorna200() throws Exception {
        TareaProgramadaEjecucionModel tarea = TareaProgramadaEjecucionModel.builder()
                .id(1L)
                .nombre("REVISION_CUOTAS")
                .inicio(new Date())
                .fin(new Date())
                .estado("OK")
                .procesados(10)
                .mensaje("OK")
                .build();

        when(tareaProgramadaService.listarUltimoEstado()).thenReturn(List.of(tarea));

        mockMvc.perform(get("/api/admin/tareas-programadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("REVISION_CUOTAS"))
                .andExpect(jsonPath("$[0].estado").value("OK"));
    }

    @Test
    @WithMockUser(username = "recolector", roles = {"RECOLECTOR"})
    @DisplayName("TA.6: GET /api/admin/tareas-programadas sin rol ADMIN devuelve 403 Forbidden")
    void testGetTareasProgramadas_SinRolAdmin_Retorna403() throws Exception {
        mockMvc.perform(get("/api/admin/tareas-programadas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TA.6: GET /api/admin/tareas-programadas sin autenticación devuelve 401")
    void testGetTareasProgramadas_SinAuth_Rechazado() throws Exception {
        mockMvc.perform(get("/api/admin/tareas-programadas"))
                .andExpect(status().isUnauthorized());
    }
}
