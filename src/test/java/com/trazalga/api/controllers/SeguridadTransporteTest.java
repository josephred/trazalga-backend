package com.trazalga.api.controllers;

import com.trazalga.api.dto.MisFrecuentesDTO;
import com.trazalga.api.dto.MisFrecuentesDTO.ChoferFrecuenteDTO;
import com.trazalga.api.dto.MisFrecuentesDTO.VehiculoFrecuenteDTO;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.repositories.ITransportistaAliasRepository;
import com.trazalga.api.repositories.ITransportistaRepository;
import com.trazalga.api.repositories.IVehiculoRepository;
import com.trazalga.api.services.FusionTransportistaService;
import com.trazalga.api.services.ReconstruccionTransporteService;
import com.trazalga.api.services.ReporteTransporteService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.defer-datasource-initialization=true"
})
public class SeguridadTransporteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ITransportistaRepository transportistaRepository;

    @MockBean
    private ITransportistaAliasRepository aliasRepository;

    @MockBean
    private IVehiculoRepository vehiculoRepository;

    @MockBean
    private FusionTransportistaService fusionService;

    @MockBean
    private ReconstruccionTransporteService reconstruccionService;

    @MockBean
    private ReporteTransporteService reporteTransporteService;

    @Test
    @WithAnonymousUser
    @DisplayName("TT.8: Acceso anónimo a /api/transporte/transportistas responde 401")
    void testAnonimoTransportistas_401() throws Exception {
        mockMvc.perform(get("/api/transporte/transportistas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "11.111.111-1", roles = {"USER"})
    @DisplayName("TT.8: Usuario ROLE_USER a /api/transporte/transportistas responde 403")
    void testRoleUserTransportistas_403() throws Exception {
        mockMvc.perform(get("/api/transporte/transportistas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33.333.333-3", roles = {"FISCALIZADOR"})
    @DisplayName("TT.8: Usuario ROLE_FISCALIZADOR a /api/transporte/transportistas responde 200")
    void testRoleFiscalizadorTransportistas_200() throws Exception {
        when(transportistaRepository.findAllActivos()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/transporte/transportistas"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "33.333.333-3", roles = {"FISCALIZADOR"})
    @DisplayName("TT.8: Usuario ROLE_FISCALIZADOR a /api/admin/transporte/reconstruir responde 403 (solo ADMIN)")
    void testRoleFiscalizadorReconstruir_403() throws Exception {
        mockMvc.perform(post("/api/admin/transporte/reconstruir"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "22.222.222-2", roles = {"ADMIN"})
    @DisplayName("TT.8: Usuario ROLE_ADMIN a /api/admin/transporte/reconstruir responde 200")
    void testRoleAdminReconstruir_200() throws Exception {
        when(reconstruccionService.reconstruir()).thenReturn(new com.trazalga.api.dto.InformeReconstruccionDTO());
        mockMvc.perform(post("/api/admin/transporte/reconstruir"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "11.111.111-1", roles = {"USER"})
    @DisplayName("TT.8: Usuario ROLE_USER a /api/transporte/mis-frecuentes responde 200 con sus choferes propios")
    void testRoleUserMisFrecuentes_200() throws Exception {
        MisFrecuentesDTO dto = MisFrecuentesDTO.builder()
                .choferes(List.of(ChoferFrecuenteDTO.builder().nombre("Chofer Propio").rut("12.345.678-5").veces(5).build()))
                .vehiculos(List.of(VehiculoFrecuenteDTO.builder().patente("ABCD12").veces(3).build()))
                .build();

        when(reporteTransporteService.obtenerMisFrecuentes(eq("11.111.111-1"))).thenReturn(dto);

        mockMvc.perform(get("/api/transporte/mis-frecuentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choferes[0].nombre").value("Chofer Propio"))
                .andExpect(jsonPath("$.vehiculos[0].patente").value("ABCD12"));
    }
}
