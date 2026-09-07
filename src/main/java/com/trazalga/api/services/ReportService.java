package com.trazalga.api.services;

import com.trazalga.api.config.CacheConfig;
import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.repositories.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Servicio de reportes.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * SOBRE LAS ANOTACIONES @Cacheable
 *
 * Las pruebas de carga con 500.000 registros midieron que un reporte cuesta
 * ~1,14 s de CPU. Con 2 cores y un 20% de reportes en el tráfico, eso deja el
 * techo del sistema en ~7 peticiones/segundo. Las consultas examinan entre
 * 173.000 y 500.000 filas para devolver 1 a 4 filas de agregados
 * (`performance_schema`, evidencia H1).
 *
 * Ese ratio no se corrige con índices: para sumar hay que leer las filas. La
 * salida es no volver a leerlas. Ver CacheConfig para el TTL y su
 * justificación.
 *
 * POR QUÉ LA CLAVE DE CACHÉ FUNCIONA
 * Los controllers parsean las fechas con
 * `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`, que produce un Date
 * truncado a medianoche. Dos peticiones con la misma fecha generan Dates
 * `equals()`, así que aciertan en el caché.
 *
 * ⚠️ SI ALGUIEN CAMBIA ESE BINDING A TIMESTAMP, EL CACHÉ DEJA DE SERVIR
 *    SILENCIOSAMENTE: cada milisegundo distinto sería una clave nueva, el hit
 *    ratio caería a cero y nadie se enteraría porque todo seguiría
 *    funcionando, solo más lento. Vigilar el hit ratio en
 *    /actuator/metrics/cache.gets es la forma de detectarlo.
 *
 * QUÉ NO SE CACHEA Y POR QUÉ
 * `getReport(...)` recibe un `rut`, así que su clave incluye al usuario. No
 * está cacheado a propósito: multiplicaría las entradas por el número de
 * usuarios sin que se repitan las claves.
 * ─────────────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    /**
     * Reporte por usuario. SIN caché: la clave incluye el rut y no se repite
     * lo suficiente para que el caché aporte.
     */
    public List<ReportDTO> getReport(Date fechaInicio, Date fechaFin, Integer tipoReporte, String rut) {
        return reportRepository.generateReport(fechaInicio, fechaFin, tipoReporte, rut);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getIndicadoresRecolector(Date startDate, Date endDate) {
        return reportRepository.getIndicadoresRecolector(startDate, endDate);
    }

    /** Medido: 4.251 ms de media, 173.221 filas examinadas por ejecución. */
    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getExtraccionVedaMetrics(Date startDate, Date endDate) {
        return reportRepository.getExtraccionVedaMetrics(startDate, endDate);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate) {
        return reportRepository.getExtraccionVedaDetalle(startDate, endDate);
    }

    /**
     * El caso más caro del sistema: 20,9 s medidos, compuestos en Java por
     * tres reportes completos en cascada (2,0 s de base UNION ALL + 4,8 s de
     * extracción en veda + 14,0 s de variación de peso).
     *
     * Cachear aquí captura la cascada COMPLETA en una sola entrada. Es la
     * razón por la que conviene medir el efecto del caché antes de invertir
     * en descomponer la cascada: puede que deje de ser el cuello.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getResumenGlobal(Date startDate, Date endDate) {
        return reportRepository.getResumenGlobal(startDate, endDate);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getCasosAbiertosDetalle(Date startDate, Date endDate) {
        return reportRepository.getCasosAbiertosDetalle(startDate, endDate);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getDobleOperacion(Date startDate, Date endDate, Double toleranciaPct) {
        return reportRepository.getDobleOperacion(startDate, endDate, toleranciaPct);
    }

    /** Medido: 4.935 ms de media, 392.876 filas examinadas para devolver 4. */
    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getVolumenPorEspecie(Date startDate, Date endDate, String perfil) {
        return reportRepository.getVolumenPorEspecie(startDate, endDate, perfil);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getCurvaSnake(Date startDate, Date endDate, Long amerbId, Long especieId) {
        return reportRepository.getCurvaSnake(startDate, endDate, amerbId, especieId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getTiempoValidacionMetrics(Date startDate, Date endDate) {
        return reportRepository.getTiempoValidacionMetrics(startDate, endDate);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getTiempoValidacionDetalle(Date startDate, Date endDate) {
        return reportRepository.getTiempoValidacionDetalle(startDate, endDate);
    }

    /** El componente más caro de la cascada de resumen-global: 14,0 s medidos. */
    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getVariacionPesoMetrics(Date startDate, Date endDate, Double umbralPct) {
        return reportRepository.getVariacionPesoMetrics(startDate, endDate, umbralPct);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getVariacionPesoDetalle(Date startDate, Date endDate) {
        return reportRepository.getVariacionPesoDetalle(startDate, endDate);
    }

    /**
     * Trazabilidad. Recorre el grafo de declaraciones con un BFS que hace
     * una consulta por nodo, así que el coste crece con el tamaño de la
     * cadena, no con el volumen de la tabla.
     *
     * ⚠️ Sigue SIN MEDIRSE bajo carga: el escenario k6 enviaba el `tipo` como
     *    texto cuando el controller espera un Integer, así que las peticiones
     *    morían en el binding de Spring. Corregido en el script, pendiente de
     *    ejecutar. El caché aquí es preventivo, no está justificado por una
     *    medición.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_TRAZABILIDAD)
    public com.trazalga.api.dto.TrazabilidadResponseDTO getTrazabilidad(Integer tipo, Long id) {
        return reportRepository.getTrazabilidad(tipo, id);
    }

    // =========================================================================
    // FASE 4 — INDICADORES Y TRAZABILIDAD POR LOTE (folio_origen)
    // =========================================================================

    private final ConfiguracionGeneralService configService;

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getTrazabilidadLoteMetrics(Date startDate, Date endDate, Double umbralVariacion) {
        double umbral = umbralVariacion != null ? umbralVariacion : configService.getDouble("variacion_peso_umbral_general_pct", 5.0);
        double mermaMinHumedo = configService.getDouble("bio_humedo_merma_minima_pct", 5.0);
        double mermaMaxSeco = configService.getDouble("bio_seco_merma_maxima_pct", 3.0);
        int diasMinHumedo = configService.getInt("bio_humedo_dias_minimos_transito", 3);
        int diasAmarilla = configService.getInt("retencion_bodega_dias_amarilla", 3);
        int diasNaranja = configService.getInt("retencion_bodega_dias_naranja", 5);
        int diasRoja = configService.getInt("retencion_bodega_dias_roja", 7);
        String estadosSujetos = configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO");

        return reportRepository.getTrazabilidadLoteMetrics(
                startDate, endDate, umbral, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getTrazabilidadLoteDetalle(Date startDate, Date endDate, String semaforo) {
        double umbral = configService.getDouble("variacion_peso_umbral_general_pct", 5.0);
        double mermaMinHumedo = configService.getDouble("bio_humedo_merma_minima_pct", 5.0);
        double mermaMaxSeco = configService.getDouble("bio_seco_merma_maxima_pct", 3.0);
        int diasMinHumedo = configService.getInt("bio_humedo_dias_minimos_transito", 3);
        int diasAmarilla = configService.getInt("retencion_bodega_dias_amarilla", 3);
        int diasNaranja = configService.getInt("retencion_bodega_dias_naranja", 5);
        int diasRoja = configService.getInt("retencion_bodega_dias_roja", 7);
        String estadosSujetos = configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO");

        return reportRepository.getTrazabilidadLoteDetalle(
                startDate, endDate, semaforo, umbral, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getDesembarqueFisicoMetrics(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId, String perfil) {
        double umbralAtipico = configService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
        boolean fRecolector = configService.getBoolean("desembarque_fuente_recolector_activa", true);
        boolean fArmador = configService.getBoolean("desembarque_fuente_armador_activa", true);
        boolean fArea = configService.getBoolean("desembarque_fuente_area_activa", true);

        return reportRepository.getDesembarqueFisicoMetrics(
                startDate, endDate, especieId, comunaId, regionId, perfil,
                umbralAtipico, fRecolector, fArmador, fArea);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getDesembarqueFisicoDetalle(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId, String perfil) {
        double umbralAtipico = configService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
        boolean fRecolector = configService.getBoolean("desembarque_fuente_recolector_activa", true);
        boolean fArmador = configService.getBoolean("desembarque_fuente_armador_activa", true);
        boolean fArea = configService.getBoolean("desembarque_fuente_area_activa", true);

        return reportRepository.getDesembarqueFisicoDetalle(
                startDate, endDate, especieId, comunaId, regionId, perfil,
                umbralAtipico, fRecolector, fArmador, fArea);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getCapturaCorregidaMetrics(Date startDate, Date endDate, Long especieId) {
        return reportRepository.getCapturaCorregidaMetrics(startDate, endDate, especieId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getLimiteExtraccionDiarioMetrics(Date fecha) {
        return reportRepository.getLimiteExtraccionDiarioMetrics(fecha);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getRetencionBodegaMetrics() {
        int diasAmarilla = configService.getInt("retencion_bodega_dias_amarilla", 3);
        int diasNaranja = configService.getInt("retencion_bodega_dias_naranja", 5);
        int diasRoja = configService.getInt("retencion_bodega_dias_roja", 7);
        String estadosSujetos = configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO");

        return reportRepository.getRetencionBodegaMetrics(diasAmarilla, diasNaranja, diasRoja, estadosSujetos);
    }
}
