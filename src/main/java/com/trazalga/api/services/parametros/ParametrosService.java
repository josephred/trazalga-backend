package com.trazalga.api.services.parametros;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.trazalga.api.services.ConfiguracionGeneralService;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio centralizado de parámetros tipados (TA.2).
 * Expone registros inmutables por dominio evitando el uso de cadenas de texto arbitrarias
 * en los servicios y se invalida automáticamente cuando cambia cualquier configuración.
 */
@Service
@Slf4j
public class ParametrosService {

    @Autowired
    @Lazy
    private ConfiguracionGeneralService configService;

    private final AtomicReference<ParametrosCuota> cuotaCache = new AtomicReference<>();
    private final AtomicReference<ParametrosRetencion> retencionCache = new AtomicReference<>();
    private final AtomicReference<ParametrosVariacion> variacionCache = new AtomicReference<>();
    private final AtomicReference<ParametrosDesembarque> desembarqueCache = new AtomicReference<>();

    public ParametrosCuota cuotas() {
        ParametrosCuota cached = cuotaCache.get();
        if (cached == null) {
            cached = new ParametrosCuota(
                    getBoolean("cuota_cierre_automatico_vencimiento", false),
                    getInt("cuota_dias_gracia_declaracion", 0),
                    getString("cuota_accion_extemporanea", "ALERTA_CRITICA"),
                    getString("cuota_imputacion_armador", "INSCRIPCION"),
                    getDouble("cuota_umbral_restante_pct", 10.0)
            );
            cuotaCache.compareAndSet(null, cached);
        }
        return cached;
    }

    public ParametrosRetencion retencion() {
        ParametrosRetencion cached = retencionCache.get();
        if (cached == null) {
            cached = new ParametrosRetencion(
                    getBoolean("retencion_bloqueo_activo", false),
                    getInt("retencion_bloqueo_humedo_horas", 120),
                    getInt("retencion_bloqueo_semihumedo_horas", 0),
                    getInt("retencion_bloqueo_semiseco_horas", 0),
                    getString("retencion_bloqueo_especies", "TODAS"),
                    getDouble("retencion_bloqueo_preaviso_pct", 80.0),
                    getInt("retencion_humedo_max_horas", 120)
            );
            retencionCache.compareAndSet(null, cached);
        }
        return cached;
    }

    public ParametrosVariacion variacion() {
        ParametrosVariacion cached = variacionCache.get();
        if (cached == null) {
            cached = new ParametrosVariacion(
                    getBoolean("bio_perdida_activo", true),
                    getInt("bio_humedo_dias_minimos_transito", 3),
                    getDouble("bio_humedo_merma_minima_pct", 5.0),
                    getDouble("bio_seco_merma_maxima_pct", 3.0),
                    getBoolean("variacion_peso_alerta_equivalente", false)
            );
            variacionCache.compareAndSet(null, cached);
        }
        return cached;
    }

    public ParametrosDesembarque desembarque() {
        ParametrosDesembarque cached = desembarqueCache.get();
        if (cached == null) {
            cached = new ParametrosDesembarque(
                    getDouble("desembarque_umbral_atipico_kg", 5000.0),
                    getBoolean("desembarque_atipico_estadistico_activo", false),
                    getDouble("desembarque_atipico_sigma", 2.5),
                    getInt("desembarque_atipico_ventana_dias", 90),
                    getInt("desembarque_atipico_min_muestras", 10)
            );
            desembarqueCache.compareAndSet(null, cached);
        }
        return cached;
    }

    /**
     * Invalida el caché en memoria de todos los parámetros tipados.
     */
    public void invalidar() {
        cuotaCache.set(null);
        retencionCache.set(null);
        variacionCache.set(null);
        desembarqueCache.set(null);
        log.debug("Caché de parámetros tipados invalidado exitosamente.");
    }

    private String getString(String clave, String def) {
        if (configService == null) return def;
        return configService.getValor(clave, def);
    }

    private boolean getBoolean(String clave, boolean def) {
        if (configService == null) return def;
        return configService.getBoolean(clave, def);
    }

    private int getInt(String clave, int def) {
        if (configService == null) return def;
        return configService.getInt(clave, def);
    }

    private double getDouble(String clave, double def) {
        if (configService == null) return def;
        return configService.getDouble(clave, def);
    }
}
