package com.trazalga.api.services.parametros;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.indicadores.CoherenciaHumedad;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio centralizado de parámetros tipados (TA.2 / TH.1).
 * Expone registros inmutables por dominio evitando el uso de cadenas de texto arbitrarias
 * en los servicios y se invalida automáticamente cuando cambia cualquier configuración.
 */
@Service
@Slf4j
public class ParametrosService {

    // Nombres canónicos de parámetros normativos
    public static final String PARAM_DESEMBARQUE_ATIPICO_SIGMA = "desembarque_atipico_sigma";
    public static final String PARAM_DESEMBARQUE_UMBRAL_KG = "desembarque_umbral_atipico_kg";
    public static final String PARAM_DESEMBARQUE_ATIPICO_ESTADISTICO_ACTIVO = "desembarque_atipico_estadistico_activo";
    public static final String PARAM_DESEMBARQUE_ATIPICO_VENTANA_DIAS = "desembarque_atipico_ventana_dias";
    public static final String PARAM_DESEMBARQUE_ATIPICO_MIN_MUESTRAS = "desembarque_atipico_min_muestras";

    public static final String PARAM_RETENCION_BLOQUEO_HUMEDO = "retencion_bloqueo_humedo_horas";
    public static final String PARAM_RETENCION_BLOQUEO_SEMIHUMEDO = "retencion_bloqueo_semihumedo_horas";
    public static final String PARAM_RETENCION_BLOQUEO_SEMISECO = "retencion_bloqueo_semiseco_horas";
    public static final String PARAM_RETENCION_BODEGA_ACTIVO = "retencion_bodega_activo";
    public static final String PARAM_RETENCION_HUMEDO_MAX_HORAS = "retencion_humedo_max_horas";
    public static final String PARAM_RETENCION_SEMIHUMEDO_MAX_HORAS = "retencion_semihumedo_max_horas";
    public static final String PARAM_RETENCION_SEMISECO_MAX_HORAS = "retencion_semiseco_max_horas";
    public static final String PARAM_RETENCION_PREAVISO_PCT = "retencion_preaviso_pct";
    public static final String PARAM_RETENCION_BLOQUEO_ACTIVO = "retencion_bloqueo_activo";
    public static final String PARAM_RETENCION_BLOQUEO_ESPECIES = "retencion_bloqueo_especies";
    public static final String PARAM_RETENCION_BLOQUEO_PREAVISO_PCT = "retencion_bloqueo_preaviso_pct";
    public static final String PARAM_RETENCION_BODEGA_ESTADOS_SUJETOS = "retencion_bodega_estados_sujetos";
    public static final String PARAM_RETENCION_BODEGA_DIAS_AMARILLA = "retencion_bodega_dias_amarilla";
    public static final String PARAM_RETENCION_BODEGA_DIAS_NARANJA = "retencion_bodega_dias_naranja";
    public static final String PARAM_RETENCION_BODEGA_DIAS_ROJA = "retencion_bodega_dias_roja";

    // TH.1 / TH.2: Coherencia de humedad (Res. 3602, art. 1)
    public static final String PARAM_HUMEDAD_COHERENCIA_ACTIVO = "humedad_coherencia_activo";
    public static final String PARAM_HUMEDAD_COHERENCIA_MODO = "humedad_coherencia_modo";
    public static final String PARAM_HUMEDAD_EDAD_MAX_HUMEDO_HORAS = "humedad_edad_max_humedo_horas";
    public static final String PARAM_HUMEDAD_EDAD_MAX_SEMIHUMEDO_HORAS = "humedad_edad_max_semihumedo_horas";
    public static final String PARAM_HUMEDAD_EDAD_MAX_SEMISECO_HORAS = "humedad_edad_max_semiseco_horas";

    public static final String PARAM_RIESGO_VARIACION_ROJO_PCT = "riesgo_variacion_rojo_pct";
    public static final String PARAM_RIESGO_VARIACION_AMARILLO_PCT = "riesgo_variacion_amarillo_pct";

    public static final String PARAM_BIO_PERDIDA_ACTIVO = "bio_perdida_activo";
    public static final String PARAM_BIO_HUMEDO_DIAS_MINIMOS_TRANSITO = "bio_humedo_dias_minimos_transito";
    public static final String PARAM_BIO_HUMEDO_MERMA_MINIMA_PCT = "bio_humedo_merma_minima_pct";
    public static final String PARAM_BIO_SECO_MERMA_MAXIMA_PCT = "bio_seco_merma_maxima_pct";

