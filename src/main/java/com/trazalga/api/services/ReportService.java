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
        return getExtraccionVedaMetrics(startDate, endDate, null, null);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getExtraccionVedaMetrics(Date startDate, Date endDate, Long especieId, Long regionId) {
        return reportRepository.getExtraccionVedaMetrics(startDate, endDate, especieId, regionId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate) {
        return getExtraccionVedaDetalle(startDate, endDate, null, null);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate, Long especieId, Long regionId) {
        return reportRepository.getExtraccionVedaDetalle(startDate, endDate, especieId, regionId);
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

    // =========================================================================
    // INDICADOR 8 — DETECCIÓN GEOTEMPORAL DE DOBLE OPERACIÓN (Res. 25-sep / T8.1)
    // =========================================================================

    public java.util.Map<String, Object> getDobleOperacionGeo(Date startDate, Date endDate, Long regionId) {
        boolean activo = configService.getBoolean("doble_op_activo", true);
        double distanciaMinKm = configService.getDouble("doble_op_distancia_min_km", 5.0);
        double velocidadMaxKmh = configService.getDouble("doble_op_velocidad_max_kmh", 80.0);
        int ventanaMinMinutos = configService.getInt("doble_op_ventana_min_minutos", 30);

        if (!activo) {
            java.util.Map<String, Object> disabled = new java.util.LinkedHashMap<>();
            disabled.put("activo", false);
            disabled.put("hallazgos", java.util.Collections.emptyList());
            disabled.put("sinGeolocalizacion", 0L);
            disabled.put("totalDeclaraciones", 0);
            disabled.put("totalHallazgos", 0);
            disabled.put("parametros", java.util.Map.of(
                "distanciaMinKm", distanciaMinKm,
                "velocidadMaxKmh", velocidadMaxKmh,
                "ventanaMinMinutos", ventanaMinMinutos
            ));
            return disabled;
        }

        List<java.util.Map<String, Object>> filas = reportRepository.getDeclaracionesConGeo(startDate, endDate, regionId);
        long sinGeo = reportRepository.contarDeclaracionesSinGeo(startDate, endDate, regionId);

        // Agrupar por usuario_id preservando orden cronológico
        java.util.Map<Long, List<java.util.Map<String, Object>>> porUsuario = new java.util.LinkedHashMap<>();
        for (java.util.Map<String, Object> f : filas) {
            Long uId = (Long) f.get("usuarioId");
            if (uId != null) {
                porUsuario.computeIfAbsent(uId, k -> new java.util.ArrayList<>()).add(f);
            }
        }

        List<java.util.Map<String, Object>> hallazgos = new java.util.ArrayList<>();

        for (List<java.util.Map<String, Object>> decls : porUsuario.values()) {
            decls.sort((d1, d2) -> {
                java.time.LocalDateTime t1 = parseTimestamp((Date) d1.get("fechaDeclaracion"), (String) d1.get("hora"));
                java.time.LocalDateTime t2 = parseTimestamp((Date) d2.get("fechaDeclaracion"), (String) d2.get("hora"));
                if (t1 == null || t2 == null) return 0;
                return t1.compareTo(t2);
            });

            for (int i = 0; i < decls.size() - 1; i++) {
                java.util.Map<String, Object> a = decls.get(i);
                java.util.Map<String, Object> b = decls.get(i + 1);

                Double latA = (Double) a.get("latitud");
                Double lonA = (Double) a.get("longitud");
                Double latB = (Double) b.get("latitud");
                Double lonB = (Double) b.get("longitud");

                if (latA == null || lonA == null || latB == null || lonB == null) continue;

                java.time.LocalDateTime tsA = parseTimestamp((Date) a.get("fechaDeclaracion"), (String) a.get("hora"));
                java.time.LocalDateTime tsB = parseTimestamp((Date) b.get("fechaDeclaracion"), (String) b.get("hora"));

                if (tsA == null || tsB == null) continue;

                double d = haversineKm(latA, lonA, latB, lonB);
                double deltaMillis = Math.abs(java.time.Duration.between(tsA, tsB).toMillis());
                double deltaHoras = deltaMillis / (3600.0 * 1000.0);
                double deltaMinutos = deltaMillis / (60.0 * 1000.0);
                double v = deltaHoras > 0 ? (d / deltaHoras) : (d > 0 ? 9999.0 : 0.0);

                boolean hayHallazgo = d >= distanciaMinKm &&
                        (v > velocidadMaxKmh || deltaMinutos < ventanaMinMinutos);

                if (hayHallazgo) {
                    java.util.Map<String, Object> h = new java.util.LinkedHashMap<>();
                    h.put("usuarioId", a.get("usuarioId"));
                    h.put("usuarioNombre", a.get("usuarioNombre"));
                    h.put("usuarioRut", a.get("usuarioRut"));
                    h.put("distanciaKm", Math.round(d * 100.0) / 100.0);
                    h.put("tiempoHoras", Math.round(deltaHoras * 100.0) / 100.0);
                    h.put("tiempoMinutos", Math.round(deltaMinutos));
                    h.put("velocidadKmh", Math.round(v * 10.0) / 10.0);
                    h.put("motivo", String.format(java.util.Locale.US,
                            "Distancia de %.1f km recorrida en %.0f min (velocidad implícita %.1f km/h)",
                            d, deltaMinutos, v));

                    java.util.Map<String, Object> puntoA = new java.util.LinkedHashMap<>(a);
                    puntoA.put("timestamp", tsA.toString());
                    h.put("declaracionA", puntoA);

                    java.util.Map<String, Object> puntoB = new java.util.LinkedHashMap<>(b);
                    puntoB.put("timestamp", tsB.toString());
                    h.put("declaracionB", puntoB);

                    hallazgos.add(h);
                }
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("activo", true);
        result.put("hallazgos", hallazgos);
        result.put("totalHallazgos", hallazgos.size());
        result.put("sinGeolocalizacion", sinGeo);
        result.put("totalDeclaraciones", filas.size());
        result.put("parametros", java.util.Map.of(
            "distanciaMinKm", distanciaMinKm,
            "velocidadMaxKmh", velocidadMaxKmh,
            "ventanaMinMinutos", ventanaMinMinutos
        ));

        return result;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0; // radio de la Tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static java.time.LocalDateTime parseTimestamp(Date fecha, String horaStr) {
        if (fecha == null) return null;
        java.time.LocalDate localDate;
        if (fecha instanceof java.sql.Date) {
            localDate = ((java.sql.Date) fecha).toLocalDate();
        } else {
            localDate = fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        java.time.LocalTime localTime = java.time.LocalTime.MIDNIGHT;
        if (horaStr != null && !horaStr.trim().isEmpty()) {
            try {
                String clean = horaStr.trim();
                if (clean.length() == 5) {
                    localTime = java.time.LocalTime.parse(clean);
                } else if (clean.length() >= 8) {
                    localTime = java.time.LocalTime.parse(clean.substring(0, 8));
                } else {
                    String[] parts = clean.split(":");
                    int h = Integer.parseInt(parts[0]);
                    int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    int s = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                    localTime = java.time.LocalTime.of(h, m, s);
                }
            } catch (Exception ignored) {
                localTime = java.time.LocalTime.MIDNIGHT;
            }
        }
        return java.time.LocalDateTime.of(localDate, localTime);
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

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getCadenaOrigenPlanta(Date startDate, Date endDate) {
        return reportRepository.getCadenaOrigenPlanta(startDate, endDate);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getCadenaOrigenPlanta(Date startDate, Date endDate, Long especieId, Long regionId) {
        return reportRepository.getCadenaOrigenPlanta(startDate, endDate, especieId, regionId);
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
        boolean bioPerdidaActivo = configService.getBoolean("bio_perdida_activo", true);
        double umbral = umbralVariacion != null ? umbralVariacion : configService.getDouble("variacion_peso_umbral_general_pct", 5.0);
        double mermaMinHumedo = configService.getDouble("bio_humedo_merma_minima_pct", 5.0);
        double mermaMaxSeco = configService.getDouble("bio_seco_merma_maxima_pct", 3.0);
        int diasMinHumedo = configService.getInt("bio_humedo_dias_minimos_transito", 3);
        int diasAmarilla = configService.getInt("retencion_bodega_dias_amarilla", 3);
        int diasNaranja = configService.getInt("retencion_bodega_dias_naranja", 5);
        int diasRoja = configService.getInt("retencion_bodega_dias_roja", 7);
        String estadosSujetos = configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO");

        java.util.Map<String, Object> res = reportRepository.getTrazabilidadLoteMetrics(
                startDate, endDate, umbral, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos, bioPerdidaActivo);
        res.put("bioPerdidaActivo", bioPerdidaActivo);
        return res;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getTrazabilidadLoteDetalle(Date startDate, Date endDate, String semaforo) {
        boolean bioPerdidaActivo = configService.getBoolean("bio_perdida_activo", true);
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
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos, bioPerdidaActivo);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getDesembarqueFisicoMetrics(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId,
            Long provinciaId, Long caletaId, Long usuarioId, Long macrozonaId, String agruparPor, String perfil) {
        double umbralAtipico = configService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
        boolean fRecolector = configService.getBoolean("desembarque_fuente_recolector_activa", true);
        boolean fArmador = configService.getBoolean("desembarque_fuente_armador_activa", true);
        boolean fArea = configService.getBoolean("desembarque_fuente_area_activa", true);

        return reportRepository.getDesembarqueFisicoMetrics(
                startDate, endDate, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, agruparPor, perfil,
                umbralAtipico, fRecolector, fArmador, fArea);
    }

    public java.util.Map<String, Object> getDesembarqueFisicoMetrics(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId, String perfil) {
        return getDesembarqueFisicoMetrics(startDate, endDate, especieId, comunaId, regionId,
                null, null, null, null, "ESPECIE", perfil);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getDesembarqueFisicoDetalle(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId,
            Long provinciaId, Long caletaId, Long usuarioId, Long macrozonaId, String perfil) {
        double umbralAtipico = configService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
        boolean fRecolector = configService.getBoolean("desembarque_fuente_recolector_activa", true);
        boolean fArmador = configService.getBoolean("desembarque_fuente_armador_activa", true);
        boolean fArea = configService.getBoolean("desembarque_fuente_area_activa", true);

        return reportRepository.getDesembarqueFisicoDetalle(
                startDate, endDate, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, perfil,
                umbralAtipico, fRecolector, fArmador, fArea);
    }

    public List<java.util.Map<String, Object>> getDesembarqueFisicoDetalle(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId, String perfil) {
        return getDesembarqueFisicoDetalle(startDate, endDate, especieId, comunaId, regionId,
                null, null, null, null, perfil);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getCapturaCorregidaMetrics(Date startDate, Date endDate, Long especieId) {
        return reportRepository.getCapturaCorregidaMetrics(startDate, endDate, especieId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getLimiteExtraccionDiarioMetrics(Date fecha) {
        return reportRepository.getLimiteExtraccionDiarioMetrics(fecha);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getLedHallazgos(Date startDate, Date endDate, Long regionId, Long embarcacionId) {
        return reportRepository.getLedHallazgos(startDate, endDate, regionId, embarcacionId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getRetencionBodegaMetrics() {
        return getRetencionBodegaMetrics(null, null);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getRetencionBodegaMetrics(Date startDate, Date endDate) {
        boolean activo = configService.getBoolean("retencion_bodega_activo", true);
        int humedoMaxH = configService.getInt("retencion_humedo_max_horas", 24);
        int semihumedoMaxH = configService.getInt("retencion_semihumedo_max_horas", 72);
        int semisecoMaxH = configService.getInt("retencion_semiseco_max_horas", 216);
        int preavisoPct = configService.getInt("retencion_preaviso_pct", 80);

        if (!activo) {
            java.util.Map<String, Object> disabled = new java.util.HashMap<>();
            disabled.put("activo", false);
            disabled.put("controlDesactivado", true);
            disabled.put("mensaje", "Control de retención en bodega desactivado en Administración");
            disabled.put("totalLotesEnBodega", 0);
            disabled.put("totalKgEnBodega", 0.0);
            disabled.put("humedoMaxHoras", humedoMaxH);
            disabled.put("semihumedoMaxHoras", semihumedoMaxH);
            disabled.put("semisecoMaxHoras", semisecoMaxH);
            disabled.put("preavisoPct", preavisoPct);
            disabled.put("semaforoVerde", 0L);
            disabled.put("semaforoAmarillo", 0L);
            disabled.put("semaforoNaranja", 0L);
            disabled.put("semaforoRojo", 0L);
            disabled.put("lotes", java.util.Collections.emptyList());
            disabled.put("lotesRojos", java.util.Collections.emptyList());
            disabled.put("matriz", java.util.Collections.emptyMap());
            return disabled;
        }

        java.util.Map<String, Object> metrics = reportRepository.getRetencionPorHumedad(
                startDate, endDate, humedoMaxH, semihumedoMaxH, semisecoMaxH, preavisoPct);
        metrics.put("activo", true);
        metrics.put("controlDesactivado", false);
        return metrics;
    }
}
