package com.trazalga.api.controllers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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

import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO;
import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO.EstadoActualDTO;
import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO.MovimientoPatenteDTO;
import com.trazalga.api.services.ConsultaPatenteService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.defer-datasource-initialization=true"
})
public class ConsultaPatenteFiscalizadorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultaPatenteService patenteService;

    @BeforeEach
    void setUp() {
        ConsultaPatenteFiscalizadorDTO dummy = ConsultaPatenteFiscalizadorDTO.builder()
                .patente("ABCD12")
                .estadoActual(EstadoActualDTO.builder()
                        .estado("AUTORIZADO")
                        .comunaDestino("Coquimbo")
                        .tipoAlga("Huiro negro")
                        .build())
                .movimientos(List.of(
                        MovimientoPatenteDTO.builder()
                                .tipo("COMERCIALIZADOR")
                                .id(101L)
                                .folioOrigen("DC-101")
                                .patenteCamion("ABCD12")
                                .comercializadorNombre("Comercializadora Algas")
                                .build()
                ))
                .build();

        lenient().when(patenteService.consultarFiscalizador(eq("ABCD12"), any(), any(), anyString(), any()))
                .thenReturn(dummy);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("TN.6: Petición anónima (sin token) a /api/consultas/patentes/{patente} devuelve HTTP 401 Unauthorized")
    void testSinToken_Devuelve401() throws Exception {
        mockMvc.perform(get("/api/consultas/patentes/ABCD12"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("No autorizado"));
    }

    @Test
    @WithMockUser(username = "11.111.111-1", roles = {"USER"})
    @DisplayName("TN.6: Usuario con ROLE_USER a /api/consultas/patentes/{patente} devuelve HTTP 403 Forbidden")
    void testUsuarioRoleUser_Devuelve403() throws Exception {
        mockMvc.perform(get("/api/consultas/patentes/ABCD12"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acceso denegado"));
    }

    @Test
    @WithMockUser(username = "33.333.333-3", roles = {"FISCALIZADOR"})
    @DisplayName("TN.6: Usuario con ROLE_FISCALIZADOR a /api/consultas/patentes/{patente} devuelve HTTP 200 OK con traza")
    void testUsuarioFiscalizador_Devuelve200() throws Exception {
        mockMvc.perform(get("/api/consultas/patentes/ABCD12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("ABCD12"))
                .andExpect(jsonPath("$.estadoActual.estado").value("AUTORIZADO"))
                .andExpect(jsonPath("$.estadoActual.comunaDestino").value("Coquimbo"))
                .andExpect(jsonPath("$.movimientos[0].id").value(101))
                .andExpect(jsonPath("$.movimientos[0].folioOrigen").value("DC-101"));

        verify(patenteService).consultarFiscalizador(eq("ABCD12"), isNull(), isNull(), eq("33.333.333-3"), isNull());
    }

    @Test
    @WithMockUser(username = "22.222.222-2", roles = {"ADMIN"})
    @DisplayName("TN.6: Usuario con ROLE_ADMIN a /api/consultas/patentes/{patente} devuelve HTTP 200 OK")
    void testUsuarioAdmin_Devuelve200() throws Exception {
        mockMvc.perform(get("/api/consultas/patentes/ABCD12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("ABCD12"))
                .andExpect(jsonPath("$.estadoActual.estado").value("AUTORIZADO"));

        verify(patenteService).consultarFiscalizador(eq("ABCD12"), isNull(), isNull(), eq("22.222.222-2"), isNull());
    }
}