    // Refinamiento 23-10 · punto 1: imputación cruzada por veda (barreteado en veda -> cuota de varado)
    public static final String PARAM_CUOTA_IMPUTACION_VEDA_ACTIVO = "cuota_imputacion_veda_activo";
    public static final String PARAM_CUOTA_IMPUTACION_VEDA_METODO_ORIGEN = "cuota_imputacion_veda_metodo_origen";
    public static final String PARAM_CUOTA_IMPUTACION_VEDA_METODO_DESTINO = "cuota_imputacion_veda_metodo_destino";

    // Refinamiento 23-10 · punto 3: Subreporte Huiro Palo (barreteado sin fronda × % de fronda IFOP)
    public static final String PARAM_SUBREPORTE_HUIRO_PALO_ACTIVO = "subreporte_huiro_palo_activo";
    public static final String PARAM_SUBREPORTE_HUIRO_PALO_ESPECIE = "subreporte_huiro_palo_especie";
    public static final String PARAM_SUBREPORTE_HUIRO_PALO_METODO = "subreporte_huiro_palo_metodo";
    public static final String PARAM_SUBREPORTE_HUIRO_PALO_COMPOSICION = "subreporte_huiro_palo_composicion";
    public static final String PARAM_SUBREPORTE_HUIRO_PALO_FACTOR_PCT = "subreporte_huiro_palo_factor_pct";
    public static final String PARAM_SUBREPORTE_HUIRO_PALO_FORMULA = "subreporte_huiro_palo_formula";

    @Autowired
    @Lazy
    private ConfiguracionGeneralService configService;

    public ParametrosService() {}

    public ParametrosService(ConfiguracionGeneralService configService) {
        this.configService = configService;
    }

    private final AtomicReference<ParametrosCuota> cuotaCache = new AtomicReference<>();
    private final AtomicReference<ParametrosRetencion> retencionCache = new AtomicReference<>();
    private final AtomicReference<ParametrosVariacion> variacionCache = new AtomicReference<>();
    private final AtomicReference<ParametrosDesembarque> desembarqueCache = new AtomicReference<>();
    private final AtomicReference<CoherenciaHumedad.ParametrosCoherencia> coherenciaHumedadCache = new AtomicReference<>();
    private final AtomicReference<ParametrosImputacionVeda> imputacionVedaCache = new AtomicReference<>();
    private final AtomicReference<ParametrosSubreporteHuiroPalo> subreporteHuiroPaloCache = new AtomicReference<>();

    /**
     * Subreporte Huiro Palo (refinamiento 23-10, punto 3): estima los kilos de fronda que no quedan
     * declarados cuando el huiro palo barreteado se declara "sin fronda".
     */
    public ParametrosSubreporteHuiroPalo subreporteHuiroPalo() {
        ParametrosSubreporteHuiroPalo cached = subreporteHuiroPaloCache.get();
        if (cached == null) {
            ParametrosSubreporteHuiroPalo def = ParametrosSubreporteHuiroPalo.porDefecto();
            cached = new ParametrosSubreporteHuiroPalo(
                    getBoolean(PARAM_SUBREPORTE_HUIRO_PALO_ACTIVO, def.activo()),
                    getString(PARAM_SUBREPORTE_HUIRO_PALO_ESPECIE, def.especie()),
                    getString(PARAM_SUBREPORTE_HUIRO_PALO_METODO, def.metodo()),
                    getString(PARAM_SUBREPORTE_HUIRO_PALO_COMPOSICION, def.composicion()),
                    getDouble(PARAM_SUBREPORTE_HUIRO_PALO_FACTOR_PCT, def.factorPct()),
                    getString(PARAM_SUBREPORTE_HUIRO_PALO_FORMULA, def.formula())
            );
            subreporteHuiroPaloCache.compareAndSet(null, cached);
        }
        return cached;
    }

