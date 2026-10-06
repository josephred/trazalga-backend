package com.trazalga.api.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.trazalga.api.dto.ResultadoFolioDTO;
import com.trazalga.api.services.ConsultaFolioService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.defer-datasource-initialization=true"
})
public class ConsultaTrazabilidadSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultaFolioService consultaFolioService;

    @BeforeEach
    void setUp() {
        ResultadoFolioDTO dummy = ResultadoFolioDTO.builder()
                .tipo("ARMADOR")
                .id(1L)
                .campo("Folio DA")
                .valor("DA-100")
                .actor("Armador Test")
                .build();
        when(consultaFolioService.buscar(anyString(), anyString()))
                .thenReturn(Collections.singletonList(dummy));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("T2.2 Seguridad: Petición anónima (sin token) a /api/consultas/folio devuelve HTTP 401 Unauthorized")
    void testSinToken_Devuelve401() throws Exception {
        mockMvc.perform(get("/api/consultas/folio")
                .param("q", "DA100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("No autorizado"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("T2.2 Seguridad: Petición anónima a /consultas/folio (ruta sin prefijo api) devuelve HTTP 401 Unauthorized")
    void testSinToken_RutaSinPrefijo_Devuelve401() throws Exception {
        mockMvc.perform(get("/consultas/folio")
                .param("q", "DA100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("No autorizado"));
    }

    @Test
    @WithMockUser(username = "11.111.111-1", roles = {"USER"})
    @DisplayName("T2.2 Seguridad: Usuario de app móvil con ROLE_USER a /api/consultas/folio devuelve HTTP 403 Forbidden")
    void testUsuarioMovilRoleUser_Devuelve403() throws Exception {
        mockMvc.perform(get("/api/consultas/folio")
                .param("q", "DA100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acceso denegado"));
    }

    @Test
    @WithMockUser(username = "22.222.222-2", roles = {"ADMIN"})
    @DisplayName("T2.2 Seguridad: Usuario con ROLE_ADMIN a /api/consultas/folio devuelve HTTP 200 OK")
    void testUsuarioAdmin_Devuelve200() throws Exception {
        mockMvc.perform(get("/api/consultas/folio")
                .param("q", "DA100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ARMADOR"))
                .andExpect(jsonPath("$[0].valor").value("DA-100"));
    }

    @Test
    @WithMockUser(username = "33.333.333-3", roles = {"FISCALIZADOR"})
    @DisplayName("T2.2 Seguridad: Usuario con ROLE_FISCALIZADOR a /api/consultas/folio devuelve HTTP 200 OK")
    void testUsuarioFiscalizador_Devuelve200() throws Exception {
        mockMvc.perform(get("/api/consultas/folio")
                .param("q", "DA100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ARMADOR"));
    }

    @Test
    @WithMockUser(username = "44.444.444-4", roles = {"AUDITOR"})
    @DisplayName("T2.2 Seguridad: Usuario con ROLE_AUDITOR a /api/consultas/folio devuelve HTTP 200 OK")
    void testUsuarioAuditor_Devuelve200() throws Exception {
        mockMvc.perform(get("/api/consultas/folio")
                .param("q", "DA100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ARMADOR"));
    }
}
