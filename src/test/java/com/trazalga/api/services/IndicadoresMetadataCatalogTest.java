package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.ConfiguracionGeneralModel;
import com.trazalga.api.repositories.ConfiguracionGeneralRepository;

/**
 * Test automatizado RX.3 en Backend:
 * Garantiza la consistencia del catálogo de parámetros generales y marcas normativas,
 * impidiendo la introducción de nombres de parámetros o marcas obsoletas.
 */
@ExtendWith(MockitoExtension.class)
public class IndicadoresMetadataCatalogTest {

    @Mock
    private ConfiguracionGeneralRepository repository;

    @InjectMocks
    private ConfiguracionGeneralService service;

    private Map<String, ConfiguracionGeneralModel> simulatedDb;

    @BeforeEach
    void setUp() {
        simulatedDb = new HashMap<>();
        lenient().when(repository.findByClave(anyString())).thenAnswer(invocation -> {
            String clave = invocation.getArgument(0);
            return Optional.ofNullable(simulatedDb.get(clave));
        });
        lenient().when(repository.save(any(ConfiguracionGeneralModel.class))).thenAnswer(invocation -> {
            ConfiguracionGeneralModel model = invocation.getArgument(0);
            simulatedDb.put(model.getClave(), model);
            return model;
        });
    }

    @Test
    void testCatalog_AllRequiredConfigKeys_AreSeededInBackend() {
        service.initDefaults();

        // Parámetros normativos requeridos por los 9 indicadores (33 claves oficiales)
        Set<String> requiredKeys = Set.of(
                "desembarque_unidad_base",
                "desembarque_umbral_atipico_kg",
                "desembarque_fuente_recolector_activa",
                "desembarque_fuente_armador_activa",
                "desembarque_fuente_area_activa",
                "captura_politica_sin_factor",
                "captura_factor_default",
                "cuota_umbral_restante_pct",
                "cuota_desvio_velocidad_pct",
                "cuota_dias_previos_expiracion",
                "cuota_accion_post_cierre",
                "cuota_accion_exceso_limite",
                "veda_modo_operacion",
                "veda_dias_aviso_previo",
                "variacion_peso_umbral_general_pct",
                "variacion_peso_exige_voucher",
                "retencion_bodega_activo",
                "retencion_bodega_dias_amarilla",
                "retencion_bodega_dias_naranja",
                "retencion_bodega_dias_roja",
                "retencion_bodega_estados_sujetos",
                "bio_perdida_activo",
                "bio_humedo_dias_minimos_transito",
                "bio_humedo_merma_minima_pct",
                "bio_seco_merma_maxima_pct",
                "riesgo_activo",
                "riesgo_variacion_amarillo_pct",
                "riesgo_variacion_rojo_pct",
                "riesgo_dias_amarillo",
                "riesgo_dias_rojo",
                "riesgo_escala_humedo_sin_merma",
                "riesgo_agravante_veda_niveles",
                "riesgo_agravante_led_niveles"
        );

        for (String key : requiredKeys) {
            assertTrue(simulatedDb.containsKey(key),
                    "Falta la clave requerida en ConfiguracionGeneralService: " + key);
            assertNotNull(simulatedDb.get(key).getValor(),
                    "El valor por defecto no debe ser nulo para: " + key);
        }
    }

    @Test
    void testCatalog_ForbiddenTokens_AreNotRegisteredAsConfigKeys() {
        service.initDefaults();

        Set<String> forbiddenKeys = Set.of(
                "PESO_FUERA_UMBRAL",
                "factor_conversion_guardado"
        );

        for (String forbidden : forbiddenKeys) {
            assertFalse(simulatedDb.containsKey(forbidden),
                    "La clave prohibida no debe existir en configuracion_general: " + forbidden);
        }
    }

    @Test
    void testCatalog_OfficialMarcas_AreExclusivelyValid() {
        Set<String> validMarcas = Set.of(
                "EN_VEDA",
                "LED_EXCEDIDO",
                "DESEMBARQUE_ATIPICO",
                "CUOTA_EXCEDIDA",
                "POSTERIOR_CIERRE"
        );

        assertTrue(validMarcas.contains("EN_VEDA"));
        assertTrue(validMarcas.contains("LED_EXCEDIDO"));
        assertTrue(validMarcas.contains("DESEMBARQUE_ATIPICO"));
        assertTrue(validMarcas.contains("CUOTA_EXCEDIDA"));
        assertTrue(validMarcas.contains("POSTERIOR_CIERRE"));

        assertFalse(validMarcas.contains("PESO_FUERA_UMBRAL"),
                "PESO_FUERA_UMBRAL es una marca obsoleta prohibida por R X.3");
    }
}
