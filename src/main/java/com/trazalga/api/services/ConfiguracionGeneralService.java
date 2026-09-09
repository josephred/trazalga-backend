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

        // 5. Cadena de Custodia / Variación de peso
        createIfNotExist("variacion_peso_umbral_general_pct", "5.0", "Tolerancia general de variación entre eslabones en porcentaje", "CADENA");
        createIfNotExist("retencion_bodega_activo", "true", "Habilita el control de días de retención en bodega", "CADENA");
        createIfNotExist("retencion_bodega_dias_amarilla", "3", "Días de retención para alerta amarilla preventiva", "CADENA");
        createIfNotExist("retencion_bodega_dias_naranja", "5", "Días de retención para alerta naranja crítica", "CADENA");
        createIfNotExist("retencion_bodega_dias_roja", "7", "Plazo máximo recomendado de retención en días", "CADENA");
        createIfNotExist("retencion_bodega_estados_sujetos", "HUMEDO", "Estados de humedad sujetos al control de retención (separados por coma)", "CADENA");
        createIfNotExist("bio_perdida_activo", "true", "Habilita el control de merma biológica en tránsito", "CADENA");
        createIfNotExist("bio_humedo_dias_minimos_transito", "3", "Días desde los que se exige evaporación en recurso húmedo", "CADENA");
        createIfNotExist("bio_humedo_merma_minima_pct", "5.0", "Merma mínima esperada en húmedo tras los días mínimos", "CADENA");
        createIfNotExist("bio_seco_merma_maxima_pct", "3.0", "Merma máxima tolerable en recurso seco", "CADENA");
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
    private org.springframework.cache.CacheManager cacheManager;

    public ConfiguracionGeneralModel updateConfig(String clave, String nuevoValor) {
        ConfiguracionGeneralModel config = repository.findByClave(clave)
                .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada: " + clave));
        config.setValor(nuevoValor);
        ConfiguracionGeneralModel saved = repository.save(config);

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