    /**
     * Imputación cruzada por veda (refinamiento 23-10, punto 1): un método declarado en veda y sin
     * cuota propia se imputa a la cuota del método de destino de la misma especie, comuna y período.
     */
    public ParametrosImputacionVeda imputacionVeda() {
        ParametrosImputacionVeda cached = imputacionVedaCache.get();
        if (cached == null) {
            cached = new ParametrosImputacionVeda(
                    getBoolean(PARAM_CUOTA_IMPUTACION_VEDA_ACTIVO, true),
                    getString(PARAM_CUOTA_IMPUTACION_VEDA_METODO_ORIGEN, "BARRETEADO"),
                    getString(PARAM_CUOTA_IMPUTACION_VEDA_METODO_DESTINO, "VARADO")
            );
            imputacionVedaCache.compareAndSet(null, cached);
        }
        return cached;
    }

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
                    getBoolean(PARAM_RETENCION_BODEGA_ACTIVO, true),
                    getInt(PARAM_RETENCION_HUMEDO_MAX_HORAS, 24),
                    getInt(PARAM_RETENCION_SEMIHUMEDO_MAX_HORAS, 72),
                    getInt(PARAM_RETENCION_SEMISECO_MAX_HORAS, 216),
                    getInt(PARAM_RETENCION_PREAVISO_PCT, 80),
                    getBoolean(PARAM_RETENCION_BLOQUEO_ACTIVO, false),
                    getInt(PARAM_RETENCION_BLOQUEO_HUMEDO, 120),
                    getInt(PARAM_RETENCION_BLOQUEO_SEMIHUMEDO, 0),
                    getInt(PARAM_RETENCION_BLOQUEO_SEMISECO, 0),
                    getString(PARAM_RETENCION_BLOQUEO_ESPECIES, "TODAS"),
                    getInt(PARAM_RETENCION_BLOQUEO_PREAVISO_PCT, 80)
            );
            retencionCache.compareAndSet(null, cached);
        }
        return cached;
    }

    public CoherenciaHumedad.ParametrosCoherencia coherenciaHumedad() {
        CoherenciaHumedad.ParametrosCoherencia cached = coherenciaHumedadCache.get();
        if (cached == null) {
            cached = CoherenciaHumedad.ParametrosCoherencia.builder()
                    .activo(getBoolean(PARAM_HUMEDAD_COHERENCIA_ACTIVO, false))
                    .modo(getString(PARAM_HUMEDAD_COHERENCIA_MODO, CoherenciaHumedad.MODO_DIRECCIONAL))
                    .edadMaxHumedoHoras(getInt(PARAM_HUMEDAD_EDAD_MAX_HUMEDO_HORAS, 24))
                    .edadMaxSemihumedoHoras(getInt(PARAM_HUMEDAD_EDAD_MAX_SEMIHUMEDO_HORAS, 72))
                    .edadMaxSemisecoHoras(getInt(PARAM_HUMEDAD_EDAD_MAX_SEMISECO_HORAS, 216))
                    .build();
            coherenciaHumedadCache.compareAndSet(null, cached);
        }
        return cached;
    }

    public ParametrosVariacion variacion() {
        ParametrosVariacion cached = variacionCache.get();
        if (cached == null) {
            cached = new ParametrosVariacion(
                    getBoolean(PARAM_BIO_PERDIDA_ACTIVO, true),
                    getInt(PARAM_BIO_HUMEDO_DIAS_MINIMOS_TRANSITO, 3),
                    getDouble(PARAM_BIO_HUMEDO_MERMA_MINIMA_PCT, 5.0),
                    getDouble(PARAM_BIO_SECO_MERMA_MAXIMA_PCT, 3.0),
                    getBoolean("variacion_peso_alerta_equivalente", false),
                    getDouble("variacion_peso_umbral_general_pct", 5.0),
                    getDouble(PARAM_RIESGO_VARIACION_ROJO_PCT, 10.0),
                    getDouble(PARAM_RIESGO_VARIACION_AMARILLO_PCT, 5.0)
            );
            variacionCache.compareAndSet(null, cached);
        }
        return cached;
    }

    public ParametrosDesembarque desembarque() {
        ParametrosDesembarque cached = desembarqueCache.get();
        if (cached == null) {
            cached = new ParametrosDesembarque(
                    getDouble(PARAM_DESEMBARQUE_UMBRAL_KG, 5000.0),
                    getBoolean(PARAM_DESEMBARQUE_ATIPICO_ESTADISTICO_ACTIVO, false),
                    getDouble(PARAM_DESEMBARQUE_ATIPICO_SIGMA, 2.5),
                    getInt(PARAM_DESEMBARQUE_ATIPICO_VENTANA_DIAS, 90),
                    getInt(PARAM_DESEMBARQUE_ATIPICO_MIN_MUESTRAS, 10)
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
        coherenciaHumedadCache.set(null);
        imputacionVedaCache.set(null);
        subreporteHuiroPaloCache.set(null);
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
