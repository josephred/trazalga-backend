package com.trazalga.api.services;

import com.trazalga.api.models.ConfiguracionGeneralModel;
import com.trazalga.api.repositories.ConfiguracionGeneralRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ConfiguracionGeneralService {

    @Autowired
    private ConfiguracionGeneralRepository repository;

    @PostConstruct
    public void initDefaults() {
        // Dispositivo / Rastreo
        createIfNotExist("intervalo_rastreo_minutos", "5", "Intervalo en minutos para el registro de ubicación del usuario en la app móvil", "RASTREO");
        createIfNotExist("rastreo_activo", "true", "Habilita o deshabilita globalmente el rastreo de ubicación en la aplicación móvil", "RASTREO");

        // 1. Desembarque
        createIfNotExist("desembarque_unidad_base", "kg", "Unidad de cara al usuario. Bloqueada por norma técnica.", "DESEMBARQUE");
        createIfNotExist("desembarque_umbral_atipico_kg", "5000", "Desembarque individual que dispara auditoría de pesaje", "DESEMBARQUE");
        createIfNotExist("desembarque_fuente_recolector_activa", "true", "Computa declaraciones de recolector", "DESEMBARQUE");
        createIfNotExist("desembarque_fuente_armador_activa", "true", "Computa declaraciones de armador", "DESEMBARQUE");
        createIfNotExist("desembarque_fuente_area_activa", "true", "Computa declaraciones de área", "DESEMBARQUE");

        // 2. Captura
        createIfNotExist("captura_politica_sin_factor", "USAR_DEFAULT", "USAR_DEFAULT o RECHAZAR_DECLARACION cuando no hay factor vigente", "CAPTURA");
        createIfNotExist("captura_factor_default", "1.0000", "Factor de respaldo cuando la política es USAR_DEFAULT", "CAPTURA");

        // 3. Cuotas
        createIfNotExist("cuota_umbral_restante_pct", "10.0", "Porcentaje restante que dispara la alerta preventiva de cuota", "CUOTA");
        createIfNotExist("cuota_desvio_velocidad_pct", "25.0", "Desvío tolerado entre % de consumo y % de tiempo transcurrido", "CUOTA");
        createIfNotExist("cuota_dias_previos_expiracion", "5", "Días antes del fin de vigencia para avisar expiración de cuota", "CUOTA");
        createIfNotExist("cuota_accion_post_cierre", "ALERTA_CRITICA", "Acción ante declaración post cierre administrativo (ALERTA_CRITICA o BLOQUEO_TOTAL)", "CUOTA");
        createIfNotExist("cuota_accion_exceso_limite", "ALERTA_EXCESO", "Acción ante exceso de cuota (ALERTA_EXCESO o BLOQUEO_DECLARACION)", "CUOTA");

        // 4. Vedas
        createIfNotExist("veda_modo_operacion", "BLOQUEO_ESTRICTO", "Modo de operación ante veda (BLOQUEO_ESTRICTO o ALERTA_FISCALIZACION)", "VEDA");
        createIfNotExist("veda_dias_aviso_previo", "7", "Días de anticipación del aviso preventivo de inicio de veda", "VEDA");

        // 5. Cadena de Custodia / Variación de peso y Retención en Bodega (Res. 3602)
        createIfNotExist("variacion_peso_umbral_general_pct", "5.0", "Tolerancia general de variación entre eslabones en porcentaje", "CADENA");
        createIfNotExist("variacion_peso_exige_voucher", "true", "Exige voucher de pesaje en romana para registrar recepción en planta", "CADENA");
        createIfNotExist("retencion_bodega_activo", "true", "Habilita el control de retención en bodega por tramos de humedad", "CADENA");
        createIfNotExist("retencion_humedo_max_horas", "24", "Horas máximas de retención para recurso húmedo (Res. 3602)", "CADENA");
        createIfNotExist("retencion_semihumedo_max_horas", "72", "Horas máximas de retención para recurso semihúmedo (Res. 3602)", "CADENA");
        createIfNotExist("retencion_semiseco_max_horas", "216", "Horas máximas de retención para recurso semiseco (Res. 3602)", "CADENA");
        createIfNotExist("retencion_preaviso_pct", "80", "% del plazo a partir del cual el semáforo es amarillo", "CADENA");

        // Deprecados desde 25-sep (Res. 3602): conservados para retrocompatibilidad
        createIfNotExist("retencion_bodega_dias_amarilla", "3", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.", "CADENA");
        createIfNotExist("retencion_bodega_dias_naranja", "5", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.", "CADENA");
        createIfNotExist("retencion_bodega_dias_roja", "7", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.", "CADENA");
        createIfNotExist("retencion_bodega_estados_sujetos", "HUMEDO", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.", "CADENA");
        updateDescripcionIfExists("retencion_bodega_dias_amarilla", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.");
        updateDescripcionIfExists("retencion_bodega_dias_naranja", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.");
        updateDescripcionIfExists("retencion_bodega_dias_roja", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.");
        updateDescripcionIfExists("retencion_bodega_estados_sujetos", "[DEPRECADO desde 25-sep] Reemplazado por retencion_humedo_max_horas et al.");

        createIfNotExist("bio_perdida_activo", "true", "Habilita el control de merma biológica en tránsito", "CADENA");
        createIfNotExist("bio_humedo_dias_minimos_transito", "3", "Días desde los que se exige evaporación en recurso húmedo", "CADENA");
        createIfNotExist("bio_humedo_merma_minima_pct", "5.0", "Merma mínima esperada en húmedo tras los días mínimos", "CADENA");
        createIfNotExist("bio_seco_merma_maxima_pct", "3.0", "Merma máxima tolerable en recurso seco", "CADENA");

        // INDICADOR 9 — PERFILADOR DE RIESGO DE FISCALIZACIÓN (R9.1)
        createIfNotExist("riesgo_activo", "true", "Interruptor maestro del perfilador de riesgo", "RIESGO");
        createIfNotExist("riesgo_variacion_amarillo_pct", "5.0", "Umbral de variación de peso para riesgo amarillo moderado", "RIESGO");
        createIfNotExist("riesgo_variacion_rojo_pct", "10.0", "Umbral de variación de peso para riesgo rojo severo", "RIESGO");
        createIfNotExist("riesgo_dias_amarillo", "3", "Días de retención en bodega que ameritan seguimiento (amarillo)", "RIESGO");
        createIfNotExist("riesgo_dias_rojo", "7", "Días de retención en bodega para nivel crítico (rojo)", "RIESGO");
        createIfNotExist("riesgo_escala_humedo_sin_merma", "ROJO", "Nivel al que escala la inconsistencia biológica grave", "RIESGO");
        createIfNotExist("riesgo_agravante_veda_niveles", "1", "Niveles que incrementa una marca activa EN_VEDA", "RIESGO");
        createIfNotExist("riesgo_agravante_led_niveles", "1", "Niveles que incrementa una marca activa LED_EXCEDIDO", "RIESGO");

        // Bloqueo de Carga en Bodega Virtual por Marcas (Punto 4 / T4.1)
        createIfNotExist("marcas_bloqueantes_carga", "LED_EXCEDIDO", "Marcas que bloquean el despacho de carga en bodega virtual", "CADENA");
        createIfNotExist("bloqueo_carga_activo", "true", "Habilita el bloqueo de carga en bodega por marcas activas", "CADENA");

        // INDICADOR 8 — DOBLE OPERACIÓN / INCONSISTENCIA GEOTEMPORAL (T8.1)
        createIfNotExist("doble_op_activo", "true", "Habilita la detección geotemporal de doble operación", "INTEGRIDAD");
        createIfNotExist("doble_op_distancia_min_km", "5", "Distancia mínima para considerar inconsistencia geográfica", "INTEGRIDAD");
        createIfNotExist("doble_op_velocidad_max_kmh", "80", "Velocidad máxima plausible entre dos declaraciones", "INTEGRIDAD");
        createIfNotExist("doble_op_ventana_min_minutos", "30", "Ventana temporal mínima entre declaraciones", "INTEGRIDAD");

        // INDICADOR 9 — ORIGEN REAL VS GEOLOCALIZACIÓN GPS (T9.3, T9.4)
        createIfNotExist("origen_geo_activo", "true", "Habilita la detección de inconsistencia GPS vs origen", "INTEGRIDAD");
        createIfNotExist("origen_geo_distancia_max_km", "30", "Distancia máxima tolerable entre GPS y referencia", "INTEGRIDAD");
        createIfNotExist("origen_geo_precision_max_m", "500", "Precisión GPS mínima aceptable para marcar", "INTEGRIDAD");
        createIfNotExist("origen_geo_patron_pct", "50", "% de declaraciones lejanas para detectar patrón", "INTEGRIDAD");
        createIfNotExist("origen_geo_patron_min_decl", "3", "Mínimo de declaraciones para evaluar patrón", "INTEGRIDAD");
    }

    private void createIfNotExist(String clave, String valorDefecto, String descripcion, String categoria) {
        Optional<ConfiguracionGeneralModel> opt = repository.findByClave(clave);
        if (opt.isEmpty()) {
            repository.save(ConfiguracionGeneralModel.builder()
                    .clave(clave)
                    .valor(valorDefecto)
                    .descripcion(descripcion)
                    .categoria(categoria)
                    .build());
        } else {
            ConfiguracionGeneralModel existing = opt.get();
            if (existing.getCategoria() == null && categoria != null) {
                existing.setCategoria(categoria);
                repository.save(existing);
            }
        }
    }

    private void updateDescripcionIfExists(String clave, String descripcion) {
        Optional<ConfiguracionGeneralModel> opt = repository.findByClave(clave);
        if (opt.isPresent()) {
            ConfiguracionGeneralModel existing = opt.get();
            if (existing.getDescripcion() == null || !existing.getDescripcion().equals(descripcion)) {
                existing.setDescripcion(descripcion);
                repository.save(existing);
            }
        }
    }

    public List<ConfiguracionGeneralModel> getAll() {
        return repository.findAll();
    }

    public List<ConfiguracionGeneralModel> getByCategoria(String categoria) {
        return repository.findByCategoria(categoria);
    }

    public Optional<ConfiguracionGeneralModel> getByClave(String clave) {
        return repository.findByClave(clave);
    }

    public String getValor(String clave, String valorDefecto) {
        return repository.findByClave(clave)
                .map(ConfiguracionGeneralModel::getValor)
                .orElse(valorDefecto);
    }

    public double getDouble(String clave, double valorDefecto) {
        String val = getValor(clave, null);
        if (val == null) return valorDefecto;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return valorDefecto;
        }
    }

    public int getInt(String clave, int valorDefecto) {
        String val = getValor(clave, null);
        if (val == null) return valorDefecto;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return valorDefecto;
        }
    }

    public boolean getBoolean(String clave, boolean valorDefecto) {
        String val = getValor(clave, null);
        if (val == null) return valorDefecto;
        return Boolean.parseBoolean(val);
    }

    @Autowired(required = false)
    private ConfiguracionAuditoriaService auditoriaService;

    @Autowired(required = false)
    private org.springframework.cache.CacheManager cacheManager;

    public ConfiguracionGeneralModel updateConfig(String clave, String nuevoValor) {
        return updateConfig(clave, nuevoValor, null, null);
    }

    public ConfiguracionGeneralModel updateConfig(String clave, String nuevoValor, String motivo, com.trazalga.api.models.UsuarioModel usuario) {
        ConfiguracionGeneralModel config = repository.findByClave(clave)
                .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada: " + clave));
        String valorAnterior = config.getValor();
        config.setValor(nuevoValor);
        ConfiguracionGeneralModel saved = repository.save(config);

        if (auditoriaService != null && (valorAnterior == null || !valorAnterior.equals(nuevoValor))) {
            try {
                auditoriaService.registrar(
                        "CONFIGURACION_GENERAL",
                        clave,
                        "valor",
                        valorAnterior,
                        nuevoValor,
                        motivo,
                        usuario);
            } catch (Exception ignored) {
            }
        }

        if (cacheManager != null) {
            try {
                for (String cacheName : cacheManager.getCacheNames()) {
                    org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return saved;
    }
}
