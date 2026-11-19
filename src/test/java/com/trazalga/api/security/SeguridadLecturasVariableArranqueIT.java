package com.trazalga.api.security;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * YD.2 · Verifica que al arrancar con la propiedad de sistema
 * {@code TRAZALGA_SEGURIDAD_LECTURAS_AUTENTICADAS=false} (Fase 1 del runbook),
 * {@code GET /declaracioncomercializador} y las lecturas legadas de APK 2.7.0
 * responden sin 401.
 */
@AutoConfigureMockMvc
class SeguridadLecturasVariableArranqueIT extends IntegracionBase {

    private static final String PROP_NAME = "TRAZALGA_SEGURIDAD_LECTURAS_AUTENTICADAS";
    private static final String VALOR_PREVIO;

    static {
        VALOR_PREVIO = System.getProperty(PROP_NAME);
        System.setProperty(PROP_NAME, "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @AfterAll
    static void restaurarPropiedadSistema() {
        if (VALOR_PREVIO == null) {
            System.clearProperty(PROP_NAME);
        } else {
            System.setProperty(PROP_NAME, VALOR_PREVIO);
        }
    }

    @Test
    @DisplayName("YD.2: TRAZALGA_SEGURIDAD_LECTURAS_AUTENTICADAS=false deja GET /declaracioncomercializador sin 401")
    void variableLecturasAutenticadasFalse_noDevuelve401() throws Exception {
        mockMvc.perform(get("/declaracioncomercializador").with(anonymous()))
                .andExpect(status().is(not(401)))
                .andExpect(status().isOk());

        // Escenario Fase 1 completo (teléfonos APK 2.7.0): /usuario pre-login y fetch sin token a destinatarios
        configuracionGeneralService.updateConfig("declaracion_lectura_exige_dueno", "false");
        mockMvc.perform(get("/usuario").with(anonymous()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/declaracioncomercializador/usuariosdestinatarios/1").with(anonymous()))
                .andExpect(status().isOk());
    }
}
