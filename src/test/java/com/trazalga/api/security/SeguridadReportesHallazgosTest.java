package com.trazalga.api.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.services.DeclaracionMarcaService;
import com.trazalga.api.services.ReportService;

/**
 * Suite de pruebas de seguridad para TX.1 (Paso 3).
 * Verifica que los endpoints que exponen datos personales (RUT, nombres, marcas, cuotas y reportes)
 * están estrictamente protegidos para roles de consola (ADMIN, FISCALIZADOR, AUDITOR),
 * que la reapertura de marcas está prohibida para AUDITOR,
 * y que las rutas operativas de la app móvil permanecen accesibles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public class SeguridadReportesHallazgosTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private DeclaracionMarcaService declaracionMarcaService;

    // 1. Reportes sin token -> 401
    @Test
    @DisplayName("TX.1: GET /api/reportes/retencion-bodega sin token devuelve 401 Unauthorized")
    void testReportes_SinToken_Retorna401() throws Exception {
        mockMvc.perform(get("/api/reportes/retencion-bodega"))
                .andExpect(status().isUnauthorized());
    }

    // 2. Reportes con ROLE_USER (app móvil) -> 403
    @Test
    @WithMockUser(username = "pescador", roles = {"USER"})
    @DisplayName("TX.1: GET /api/reportes/retencion-bodega con ROLE_USER (app móvil) devuelve 403 Forbidden")
    void testReportes_ConRoleUser_Retorna403() throws Exception {
        mockMvc.perform(get("/api/reportes/retencion-bodega"))
                .andExpect(status().isForbidden());
    }

    // 3. Reportes con FISCALIZADOR -> 200
    @Test
    @WithMockUser(username = "fiscalizador", roles = {"FISCALIZADOR"})
    @DisplayName("TX.1: GET /api/reportes/retencion-bodega con FISCALIZADOR devuelve 200 OK")
    void testReportes_ConFiscalizador_Retorna200() throws Exception {
        when(reportService.getRetencionBodegaMetrics()).thenReturn(Map.of("total", 0));

        mockMvc.perform(get("/api/reportes/retencion-bodega"))
                .andExpect(status().isOk());
    }

    // 4. Reportes con AUDITOR -> 200
    @Test
    @WithMockUser(username = "auditor", roles = {"AUDITOR"})
    @DisplayName("TX.1: GET /api/reportes/retencion-bodega con AUDITOR devuelve 200 OK")
    void testReportes_ConAuditor_Retorna200() throws Exception {
        when(reportService.getRetencionBodegaMetrics()).thenReturn(Map.of("total", 0));

        mockMvc.perform(get("/api/reportes/retencion-bodega"))
                .andExpect(status().isOk());
    }

    // 5. Declaracion marcas sin token -> 401
    @Test
    @DisplayName("TX.1: GET /api/declaracion-marcas sin token devuelve 401 Unauthorized")
    void testDeclaracionMarcas_SinToken_Retorna401() throws Exception {
        mockMvc.perform(get("/api/declaracion-marcas"))
                .andExpect(status().isUnauthorized());
    }

    // 6. Declaracion marcas con ROLE_USER -> 403
    @Test
    @WithMockUser(username = "pescador", roles = {"USER"})
    @DisplayName("TX.1: GET /api/declaracion-marcas con ROLE_USER devuelve 403 Forbidden")
    void testDeclaracionMarcas_ConRoleUser_Retorna403() throws Exception {
        mockMvc.perform(get("/api/declaracion-marcas"))
                .andExpect(status().isForbidden());
    }

    // 7. Declaracion marcas con FISCALIZADOR -> 200
    @Test
    @WithMockUser(username = "fiscalizador", roles = {"FISCALIZADOR"})
    @DisplayName("TX.1: GET /api/declaracion-marcas con FISCALIZADOR devuelve 200 OK")
    void testDeclaracionMarcas_ConFiscalizador_Retorna200() throws Exception {
        when(declaracionMarcaService.getAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/declaracion-marcas"))
                .andExpect(status().isOk());
    }

    // 8. Reabrir marca con AUDITOR -> 403 (Criterio de aceptación explícito: reabrir una marca con AUDITOR, 403)
    @Test
    @WithMockUser(username = "auditor", roles = {"AUDITOR"})
    @DisplayName("TX.1: PUT /api/declaracion-marcas/1/reabrir con AUDITOR devuelve 403 Forbidden")
    void testReabrirMarca_ConAuditor_Retorna403() throws Exception {
        mockMvc.perform(put("/api/declaracion-marcas/1/reabrir"))
                .andExpect(status().isForbidden());
    }

    // 9. Reabrir marca con ROLE_USER -> 403
    @Test
    @WithMockUser(username = "pescador", roles = {"USER"})
    @DisplayName("TX.1: PUT /api/declaracion-marcas/1/reabrir con ROLE_USER devuelve 403 Forbidden")
    void testReabrirMarca_ConRoleUser_Retorna403() throws Exception {
        mockMvc.perform(put("/api/declaracion-marcas/1/reabrir"))
                .andExpect(status().isForbidden());
    }

    // 10. Reabrir marca con FISCALIZADOR -> 200
    @Test
    @WithMockUser(username = "fiscalizador", roles = {"FISCALIZADOR"})
    @DisplayName("TX.1: PUT /api/declaracion-marcas/1/reabrir con FISCALIZADOR devuelve 200 OK")
    void testReabrirMarca_ConFiscalizador_Retorna200() throws Exception {
        DeclaracionMarcaModel marca = DeclaracionMarcaModel.builder().id(1L).marca("RETENCION_TIEMPO").resuelta(false).build();
        when(declaracionMarcaService.reabrirMarca(1L)).thenReturn(Optional.of(marca));

        mockMvc.perform(put("/api/declaracion-marcas/1/reabrir"))
                .andExpect(status().isOk());
    }

    // 11. Cuotas GET sin token -> 401
    @Test
    @DisplayName("TX.1: GET /api/cuotas/listado sin token devuelve 401 Unauthorized")
    void testCuotas_SinToken_Retorna401() throws Exception {
        mockMvc.perform(get("/api/cuotas/listado"))
                .andExpect(status().isUnauthorized());
    }

    // 12. Cuotas GET con ROLE_USER -> 403
    @Test
    @WithMockUser(username = "pescador", roles = {"USER"})
    @DisplayName("TX.1: GET /api/cuotas/listado con ROLE_USER devuelve 403 Forbidden")
    void testCuotas_ConRoleUser_Retorna403() throws Exception {
        mockMvc.perform(get("/api/cuotas/listado"))
                .andExpect(status().isForbidden());
    }

    // 13. Cuotas GET con FISCALIZADOR -> 200
    @Test
    @WithMockUser(username = "fiscalizador", roles = {"FISCALIZADOR"})
    @DisplayName("TX.1: GET /api/cuotas/listado con FISCALIZADOR devuelve 200 OK")
    void testCuotas_ConFiscalizador_Retorna200() throws Exception {
        mockMvc.perform(get("/api/cuotas/listado"))
                .andExpect(status().isOk());
    }

    // 14. Búsqueda de usuarios sin token -> 401
    @Test
    @DisplayName("TX.1: GET /api/usuarios/buscar sin token devuelve 401 Unauthorized")
    void testUsuariosBuscar_SinToken_Retorna401() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar"))
                .andExpect(status().isUnauthorized());
    }

    // 15. Búsqueda de usuarios con ROLE_USER -> 403
    @Test
    @WithMockUser(username = "pescador", roles = {"USER"})
    @DisplayName("TX.1: GET /api/usuarios/buscar con ROLE_USER devuelve 403 Forbidden")
    void testUsuariosBuscar_ConRoleUser_Retorna403() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar"))
                .andExpect(status().isForbidden());
    }

    // 16. Flujo móvil sin token: /usuario sigue accesible para sincronización offline
    @Test
    @DisplayName("TX.1: GET /usuario sin token sigue accesible para compatibilidad con la app móvil")
    void testAppMovil_UsuarioEndpoint_Permitido() throws Exception {
        mockMvc.perform(get("/usuario"))
                .andExpect(status().isOk());
    }
}
