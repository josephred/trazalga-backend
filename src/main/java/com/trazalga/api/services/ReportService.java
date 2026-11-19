package com.trazalga.api.services;

import com.trazalga.api.config.CacheConfig;
import com.trazalga.api.dto.FiltroRecepcionesVariacion;
import com.trazalga.api.dto.OrigenInformativoDTO;
import com.trazalga.api.dto.PageDTO;
import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.dto.ResultadoVariacionPesoDTO;
import com.trazalga.api.repositories.ReportRepository;
import com.trazalga.api.services.indicadores.VariacionPesoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
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
 * SILENCIOSAMENTE: cada milisegundo distinto sería una clave nueva, el hit
 * ratio caería a cero y nadie se enteraría porque todo seguiría
 * funcionando, solo más lento. Vigilar el hit ratio en
 * /actuator/metrics/cache.gets es la forma de detectarlo.
 *
 * QUÉ NO SE CACHEA Y POR QUÉ
 * `getReport(...)` recibe un `rut`, así que su clave incluye al usuario. No
 * está cacheado a propósito: multiplicaría las entradas por el número de
 * usuarios sin que se repitan las claves.
 * ─────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ConfiguracionGeneralService configService;
    private final VariacionPesoService variacionPesoService;

    @Autowired(required = false)
    private com.trazalga.api.services.geo.ReferenciaOrigenService referenciaOrigenService;

    public void setReferenciaOrigenService(
            com.trazalga.api.services.geo.ReferenciaOrigenService referenciaOrigenService) {
        this.referenciaOrigenService = referenciaOrigenService;
    }

    @Autowired(required = false)
    private com.trazalga.api.services.retencion.RetencionCustodiaService retencionCustodiaService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.trazalga.api.services.parametros.ParametrosService parametrosService;

    public void setParametrosService(com.trazalga.api.services.parametros.ParametrosService parametrosService) {
        this.parametrosService = parametrosService;
    }

    private com.trazalga.api.services.parametros.ParametrosService getParametrosService() {
        if (this.parametrosService != null) {
            return this.parametrosService;
        }
        if (this.configService != null) {
            return new com.trazalga.api.services.parametros.ParametrosService(this.configService);
        }
        return null;
    }

    public ReportService(ReportRepository reportRepository, ConfiguracionGeneralService configService) {
        this(reportRepository, configService, null, null);
    }

    public ReportService(ReportRepository reportRepository,
            ConfiguracionGeneralService configService,
            VariacionPesoService variacionPesoService) {
        this(reportRepository, configService, variacionPesoService, null);
    }

    @Autowired
    public ReportService(ReportRepository reportRepository,
            ConfiguracionGeneralService configService,
            @Autowired(required = false) VariacionPesoService variacionPesoService,
            @Autowired(required = false) com.trazalga.api.services.retencion.RetencionCustodiaService retencionCustodiaService) {
        this.reportRepository = reportRepository;
        this.configService = configService;
        this.variacionPesoService = variacionPesoService;
        this.retencionCustodiaService = retencionCustodiaService;
        if (this.reportRepository != null && this.variacionPesoService != null) {
            this.reportRepository.setVariacionPesoService(this.variacionPesoService);
        }
    }

    public void setRetencionCustodiaService(
            com.trazalga.api.services.retencion.RetencionCustodiaService retencionCustodiaService) {
        this.retencionCustodiaService = retencionCustodiaService;
    }

    /**
     * Reporte por usuario. SIN caché: la clave incluye el rut y no se repite
     * lo suficiente para que el caché aporte.
     */
    public List<ReportDTO> getReport(Date fechaInicio, Date fechaFin, Integer tipoReporte, String rut) {
        return reportRepository.generateReport(fechaInicio, fechaFin, tipoReporte, rut, referenciaOrigenService);
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
    public java.util.Map<String, Object> getExtraccionVedaMetrics(Date startDate, Date endDate, Long especieId,
            Long regionId) {
        return reportRepository.getExtraccionVedaMetrics(startDate, endDate, especieId, regionId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate) {
        return getExtraccionVedaDetalle(startDate, endDate, null, null);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate, Long especieId,
            Long regionId) {
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
                    "ventanaMinMinutos", ventanaMinMinutos));
            return disabled;
        }

        List<java.util.Map<String, Object>> filas = reportRepository.getDeclaracionesConGeo(startDate, endDate,
                regionId);
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
                if (t1 == null || t2 == null)
                    return 0;
                return t1.compareTo(t2);
            });

            for (int i = 0; i < decls.size() - 1; i++) {
                java.util.Map<String, Object> a = decls.get(i);
                java.util.Map<String, Object> b = decls.get(i + 1);

                Double latA = (Double) a.get("latitud");
                Double lonA = (Double) a.get("longitud");
                Double latB = (Double) b.get("latitud");
                Double lonB = (Double) b.get("longitud");

                if (latA == null || lonA == null || latB == null || lonB == null)
                    continue;

                java.time.LocalDateTime tsA = parseTimestamp((Date) a.get("fechaDeclaracion"), (String) a.get("hora"));
                java.time.LocalDateTime tsB = parseTimestamp((Date) b.get("fechaDeclaracion"), (String) b.get("hora"));

                if (tsA == null || tsB == null)
                    continue;

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
                "ventanaMinMinutos", ventanaMinMinutos));

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
        if (fecha == null)
            return null;
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

    // =========================================================================
    // INDICADOR 9 — ORIGEN REAL VS GEOLOCALIZACIÓN GPS (Res. 25-sep / T9.4)
    // =========================================================================

    public java.util.Map<String, Object> getOrigenGeoPatrones(Date startDate, Date endDate, Long regionId) {
        boolean activo = configService.getBoolean("origen_geo_activo", true);
        double distanciaMaxKm = configService.getDouble("origen_geo_distancia_max_km", 30.0);
        double precisionMaxM = configService.getDouble("origen_geo_precision_max_m", 500.0);
        double patronPct = configService.getDouble("origen_geo_patron_pct", 50.0);
        int patronMinDecl = configService.getInt("origen_geo_patron_min_decl", 3);

        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("activo", activo);
        response.put("parametros", java.util.Map.of(
                "distanciaMaxKm", distanciaMaxKm,
                "precisionMaxM", precisionMaxM,
                "patronPct", patronPct,
                "patronMinDecl", patronMinDecl));

        if (!activo) {
            response.put("totalDeclaracionesEvaluadas", 0);
            response.put("totalInconsistencias", 0);
            response.put("totalSinGps", 0);
            response.put("totalSinReferencia", 0);
            response.put("totalMarcadas", 0);
            response.put("totalPrecisionBaja", 0);
            response.put("usuariosPatronSospechoso", 0);
            response.put("rankingUsuarios", java.util.Collections.emptyList());
            response.put("hallazgos", java.util.Collections.emptyList());
            return response;
        }

        List<java.util.Map<String, Object>> filas = reportRepository.getDeclaracionesOrigenGeo(startDate, endDate,
                regionId);

        long totalSinGps = 0;
        long totalSinReferencia = 0;
        long totalInconsistencias = 0;
        long totalMarcadas = 0;
        long totalPrecisionBaja = 0;

        List<java.util.Map<String, Object>> hallazgos = new java.util.ArrayList<>();

        class UserStat {
            Long usuarioId;
            String rut;
            String nombre;
            int total = 0;
            int sinGps = 0;
            int sinRef = 0;
            int lejos = 0;
            List<Double> distancias = new java.util.ArrayList<>();
        }

        java.util.Map<Long, UserStat> userStatsMap = new java.util.LinkedHashMap<>();

        for (java.util.Map<String, Object> fila : filas) {
            Long uId = fila.get("usuarioId") != null ? ((Number) fila.get("usuarioId")).longValue() : 0L;

            UserStat uStat = userStatsMap.computeIfAbsent(uId, k -> {
                UserStat s = new UserStat();
                s.usuarioId = k;
                s.rut = (String) fila.get("usuarioRut");
                s.nombre = (String) fila.get("usuarioNombre");
                return s;
            });

            uStat.total++;

            Double lat = fila.get("latitud") != null ? ((Number) fila.get("latitud")).doubleValue() : null;
            Double lon = fila.get("longitud") != null ? ((Number) fila.get("longitud")).doubleValue() : null;
            Double refLat = fila.get("refLat") != null ? ((Number) fila.get("refLat")).doubleValue() : null;
            Double refLon = fila.get("refLon") != null ? ((Number) fila.get("refLon")).doubleValue() : null;
            String refTipo = fila.get("refTipo") != null ? fila.get("refTipo").toString() : null;
            String refNombre = fila.get("refNombre") != null ? fila.get("refNombre").toString() : null;
            String refFuente = fila.get("refFuente") != null ? fila.get("refFuente").toString() : null;

            // TG.2: Resolución de referencia en Java mediante caché de
            // ReferenciaOrigenService
            if (referenciaOrigenService != null) {
                String tipoDecl = fila.get("tipo") != null ? fila.get("tipo").toString() : "";
                Long caletaId = fila.get("caletaId") != null ? ((Number) fila.get("caletaId")).longValue() : null;
                Long amerbId = fila.get("amerbId") != null ? ((Number) fila.get("amerbId")).longValue() : null;
                Long varaderoId = fila.get("varaderoId") != null ? ((Number) fila.get("varaderoId")).longValue() : null;
                String varaderoTexto = fila.get("varadero") != null ? fila.get("varadero").toString() : null;
                Long comunaId = fila.get("comunaId") != null ? ((Number) fila.get("comunaId")).longValue() : null;

                com.trazalga.api.dto.geo.PuntoReferencia puntoRef = referenciaOrigenService.resolverPorIds(tipoDecl,
                        caletaId, amerbId, varaderoId, varaderoTexto, comunaId);
                if (puntoRef != null) {
                    refLat = puntoRef.getLat();
                    refLon = puntoRef.getLon();
                    refTipo = puntoRef.getTipo();
                    refNombre = puntoRef.getNombre();
                    refFuente = puntoRef.getFuente();
                }
            }

            Double precision = fila.get("precisionGpsM") != null ? ((Number) fila.get("precisionGpsM")).doubleValue()
                    : null;
            Boolean offline = Boolean.TRUE.equals(fila.get("envioOffline"));

            boolean tieneGps = (lat != null && lon != null);
            boolean tieneRef = (refLat != null && refLon != null);

            if (!tieneGps) {
                totalSinGps++;
                uStat.sinGps++;
            }
            if (!tieneRef) {
                totalSinReferencia++;
                uStat.sinRef++;
            }

            if (tieneGps && tieneRef) {
                double dist = haversineKm(lat, lon, refLat, refLon);
                dist = Math.round(dist * 10.0) / 10.0;
                uStat.distancias.add(dist);

                if (dist > distanciaMaxKm) {
                    totalInconsistencias++;
                    uStat.lejos++;

                    boolean precisionAceptable = (precision == null || precision <= precisionMaxM);
                    if (precisionAceptable) {
                        totalMarcadas++;
                    } else {
                        totalPrecisionBaja++;
                    }

                    java.util.Map<String, Object> h = new java.util.LinkedHashMap<>();
                    h.put("tipo", fila.get("tipo"));
                    h.put("id", fila.get("id"));
                    h.put("folio", fila.get("folio"));
                    h.put("fechaDeclaracion", fila.get("fechaDeclaracion"));
                    h.put("hora", fila.get("hora"));
                    h.put("usuarioId", uId);
                    h.put("usuarioRut", fila.get("usuarioRut"));
                    h.put("usuarioNombre", fila.get("usuarioNombre"));
                    h.put("distanciaKm", dist);
                    h.put("precisionGpsM", precision != null ? Math.round(precision * 10.0) / 10.0 : null);
                    h.put("envioOffline", offline);
                    h.put("precisionAceptable", precisionAceptable);
                    h.put("refTipo", refTipo);
                    h.put("refNombre", refNombre);
                    if (refFuente != null) {
                        h.put("refFuente", refFuente);
                    }
                    h.put("gpsLatitud", lat);
                    h.put("gpsLongitud", lon);
                    h.put("refLatitud", refLat);
                    h.put("refLongitud", refLon);
                    h.put("marcaGenerada", precisionAceptable ? "ORIGEN_GEO_INCONSISTENTE" : null);

                    hallazgos.add(h);
                }
            }
        }

        List<java.util.Map<String, Object>> rankingUsuarios = new java.util.ArrayList<>();
        int sospechososCount = 0;

        for (UserStat stat : userStatsMap.values()) {
            java.util.Collections.sort(stat.distancias);
            double medianaDist = 0.0;
            if (!stat.distancias.isEmpty()) {
                int size = stat.distancias.size();
                if (size % 2 == 1) {
                    medianaDist = stat.distancias.get(size / 2);
                } else {
                    medianaDist = (stat.distancias.get((size / 2) - 1) + stat.distancias.get(size / 2)) / 2.0;
                }
                medianaDist = Math.round(medianaDist * 10.0) / 10.0;
            }

            int evaluadas = stat.distancias.size();
            double pctLejos = evaluadas > 0 ? ((stat.lejos * 100.0) / evaluadas) : 0.0;
            pctLejos = Math.round(pctLejos * 10.0) / 10.0;

            boolean sospechoso = (pctLejos >= patronPct) && (evaluadas >= patronMinDecl);
            if (sospechoso)
                sospechososCount++;

            java.util.Map<String, Object> uMap = new java.util.LinkedHashMap<>();
            uMap.put("usuarioId", stat.usuarioId);
            uMap.put("usuarioRut", stat.rut);
            uMap.put("usuarioNombre", stat.nombre);
            uMap.put("totalDeclaraciones", stat.total);
            uMap.put("declaracionesEvaluadas", evaluadas);
            uMap.put("declaracionesLejos", stat.lejos);
            uMap.put("porcentajeLejos", pctLejos);
            uMap.put("sinGps", stat.sinGps);
            uMap.put("sinReferencia", stat.sinRef);
            uMap.put("medianaDistanciaKm", medianaDist);
            uMap.put("patronSospechoso", sospechoso);
            uMap.put("rotuloPatron",
                    sospechoso ? "Patrón sospechoso — posible uso de clave por terceros" : "Comportamiento regular");

            rankingUsuarios.add(uMap);
        }

        rankingUsuarios.sort((a, b) -> {
            int cmpSospechoso = Boolean.compare((Boolean) b.get("patronSospechoso"),
                    (Boolean) a.get("patronSospechoso"));
            if (cmpSospechoso != 0)
                return cmpSospechoso;
            int cmpLejos = Integer.compare((Integer) b.get("declaracionesLejos"),
                    (Integer) a.get("declaracionesLejos"));
            if (cmpLejos != 0)
                return cmpLejos;
            return Double.compare((Double) b.get("porcentajeLejos"), (Double) a.get("porcentajeLejos"));
        });

        response.put("totalDeclaracionesEvaluadas", filas.size());
        response.put("totalInconsistencias", totalInconsistencias);
        response.put("totalSinGps", totalSinGps);
        response.put("totalSinReferencia", totalSinReferencia);
        response.put("totalMarcadas", totalMarcadas);
        response.put("totalPrecisionBaja", totalPrecisionBaja);
        response.put("usuariosEvaluados", userStatsMap.size());
        response.put("usuariosPatronSospechoso", sospechososCount);
        response.put("rankingUsuarios", rankingUsuarios);
        response.put("hallazgos", hallazgos);

        return response;
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
        java.util.Map<String, Object> metrics = new java.util.LinkedHashMap<>(
                reportRepository.getVariacionPesoMetrics(startDate, endDate, umbralPct));
        if (variacionPesoService != null) {
            try {
                metrics.put("comercializadorPlanta",
                        variacionPesoService.obtenerResumenComercializadorPlanta(startDate, endDate));
            } catch (Exception e) {
                log.warn("Error calculando bloque comercializadorPlanta: {}", e.getMessage());
                java.util.Map<String, Object> errBlock = new java.util.LinkedHashMap<>();
                errBlock.put("error", "Error calculando indicador: " + e.getMessage());
                errBlock.put("disponible", false);
                metrics.put("comercializadorPlanta", errBlock);
            }
        } else {
            java.util.Map<String, Object> errBlock = new java.util.LinkedHashMap<>();
            errBlock.put("error", "Servicio de variación de peso no disponible");
            errBlock.put("disponible", false);
            metrics.put("comercializadorPlanta", errBlock);
        }
        return metrics;
    }

    /**
     * TV.3 · Lista paginada de recepciones comercializador -> planta evaluadas con
     * la fórmula canónica.
     */
    public PageDTO<ResultadoVariacionPesoDTO> getVariacionPesoRecepciones(FiltroRecepcionesVariacion filtro,
            Pageable pageable) {
        if (variacionPesoService == null) {
            return PageDTO.of(java.util.Collections.emptyList(), 0, 20, 0);
        }
        return variacionPesoService.evaluarRecepciones(filtro, pageable);
    }

    /**
     * TV.3 · Lista de orígenes biológicos que alimentaron a la planta (informativo
     * bajo demanda).
     */
    public List<OrigenInformativoDTO> getOrigenesRecepcionPlanta(Long plantaId) {
        if (variacionPesoService == null) {
            return java.util.Collections.emptyList();
        }
        return variacionPesoService.obtenerOrigenesInformativos(plantaId);
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
    public List<java.util.Map<String, Object>> getCadenaOrigenPlanta(Date startDate, Date endDate, Long especieId,
            Long regionId) {
        return reportRepository.getCadenaOrigenPlanta(startDate, endDate, especieId, regionId);
    }

    /**
     * Trazabilidad. Recorre el grafo de declaraciones con un BFS que hace
     * una consulta por nodo, así que el coste crece con el tamaño de la
     * cadena, no con el volumen de la tabla.
     *
     * ⚠️ Sigue SIN MEDIRSE bajo carga: el escenario k6 enviaba el `tipo` como
     * texto cuando el controller espera un Integer, así que las peticiones
     * morían en el binding de Spring. Corregido en el script, pendiente de
     * ejecutar. El caché aquí es preventivo, no está justificado por una
     * medición.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_TRAZABILIDAD)
    public com.trazalga.api.dto.TrazabilidadResponseDTO getTrazabilidad(Integer tipo, Long id) {
        return reportRepository.getTrazabilidad(tipo, id);
    }

    // =========================================================================
    // FASE 4 — INDICADORES Y TRAZABILIDAD POR LOTE (folio_origen)
    // =========================================================================

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getTrazabilidadLoteMetrics(Date startDate, Date endDate,
            Double umbralVariacion) {
        com.trazalga.api.services.parametros.ParametrosService ps = getParametrosService();
        com.trazalga.api.services.parametros.ParametrosVariacion pVar = ps != null ? ps.variacion() : null;
        boolean bioPerdidaActivo = pVar != null ? pVar.bioPerdidaActivo() : true;
        double umbral = umbralVariacion != null ? umbralVariacion
                : (pVar != null ? pVar.variacionPesoUmbralGeneralPct() : 5.0);
        double mermaMinHumedo = pVar != null ? pVar.bioHumedoMermaMinimaPct() : 5.0;
        double mermaMaxSeco = pVar != null ? pVar.bioSecoMermaMaximaPct() : 3.0;
        int diasMinHumedo = pVar != null ? pVar.bioHumedoDiasMinimosTransito() : 3;

        int diasAmarilla = configService != null
                ? configService.getInt(
                        com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_DIAS_AMARILLA, 3)
                : 3;
        int diasNaranja = configService != null
                ? configService.getInt(
                        com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_DIAS_NARANJA, 5)
                : 5;
        int diasRoja = configService != null
                ? configService.getInt(
                        com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_DIAS_ROJA, 7)
                : 7;
        com.trazalga.api.services.parametros.ParametrosRetencion retParams = retencionCustodiaService != null
                ? retencionCustodiaService.getParametros()
                : null;
        if (retParams != null) {
            int horasHumedo = retParams.humedoMaxHoras();
            double preavisoPct = retParams.semaforoPreavisoPct();
            diasAmarilla = Math.max(1, (int) Math.round((horasHumedo * (preavisoPct / 100.0)) / 24.0));
            diasNaranja = Math.max(diasAmarilla, (int) Math.round(horasHumedo / 24.0));
            diasRoja = Math.max(diasNaranja + 1, (int) Math.round((horasHumedo * 1.5) / 24.0));
        }
        String estadosSujetos = configService != null ? configService.getValor(
                com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_ESTADOS_SUJETOS, "HUMEDO")
                : "HUMEDO";

        java.util.Map<String, Object> res = reportRepository.getTrazabilidadLoteMetrics(
                startDate, endDate, umbral, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos, bioPerdidaActivo);
        res.put("bioPerdidaActivo", bioPerdidaActivo);
        return res;
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    public List<java.util.Map<String, Object>> getTrazabilidadLoteDetalle(Date startDate, Date endDate,
            String semaforo) {
        com.trazalga.api.services.parametros.ParametrosService ps = getParametrosService();
        com.trazalga.api.services.parametros.ParametrosVariacion pVar = ps != null ? ps.variacion() : null;
        boolean bioPerdidaActivo = pVar != null ? pVar.bioPerdidaActivo() : true;
        double umbral = pVar != null ? pVar.variacionPesoUmbralGeneralPct() : 5.0;
        double mermaMinHumedo = pVar != null ? pVar.bioHumedoMermaMinimaPct() : 5.0;
        double mermaMaxSeco = pVar != null ? pVar.bioSecoMermaMaximaPct() : 3.0;
        int diasMinHumedo = pVar != null ? pVar.bioHumedoDiasMinimosTransito() : 3;

        int diasAmarilla = configService != null
                ? configService.getInt(
                        com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_DIAS_AMARILLA, 3)
                : 3;
        int diasNaranja = configService != null
                ? configService.getInt(
                        com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_DIAS_NARANJA, 5)
                : 5;
        int diasRoja = configService != null
                ? configService.getInt(
                        com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_DIAS_ROJA, 7)
                : 7;
        com.trazalga.api.services.parametros.ParametrosRetencion retParams = retencionCustodiaService != null
                ? retencionCustodiaService.getParametros()
                : null;
        if (retParams != null) {
            int horasHumedo = retParams.humedoMaxHoras();
            double preavisoPct = retParams.semaforoPreavisoPct();
            diasAmarilla = Math.max(1, (int) Math.round((horasHumedo * (preavisoPct / 100.0)) / 24.0));
            diasNaranja = Math.max(diasAmarilla, (int) Math.round(horasHumedo / 24.0));
            diasRoja = Math.max(diasNaranja + 1, (int) Math.round((horasHumedo * 1.5) / 24.0));
        }
        String estadosSujetos = configService != null ? configService.getValor(
                com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BODEGA_ESTADOS_SUJETOS, "HUMEDO")
                : "HUMEDO";

        return reportRepository.getTrazabilidadLoteDetalle(
                startDate, endDate, semaforo, umbral, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos, bioPerdidaActivo);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getDesembarqueFisicoMetrics(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId,
            Long provinciaId, Long caletaId, Long usuarioId, Long macrozonaId, String agruparPor, String perfil) {
        com.trazalga.api.services.parametros.ParametrosService ps = getParametrosService();
        double umbralAtipico = (ps != null && ps.desembarque() != null)
                ? ps.desembarque().umbralAtipicoKg()
                : 5000.0;
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
    public List<java.util.Map<String, Object>> getLedHallazgos(Date startDate, Date endDate, Long regionId,
            Long embarcacionId) {
        return reportRepository.getLedHallazgos(startDate, endDate, regionId, embarcacionId);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getRetencionBodegaMetrics() {
        return getRetencionBodegaMetrics(null, null);
    }

    @Cacheable(cacheNames = CacheConfig.CACHE_METRICAS)
    public java.util.Map<String, Object> getRetencionBodegaMetrics(Date startDate, Date endDate) {
        if (retencionCustodiaService != null) {
            return retencionCustodiaService.obtenerMetricasRetencion(startDate, endDate);
        }

        com.trazalga.api.services.parametros.ParametrosService ps = getParametrosService();
        com.trazalga.api.services.parametros.ParametrosRetencion pRet = ps != null ? ps.retencion() : null;
        boolean activo = pRet != null ? pRet.retencionActivo() : true;
        int humedoMaxH = pRet != null ? pRet.humedoMaxHoras() : 24;
        int semihumedoMaxH = pRet != null ? pRet.semihumedoMaxHoras() : 72;
        int semisecoMaxH = pRet != null ? pRet.semisecoMaxHoras() : 216;
        int preavisoPct = pRet != null ? (int) Math.round(pRet.semaforoPreavisoPct()) : 80;

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

    /**
     * Consulta de control para detectar cadenas con mas de un salto de
     * comercializador (T2.5).
     */
    public long contarComercializadoresMultisalto() {
        return reportRepository.contarComercializadoresMultisalto();
    }

    /**
     * Stock disponible en planta: recepciones de abastecimiento aún no consumidas
     * por producción ni destino (NA.2).
     */
    public java.util.Map<String, Object> getStockEnPlanta(Long plantaId) {
        return reportRepository.getStockEnPlanta(plantaId);
    }

    /**
     * Rendimiento de producción en planta de proceso (NP.7).
     */
    public java.util.Map<String, Object> getRendimientoPlanta(Date startDate, Date endDate, Long plantaId,
            Long especieId) {
        return reportRepository.getRendimientoPlanta(startDate, endDate, plantaId, especieId);
    }

    /**
     * Resumen y bandeja de consultas ciudadanas de patentes (QC.4 / D39).
     * Respeta exclusivamente los resultados anómalos configurados, nunca expone
     * ip_hash y no crea marcas.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Object> getConsultasCiudadanas(Date startDate, Date endDate) {
        return reportRepository.getConsultasCiudadanas(startDate, endDate);
    }

    /**
     * QM.3 / QE.1 · Mapa regional de flujos origen -> planta y detalle plano por
     * declaración para exportación.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_DETALLE)
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<String, Object> getFlujosOrigenDestino(
            Date startDate, Date endDate, Long especieId, Long regionId) {
        return getFlujosOrigenDestino(startDate, endDate, especieId, regionId, null);
    }

    public java.util.Map<String, Object> getFlujosOrigenDestino(
            Date startDate, Date endDate, Long especieId, Long regionId, Long regionDestinoId) {
        List<java.util.Map<String, Object>> filas = reportRepository.getCadenaOrigenPlantaFlujosRaw(startDate, endDate,
                especieId, regionId, regionDestinoId);

        List<java.util.Map<String, Object>> declaraciones = new java.util.ArrayList<>();
        java.util.Map<Long, Double> pesoRomanaPorRecepcion = new java.util.HashMap<>();

        class PairAcc {
            String key;
            String origTipo;
            String origTipoDeclaracion;
            Long origRefId;
            String origNombre;
            Long regionId;
            String regionNombre;
            Double origLat;
            Double origLon;
            String origFuente = "SIN_COORDENADAS";
            String origFuenteDetalle;
            String origReferenciaFuente = "SIN_COORD";

            String plantaCodigo;
            String plantaNombre;
            String plantaRut;
            Long plantaRegionId;
            String plantaRegionNombre;
            String plantaComunaNombre;
            Double plantaLat;
            Double plantaLon;
            String plantaFuente = "SIN_COORDENADAS";
            String plantaFuenteDetalle;

            double desembarqueKg = 0.0;
            double capturaKg = 0.0;
            int declaracionesCount = 0;
            java.util.Map<Long, Double> recepciones = new java.util.LinkedHashMap<>();
            java.util.Set<String> especies = new java.util.LinkedHashSet<>();
            java.util.List<java.util.Map<String, Object>> comercializadores = new java.util.ArrayList<>();
            java.util.Set<String> comVistos = new java.util.LinkedHashSet<>();
            String comercializadorFolio;
            Double comercializadorLatitud;
            Double comercializadorLongitud;
            String comercializadorFuente;
            boolean algunComercializadorSinCoordenadas = false;
        }

        java.util.Map<String, PairAcc> pairsByKey = new java.util.LinkedHashMap<>();

        for (java.util.Map<String, Object> fila : filas) {
            String origenTipoDecl = strOrNull(fila.get("origenTipo"));
            Long caletaId = longOrNull(fila.get("caletaId"));
            Long amerbId = longOrNull(fila.get("amerbId"));
            Long varaderoId = longOrNull(fila.get("varaderoId"));
            String varaderoTexto = strOrNull(fila.get("varaderoTexto"));
            Long comunaId = longOrNull(fila.get("comunaId"));
            String catalogoNombre = resolverNombreCatalogoOrigen(
                    origenTipoDecl,
                    strOrNull(fila.get("caletaNombre")),
                    strOrNull(fila.get("amerbNombre")),
                    strOrNull(fila.get("varaderoNombre")),
                    varaderoTexto);

            com.trazalga.api.dto.geo.PuntoReferencia puntoRefOrigen = referenciaOrigenService != null
                    ? referenciaOrigenService.resolverPorIds(
                            origenTipoDecl, caletaId, amerbId, varaderoId, varaderoTexto, comunaId)
                    : null;

            Double origGpsLat = doubleOrNull(fila.get("origenGpsLat"));
            Double origGpsLon = doubleOrNull(fila.get("origenGpsLon"));

            Double origLat = null;
            Double origLon = null;
            String origFuente = "SIN_COORDENADAS";
            String origFuenteDetalle = null;
            String origReferenciaFuente = "SIN_COORD";
            String origTipoPunto = origenTipoDecl;
            Long origPointId = null;
            String origNombre;
            String origGroupKey;

            if (puntoRefOrigen != null && esCoordenadaChileValida(puntoRefOrigen.getLat(), puntoRefOrigen.getLon())) {
                origLat = puntoRefOrigen.getLat();
                origLon = puntoRefOrigen.getLon();
                origFuente = "REFERENCIA";
                origFuenteDetalle = puntoRefOrigen.getFuente();
                origTipoPunto = firstNonBlank(puntoRefOrigen.getTipo(), origenTipoDecl);
                origReferenciaFuente = normalizarFuenteReferenciaOrigen(origTipoPunto, origFuenteDetalle);
                origPointId = puntoRefOrigen.getId();
                origNombre = firstNonBlank(puntoRefOrigen.getNombre(), catalogoNombre, origenTipoDecl);
                origGroupKey = "REF|" + origTipoPunto + "|" +
                        (origPointId != null ? origPointId : normalizarClave(origNombre));
            } else if (esCoordenadaChileValida(origGpsLat, origGpsLon)) {
                origLat = origGpsLat;
                origLon = origGpsLon;
                origFuente = "GPS";
                origFuenteDetalle = "GPS_DECLARACION";
                origReferenciaFuente = "GPS";
                origNombre = firstNonBlank(catalogoNombre, "Punto GPS (" + origenTipoDecl + ")");
                String baseId = caletaId != null ? "C" + caletaId
                        : amerbId != null ? "A" + amerbId
                                : varaderoId != null ? "V" + varaderoId
                                        : normalizarClave(origNombre);
                origGroupKey = "GPS|" + origenTipoDecl + "|" + baseId;
            } else {
                origNombre = firstNonBlank(catalogoNombre, "Sin ubicación (" + origenTipoDecl + ")");
                String baseId = caletaId != null ? "C" + caletaId
                        : amerbId != null ? "A" + amerbId
                                : varaderoId != null ? "V" + varaderoId
                                        : normalizarClave(origNombre);
                origGroupKey = "NONE|" + origenTipoDecl + "|" + baseId;
            }

            String plantaCodigo = strOrNull(fila.get("plantaCodigo"));
            String plantaNombreRaw = strOrNull(fila.get("plantaNombreRaw"));
            String plantaUsuarioNombre = strOrNull(fila.get("plantaUsuarioNombre"));
            String plantaUsuarioRut = strOrNull(fila.get("plantaUsuarioRut"));
            String plantaNombreDisplay = firstNonBlank(plantaNombreRaw, plantaUsuarioNombre, plantaCodigo, "Planta");

            com.trazalga.api.dto.geo.PuntoReferencia puntoRefPlanta = referenciaOrigenService != null
                    ? referenciaOrigenService.resolverPlantaOficial(plantaCodigo, plantaUsuarioRut, plantaNombreRaw)
                    : null;

            Double plantaGpsLat = doubleOrNull(fila.get("plantaGpsLat"));
            Double plantaGpsLon = doubleOrNull(fila.get("plantaGpsLon"));

            Double plLat = null;
            Double plLon = null;
            String plFuente = "SIN_COORDENADAS";
            String plFuenteDetalle = null;
            String plNombre = plantaNombreDisplay;

            if (puntoRefPlanta != null && esCoordenadaChileValida(puntoRefPlanta.getLat(), puntoRefPlanta.getLon())) {
                plLat = puntoRefPlanta.getLat();
                plLon = puntoRefPlanta.getLon();
                plFuente = "REFERENCIA";
                plFuenteDetalle = puntoRefPlanta.getFuente();
                plNombre = firstNonBlank(puntoRefPlanta.getNombre(), plantaNombreDisplay);
            } else if (esCoordenadaChileValida(plantaGpsLat, plantaGpsLon)) {
                plLat = plantaGpsLat;
                plLon = plantaGpsLon;
                plFuente = "GPS";
                plFuenteDetalle = "GPS_RECEPCION";
            }

            List<java.util.Map<String, Object>> comercializadoresDecl = new java.util.ArrayList<>();
            Object rawHopsObj = fila.get("comercializadoresRaw");
            Double comLatPrincipal = null;
            Double comLonPrincipal = null;
            String comFuentePrincipal = null;
            boolean declComSinCoords = false;
            if (rawHopsObj instanceof List<?> rawHops) {
                for (Object hObj : rawHops) {
                    if (hObj instanceof java.util.Map<?, ?> hm) {
                        Long cId = longOrNull(hm.get("id"));
                        String cFolio = strOrNull(hm.get("folio"));
                        Double cLat = doubleOrNull(hm.get("latitud"));
                        Double cLon = doubleOrNull(hm.get("longitud"));
                        boolean coordOk = esCoordenadaChileValida(cLat, cLon);
                        java.util.Map<String, Object> hop = new java.util.LinkedHashMap<>();
                        hop.put("id", cId);
                        hop.put("folio", cFolio);
                        hop.put("latitud", coordOk ? cLat : null);
                        hop.put("longitud", coordOk ? cLon : null);
                        hop.put("fuente", coordOk ? "GPS_DECLARACION" : "SIN_COORDENADAS");
                        comercializadoresDecl.add(hop);
                        if (coordOk && comLatPrincipal == null) {
                            comLatPrincipal = cLat;
                            comLonPrincipal = cLon;
                            comFuentePrincipal = "GPS_DECLARACION";
                        } else if (!coordOk) {
                            declComSinCoords = true;
                        }
                    }
                }
            }
            if (!comercializadoresDecl.isEmpty() && comFuentePrincipal == null) {
                comFuentePrincipal = "SIN_COORDENADAS";
            }

            String especie = firstNonBlank(strOrNull(fila.get("origenEspecieNombre")),
                    strOrNull(fila.get("recepcionEspecieNombre")));
            double desembarqueKg = doubleOrZero(fila.get("desembarqueKg"));
            double capturaKg = doubleOrZero(fila.get("capturaKg"));
            double pesoRomanaKg = doubleOrZero(fila.get("pesoRomanaKg"));
            Long recepcionId = longOrNull(fila.get("recepcionId"));
            Long rowRegionId = longOrNull(fila.get("regionId"));
            String rowRegionNombre = strOrNull(fila.get("regionNombre"));

            if (recepcionId != null) {
                pesoRomanaPorRecepcion.putIfAbsent(recepcionId, pesoRomanaKg);
            }

            java.util.Map<String, Object> declRow = new java.util.LinkedHashMap<>();
            declRow.put("origenFolio", strOrNull(fila.get("origenFolio")));
            declRow.put("origenTipo", origenTipoDecl);
            declRow.put("origenFecha", strOrNull(fila.get("origenFecha")));
            declRow.put("especie", especie);
            declRow.put("desembarqueKg", redondearDosDec(desembarqueKg));
            declRow.put("capturaKg", redondearDosDec(capturaKg));
            declRow.put("origenNombre", origNombre);
            declRow.put("origenLatitud", origLat);
            declRow.put("origenLongitud", origLon);
            declRow.put("origenFuente", origFuente);
            declRow.put("origenFuenteDetalle", origFuenteDetalle);
            declRow.put("origenReferenciaFuente", origReferenciaFuente);
            declRow.put("regionId", rowRegionId);
            declRow.put("regionNombre", rowRegionNombre);
            declRow.put("comercializadorFolio", strOrNull(fila.get("comercializadorFolio")));
            declRow.put("comercializadorLatitud", comLatPrincipal);
            declRow.put("comercializadorLongitud", comLonPrincipal);
            declRow.put("comercializadorFuente", comFuentePrincipal);
            declRow.put("comercializadores", comercializadoresDecl);
            Long plantaRegionId = longOrNull(fila.get("plantaRegionId"));
            String plantaRegionNombre = strOrNull(fila.get("plantaRegionNombre"));
            String plantaComunaNombre = strOrNull(fila.get("plantaComunaNombre"));
            declRow.put("plantaNombre", plNombre);
            declRow.put("plantaCodigo", plantaCodigo);
            declRow.put("plantaComuna", plantaComunaNombre);
            declRow.put("plantaRegionId", plantaRegionId);
            declRow.put("plantaRegionNombre", plantaRegionNombre);
            declRow.put("plantaLatitud", plLat);
            declRow.put("plantaLongitud", plLon);
            declRow.put("plantaFuente", plFuente);
            declRow.put("plantaFuenteDetalle", plFuenteDetalle);
            declRow.put("recepcionFolio", strOrNull(fila.get("recepcionFolio")));
            declRow.put("recepcionFecha", strOrNull(fila.get("recepcionFecha")));
            declRow.put("pesoRomanaKg", redondearDosDec(pesoRomanaKg));
            declaraciones.add(declRow);

            String plantaGroupKey = !esBlanco(plantaCodigo)
                    ? "COD|" + plantaCodigo.trim().toUpperCase(java.util.Locale.ROOT)
                    : "NOM|" + normalizarClave(plNombre);
            String pairKey = origGroupKey + "||" + plantaGroupKey;

            final String fOrigTipo = origTipoPunto;
            final String fOrigTipoDecl = origenTipoDecl;
            final Long fOrigId = origPointId;
            final String fOrigNombre = origNombre;
            final String fPlCodigo = plantaCodigo;
            final String fPlNombre = plNombre;
            final String fPlRut = plantaUsuarioRut;

            PairAcc acc = pairsByKey.computeIfAbsent(pairKey, k -> {
                PairAcc p = new PairAcc();
                p.key = k;
                p.origTipo = fOrigTipo;
                p.origTipoDeclaracion = fOrigTipoDecl;
                p.origRefId = fOrigId;
                p.origNombre = fOrigNombre;
                p.regionId = rowRegionId;
                p.regionNombre = rowRegionNombre;
                p.plantaCodigo = fPlCodigo;
                p.plantaNombre = fPlNombre;
                p.plantaRut = fPlRut;
                p.plantaRegionId = plantaRegionId;
                p.plantaRegionNombre = plantaRegionNombre;
                p.plantaComunaNombre = plantaComunaNombre;
                return p;
            });

            if (acc.origLat == null && origLat != null && origLon != null) {
                acc.origLat = origLat;
                acc.origLon = origLon;
                acc.origFuente = origFuente;
                acc.origFuenteDetalle = origFuenteDetalle;
                acc.origReferenciaFuente = origReferenciaFuente;
            }
            if ("SIN_COORDENADAS".equals(acc.plantaFuente)
                    || ("GPS".equals(acc.plantaFuente) && "REFERENCIA".equals(plFuente))) {
                if (plLat != null && plLon != null) {
                    acc.plantaLat = plLat;
                    acc.plantaLon = plLon;
                    acc.plantaFuente = plFuente;
                    acc.plantaFuenteDetalle = plFuenteDetalle;
                    acc.plantaNombre = plNombre;
                }
            }
            acc.desembarqueKg += desembarqueKg;
            acc.capturaKg += capturaKg;
            acc.declaracionesCount++;
            if (recepcionId != null) {
                acc.recepciones.putIfAbsent(recepcionId, pesoRomanaKg);
            }
            if (!esBlanco(especie)) {
                acc.especies.add(especie);
            }
            if (!comercializadoresDecl.isEmpty()) {
                if (esBlanco(acc.comercializadorFolio)) {
                    acc.comercializadorFolio = strOrNull(fila.get("comercializadorFolio"));
                }
                if (acc.comercializadorLatitud == null && comLatPrincipal != null) {
                    acc.comercializadorLatitud = comLatPrincipal;
                    acc.comercializadorLongitud = comLonPrincipal;
                    acc.comercializadorFuente = comFuentePrincipal;
                } else if (acc.comercializadorFuente == null) {
                    acc.comercializadorFuente = comFuentePrincipal;
                }
                if (declComSinCoords) {
                    acc.algunComercializadorSinCoordenadas = true;
                }
                for (java.util.Map<String, Object> hop : comercializadoresDecl) {
                    String hKey = String.valueOf(hop.get("id")) + "|" + String.valueOf(hop.get("folio"));
                    if (acc.comVistos.add(hKey)) {
                        acc.comercializadores.add(hop);
                    }
                }
            }
        }

        List<PairAcc> sortedPairs = new java.util.ArrayList<>(pairsByKey.values());
        sortedPairs.sort((a, b) -> Double.compare(b.desembarqueKg, a.desembarqueKg));

        List<java.util.Map<String, Object>> flujosMapa = new java.util.ArrayList<>();
        List<java.util.Map<String, Object>> flujosSinCoordenadas = new java.util.ArrayList<>();
        double totalDesembarque = 0.0;
        double totalCaptura = 0.0;

        for (PairAcc acc : sortedPairs) {
            totalDesembarque += acc.desembarqueKg;
            totalCaptura += acc.capturaKg;
            double pairRomanaKg = acc.recepciones.values().stream().mapToDouble(Double::doubleValue).sum();

            boolean tieneOrig = acc.origLat != null && acc.origLon != null;
            boolean tienePl = acc.plantaLat != null && acc.plantaLon != null;
            String motivo = null;
            if (!tieneOrig && !tienePl) {
                motivo = "Origen sin referencia oficial ni GPS de declaración; planta sin coordenadas oficiales ni GPS de recepción";
            } else if (!tieneOrig) {
                motivo = "Origen sin referencia oficial (caleta/varadero/AMERB) ni GPS de declaración";
            } else if (!tienePl) {
                motivo = "Planta sin coordenadas en maestro oficial ni GPS de recepción";
            }

            java.util.Map<String, Object> origMap = new java.util.LinkedHashMap<>();
            origMap.put("tipo", acc.origTipo);
            origMap.put("tipoDeclaracion", acc.origTipoDeclaracion);
            origMap.put("id", acc.origRefId);
            origMap.put("nombre", acc.origNombre);
            origMap.put("regionId", acc.regionId);
            origMap.put("regionNombre", acc.regionNombre);
            origMap.put("latitud", acc.origLat);
            origMap.put("longitud", acc.origLon);
            origMap.put("fuente", acc.origFuente);
            origMap.put("fuenteDetalle", acc.origFuenteDetalle);
            origMap.put("fuenteReferencia", acc.origReferenciaFuente);

            java.util.Map<String, Object> plantaMap = new java.util.LinkedHashMap<>();
            plantaMap.put("codigo", acc.plantaCodigo);
            plantaMap.put("nombre", acc.plantaNombre);
            plantaMap.put("comuna", acc.plantaComunaNombre);
            plantaMap.put("regionId", acc.plantaRegionId);
            plantaMap.put("regionNombre", acc.plantaRegionNombre);
            plantaMap.put("latitud", acc.plantaLat);
            plantaMap.put("longitud", acc.plantaLon);
            plantaMap.put("fuente", acc.plantaFuente);
            plantaMap.put("fuenteDetalle", acc.plantaFuenteDetalle);

            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", acc.key);
            item.put("origen", origMap);
            item.put("planta", plantaMap);
            item.put("comercializadorFolio", acc.comercializadorFolio);
            item.put("comercializadorLatitud", acc.comercializadorLatitud);
            item.put("comercializadorLongitud", acc.comercializadorLongitud);
            item.put("comercializadorFuente", acc.comercializadorFuente);
            item.put("comercializadores", acc.comercializadores);
            item.put("algunComercializadorSinCoordenadas", acc.algunComercializadorSinCoordenadas);
            item.put("desembarqueKg", redondearDosDec(acc.desembarqueKg));
            item.put("capturaKg", redondearDosDec(acc.capturaKg));
            item.put("pesoRomanaKg", redondearDosDec(pairRomanaKg));
            item.put("declaracionesCount", acc.declaracionesCount);
            item.put("recepcionesCount", acc.recepciones.size());
            item.put("especies", new java.util.ArrayList<>(acc.especies));
            if (motivo != null) {
                item.put("motivoSinCoordenadas", motivo);
                flujosSinCoordenadas.add(item);
            } else {
                flujosMapa.add(item);
            }
        }

        double pesoRomanaTotal = pesoRomanaPorRecepcion.values().stream().mapToDouble(Double::doubleValue).sum();
        java.util.Map<String, Object> resumen = new java.util.LinkedHashMap<>();
        resumen.put("totalPares", sortedPairs.size());
        resumen.put("paresConCoordenadas", flujosMapa.size());
        resumen.put("paresSinCoordenadas", flujosSinCoordenadas.size());
        resumen.put("totalDeclaraciones", declaraciones.size());
        resumen.put("desembarqueTotalKg", redondearDosDec(totalDesembarque));
        resumen.put("capturaTotalKg", redondearDosDec(totalCaptura));
        resumen.put("pesoRomanaTotalKg", redondearDosDec(pesoRomanaTotal));

        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("resumen", resumen);
        resp.put("flujosMapa", flujosMapa);
        resp.put("flujosSinCoordenadas", flujosSinCoordenadas);
        resp.put("declaraciones", declaraciones);
        return resp;
    }

    private static String resolverNombreCatalogoOrigen(
            String tipo, String caletaNombre, String amerbNombre, String varaderoNombre, String varaderoTexto) {
        if ("AREA".equals(tipo) || "AMERB".equals(tipo)) {
            return firstNonBlank(amerbNombre, caletaNombre);
        }
        if ("RECOLECTOR".equals(tipo)) {
            return firstNonBlank(varaderoNombre, varaderoTexto, caletaNombre);
        }
        return firstNonBlank(caletaNombre, varaderoNombre, varaderoTexto);
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null)
            return null;
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) {
                return c.trim();
            }
        }
        return null;
    }

    private static boolean esBlanco(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizarClave(String s) {
        return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String strOrNull(Object o) {
        return o != null ? o.toString() : null;
    }

    private static Long longOrNull(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : null;
    }

    private static Double doubleOrNull(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : null;
    }

    private static double doubleOrZero(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    public java.util.Map<String, Object> getCapasGeorreferencia(Long regionId) {
        return reportRepository.getCapasGeorreferencia(regionId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] exportarFlujosOrigenDestinoExcel(
            Date startDate,
            Date endDate,
            Long especieId,
            Long regionId,
            Long regionDestinoId) {
        java.util.Map<String, Object> data = getFlujosOrigenDestino(
                startDate, endDate, especieId, regionId, regionDestinoId);
        return com.trazalga.api.services.geo.FlujosSigExporter.exportarExcel(
                data, startDate, endDate, especieId, regionId, regionDestinoId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] exportarFlujosOrigenDestinoCsv(
            Date startDate,
            Date endDate,
            Long especieId,
            Long regionId,
            Long regionDestinoId) {
        java.util.Map<String, Object> data = getFlujosOrigenDestino(
                startDate, endDate, especieId, regionId, regionDestinoId);
        return com.trazalga.api.services.geo.FlujosSigExporter.exportarCsv(data);
    }

    public static boolean esCoordenadaChileValida(Double lat, Double lon) {
        return com.trazalga.api.services.geo.CoordenadaNormalizer.estaEnRangoChile(lat, lon);
    }

    /**
     * YR.3: Etiqueta compacta (<= 10 caracteres) de la fuente geográfica de origen
     * para reportes, mapa de flujos y campo SIG {@code REF_ORI}.
     */
    public static String normalizarFuenteReferenciaOrigen(String tipoPunto, String fuenteDetalle) {
        String t = tipoPunto != null ? tipoPunto.trim().toUpperCase(java.util.Locale.ROOT) : "";
        String d = fuenteDetalle != null ? fuenteDetalle.trim().toUpperCase(java.util.Locale.ROOT) : "";
        if ("VARADERO".equals(t) || d.contains("VARADERO")) {
            return "VARADERO";
        }
        if ("CALETA".equals(t) || "CALETA".equals(d)) {
            return "CALETA";
        }
        if ("AMERB".equals(t) || "AREA".equals(t) || "AMERB".equals(d)) {
            return "AMERB";
        }
        if ("GPS".equals(t) || d.contains("GPS")) {
            return "GPS";
        }
        return "SIN_COORD";
    }

    private static double redondearDosDec(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
