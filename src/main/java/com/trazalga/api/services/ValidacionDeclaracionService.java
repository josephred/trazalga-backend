package com.trazalga.api.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.dto.ResultadoValidacion.DecisionValidacion;
import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.repositories.IComunaRepository;

@Service
public class ValidacionDeclaracionService {

    @Autowired
    private CapturaService capturaService;

    @Autowired
    private VedaEvaluadorService vedaEvaluadorService;

    @Autowired
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    private LimiteExtraccionDiarioService limiteExtraccionDiarioService;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired
    private IComunaRepository comunaRepository;

    @Autowired(required = false)
    private DeclaracionMarcaService declaracionMarcaService;

    @Autowired(required = false)
    private com.trazalga.api.repositories.ReportRepository reportRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.ICaletaRepository caletaRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IAmerbRepository amerbRepository;

    @Autowired(required = false)
    private com.trazalga.api.services.cuotas.ResolutorImputacionTerritorial resolutorImputacion;

    /**
     * Orquesta las 5 validaciones del servidor antes de persistir una declaración.
     */
    public ResultadoValidacion validar(ContextoDeclaracion ctx) {
        List<MarcaItem> marcas = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        // ---------------------------------------------------------------------
        // 1. Cálculo de Captura (Backend Authority)
        // ---------------------------------------------------------------------
        CalculoCapturaResult capRes = capturaService.calcular(
                ctx.getEspecieId(),
                ctx.getHumedadEstadoId(),
                ctx.getFechaExtraccion(),
                ctx.getDesembarqueKg());

        if (!capRes.isExitoso()) {
            return ResultadoValidacion.builder()
                    .decision(DecisionValidacion.RECHAZAR)
                    .motivoRechazo(capRes.getMensaje())
                    .build();
        }

        BigDecimal capturaCalculada = capRes.getCaptura();
        BigDecimal factorAplicado = capRes.getFactorAplicado();
        Long factorConversionId = capRes.getFactorConversionId();

        // ---------------------------------------------------------------------
        // Resolver Región
        // ---------------------------------------------------------------------
        Long regionId = ctx.getRegionId();
        if (regionId == null) {
            Long comId = ctx.getComunaDesembarqueId() != null ? ctx.getComunaDesembarqueId() : ctx.getComunaInscripcionId();
            if (comId != null) {
                Optional<ComunaModel> comOpt = comunaRepository.findById(comId);
                if (comOpt.isPresent() && comOpt.get().getRegion() != null) {
                    regionId = comOpt.get().getRegion().getId();
                }
            }
        }

        // ---------------------------------------------------------------------
        // 2. Evaluación de Veda
        // ---------------------------------------------------------------------
        VedaEvaluadorService.EvaluacionVedaResult vedaRes = vedaEvaluadorService.evaluar(
                ctx.getEspecieId(),
                ctx.getExtraccionTipoId(),
                regionId,
                ctx.getFechaExtraccion());

        if (vedaRes.isEnVeda()) {
            marcas.add(MarcaItem.builder()
                    .marca("EN_VEDA")
                    .detalle(vedaRes.getMensaje())
                    .reglaId(vedaRes.getVedaAplicada() != null ? vedaRes.getVedaAplicada().getId() : null)
                    .build());
            if (vedaRes.isBloquear()) {
                return ResultadoValidacion.builder()
                        .decision(DecisionValidacion.RECHAZAR)
                        .motivoRechazo(vedaRes.getMensaje())
                        .marcas(marcas)
                        .capturaCalculada(capturaCalculada)
                        .factorAplicado(factorAplicado)
                        .factorConversionId(factorConversionId)
                        .build();
            } else {
                advertencias.add(vedaRes.getMensaje());
            }
        }

        // ---------------------------------------------------------------------
        // 3. Evaluación de Cuotas de Extracción (TC.8)
        // ---------------------------------------------------------------------
        Long comunaImputacion;
        if (resolutorImputacion != null) {
            comunaImputacion = resolutorImputacion.resolver(ctx.getTipoDeclaracion()).comunaImputacion(ctx);
        } else {
            comunaImputacion = "RECOLECTOR".equalsIgnoreCase(ctx.getTipoDeclaracion())
                    ? (ctx.getComunaInscripcionId() != null ? ctx.getComunaInscripcionId() : ctx.getComunaDesembarqueId())
                    : (ctx.getComunaDesembarqueId() != null ? ctx.getComunaDesembarqueId() : ctx.getComunaInscripcionId());
        }

        CuotaExtraccionService.EvaluacionCuotaResult cuotaRes;
        if (Boolean.TRUE.equals(ctx.getEsEdicion())) {
            cuotaRes = cuotaExtraccionService.evaluarCuotaDeclaracion(
                    ctx.getTipoDeclaracion(),
                    ctx.getUsuarioId(),
                    ctx.getAmerbId(),
                    ctx.getEspecieId(),
                    ctx.getExtraccionTipoId(),
                    comunaImputacion,
                    ctx.getFechaExtraccion(),
                    ctx.getFechaDeclaracion(),
                    ctx.getDesembarqueKg(),
                    capturaCalculada,
                    true);
        } else {
            cuotaRes = cuotaExtraccionService.evaluarCuotaDeclaracion(
                    ctx.getTipoDeclaracion(),
                    ctx.getUsuarioId(),
                    ctx.getAmerbId(),
                    ctx.getEspecieId(),
                    ctx.getExtraccionTipoId(),
                    comunaImputacion,
                    ctx.getFechaExtraccion(),
                    ctx.getFechaDeclaracion(),
                    ctx.getDesembarqueKg(),
                    capturaCalculada);
        }

        if (cuotaRes.getMarcas() != null && !cuotaRes.getMarcas().isEmpty()) {
            marcas.addAll(cuotaRes.getMarcas());
        } else if (cuotaRes.getMarca() != null) {
            marcas.add(MarcaItem.builder()
                    .marca(cuotaRes.getMarca())
                    .detalle(cuotaRes.getMensaje())
                    .reglaId(cuotaRes.getCuotaAplicada() != null ? cuotaRes.getCuotaAplicada().getId() : null)
                    .build());
        }

        if (cuotaRes.isBloquear()) {
            return ResultadoValidacion.builder()
                    .decision(DecisionValidacion.RECHAZAR)
                    .motivoRechazo(cuotaRes.getMensaje())
                    .marcas(marcas)
                    .capturaCalculada(capturaCalculada)
                    .factorAplicado(factorAplicado)
                    .factorConversionId(factorConversionId)
                    .build();
        }

        if (cuotaRes.getMarca() != null) {
            advertencias.add(cuotaRes.getMensaje());
        }

        // ---------------------------------------------------------------------
        // 4. Evaluación de Límite de Extracción Diario (LED)
        // ---------------------------------------------------------------------
        LimiteExtraccionDiarioService.EvaluacionLedResult ledRes = limiteExtraccionDiarioService.evaluar(
                ctx.getTipoDeclaracion(),
                ctx.getEmbarcacionId(),
                ctx.getUsuarioId(),
                ctx.getBuzoId(),
                ctx.getEspecieId(),
                ctx.getExtraccionTipoId(),
                regionId,
                ctx.getFechaDeclaracion(),
                ctx.getDesembarqueKg(),
                capturaCalculada);

        if (ledRes.isExcede()) {
            marcas.add(MarcaItem.builder()
                    .marca("LED_EXCEDIDO")
                    .detalle(ledRes.getMensaje())
                    .reglaId(ledRes.getReglaAplicada() != null ? ledRes.getReglaAplicada().getId() : null)
                    .build());
            if (ledRes.isBloquear()) {
                return ResultadoValidacion.builder()
                        .decision(DecisionValidacion.RECHAZAR)
                        .motivoRechazo(ledRes.getMensaje())
                        .marcas(marcas)
                        .capturaCalculada(capturaCalculada)
                        .factorAplicado(factorAplicado)
                        .factorConversionId(factorConversionId)
                        .build();
            } else {
                advertencias.add(ledRes.getMensaje());
            }
        }

        // ---------------------------------------------------------------------
        // 5. Desembarque Atípico (Auditoría de Pesaje)
        // ---------------------------------------------------------------------
        double umbralAtipico = configuracionGeneralService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
        if (ctx.getDesembarqueKg() != null && ctx.getDesembarqueKg().doubleValue() > umbralAtipico) {
            com.trazalga.api.services.hallazgos.CriterioHallazgo crit = com.trazalga.api.services.hallazgos.CriterioHallazgo.deKilos(
                    "desembarque_umbral_atipico_kg",
                    umbralAtipico,
                    ctx.getDesembarqueKg().doubleValue()
            );
            String msgAtipico = crit.texto();
            marcas.add(MarcaItem.builder()
                    .marca("DESEMBARQUE_ATIPICO")
                    .detalle(msgAtipico)
                    .reglaId(null)
                    .criterio(crit)
                    .build());
            advertencias.add(msgAtipico);
        }

        DecisionValidacion decision = marcas.isEmpty() ? DecisionValidacion.PERMITIR : DecisionValidacion.MARCAR;

        return ResultadoValidacion.builder()
                .decision(decision)
                .marcas(marcas)
                .advertencias(advertencias)
                .capturaCalculada(capturaCalculada)
                .factorAplicado(factorAplicado)
                .factorConversionId(factorConversionId)
                .build();
    }

    // =========================================================================
    // INDICADOR 8 — VERIFICACIÓN Y MARCA DOBLE_OPERACION AL GUARDAR (T8.2)
    // =========================================================================

    public Optional<com.trazalga.api.models.DeclaracionMarcaModel> verificarDobleOperacion(
            Long usuarioId, Double lat, Double lon, Date fechaDeclaracion, String hora,
            String tipo, Long id, String folio) {

        if (usuarioId == null || lat == null || lon == null || fechaDeclaracion == null) {
            return Optional.empty();
        }

        java.time.LocalDateTime targetTs = ReportService.parseTimestamp(fechaDeclaracion, hora);
        if (targetTs == null) {
            return Optional.empty();
        }

        return verificarDobleOperacion(usuarioId, lat, lon, targetTs, tipo, id, folio);
    }

    public Optional<com.trazalga.api.models.DeclaracionMarcaModel> verificarDobleOperacion(
            Long usuarioId, double lat, double lon, java.time.LocalDateTime timestamp, String tipo, Long id) {
        return verificarDobleOperacion(usuarioId, lat, lon, timestamp, tipo, id, null);
    }

    public Optional<com.trazalga.api.models.DeclaracionMarcaModel> verificarDobleOperacion(
            Long usuarioId, double lat, double lon, java.time.LocalDateTime targetTs,
            String tipo, Long id, String folio) {

        if (usuarioId == null || configuracionGeneralService == null) {
            return Optional.empty();
        }

        boolean activo = configuracionGeneralService.getBoolean("doble_op_activo", true);
        if (!activo) {
            return Optional.empty();
        }

        if (reportRepository == null || declaracionMarcaService == null) {
            return Optional.empty();
        }

        double distMin = configuracionGeneralService.getDouble("doble_op_distancia_min_km", 5.0);
        double velMax = configuracionGeneralService.getDouble("doble_op_velocidad_max_kmh", 80.0);
        int ventanaMin = configuracionGeneralService.getInt("doble_op_ventana_min_minutos", 30);

        List<java.util.Map<String, Object>> adyacentes = reportRepository.buscarAdyacentesConGeo(usuarioId, id, tipo);
        if (adyacentes == null || adyacentes.isEmpty()) {
            return Optional.empty();
        }

        // Buscar las declaraciones adyacentes (inmediatamente anterior y posterior) del mismo usuario
        java.util.Map<String, Object> anterior = null;
        java.time.LocalDateTime anteriorTs = null;

        java.util.Map<String, Object> posterior = null;
        java.time.LocalDateTime posteriorTs = null;

        for (java.util.Map<String, Object> otra : adyacentes) {
            Double otraLat = (Double) otra.get("latitud");
            Double otraLon = (Double) otra.get("longitud");
            if (otraLat == null || otraLon == null) continue;

            java.time.LocalDateTime ts = ReportService.parseTimestamp(
                    (Date) otra.get("fechaDeclaracion"), (String) otra.get("hora"));
            if (ts == null) continue;

            if (!ts.isAfter(targetTs)) {
                if (anteriorTs == null || ts.isAfter(anteriorTs)) {
                    anterior = otra;
                    anteriorTs = ts;
                }
            } else {
                if (posteriorTs == null || ts.isBefore(posteriorTs)) {
                    posterior = otra;
                    posteriorTs = ts;
                }
            }
        }

        List<java.util.Map<String, Object>> candidatos = new java.util.ArrayList<>();
        if (anterior != null) candidatos.add(anterior);
        if (posterior != null) candidatos.add(posterior);

        for (java.util.Map<String, Object> otra : candidatos) {
            Double otraLat = (Double) otra.get("latitud");
            Double otraLon = (Double) otra.get("longitud");
            java.time.LocalDateTime otraTs = ReportService.parseTimestamp(
                    (Date) otra.get("fechaDeclaracion"), (String) otra.get("hora"));
            if (otraTs == null) continue;

            double d = ReportService.haversineKm(lat, lon, otraLat, otraLon);
            double deltaMillis = Math.abs(java.time.Duration.between(targetTs, otraTs).toMillis());
            double deltaHoras = deltaMillis / (3600.0 * 1000.0);
            double deltaMinutos = deltaMillis / (60.0 * 1000.0);
            double v = deltaHoras > 0 ? (d / deltaHoras) : (d > 0 ? 9999.0 : 0.0);

            if (d >= distMin && (v > velMax || deltaMinutos < ventanaMin)) {
                String otraFolio = otra.get("folio") != null ? otra.get("folio").toString() : "s/f";
                String targetFolio = folio != null ? folio : (tipo + "-" + id);
                String detalle = String.format(java.util.Locale.US,
                        "Declaración %s en (%.4f,%.4f) a %.1f km de %s con %.0f min de diferencia (velocidad implícita %.1f km/h)",
                        targetFolio, lat, lon, d, otraFolio, deltaMinutos, v);

                com.trazalga.api.models.DeclaracionMarcaModel marca =
                        declaracionMarcaService.marcar(tipo, id, "DOBLE_OPERACION", detalle, null);
                return Optional.of(marca);
            }
        }

        return Optional.empty();
    }

    // =========================================================================
    // INDICADOR 9 — ORIGEN REAL VS GPS CAPTURADO (Res. 25-sep / T9.3)
    // =========================================================================

    public static class PuntoReferencia {
        private final String tipo;
        private final String nombre;
        private final Double lat;
        private final Double lon;

        public PuntoReferencia(String tipo, String nombre, Double lat, Double lon) {
            this.tipo = tipo;
            this.nombre = nombre;
            this.lat = lat;
            this.lon = lon;
        }

        public String getTipo() { return tipo; }
        public String getNombre() { return nombre; }
        public Double getLat() { return lat; }
        public Double getLon() { return lon; }
    }

    public PuntoReferencia resolverReferencia(Long amerbId, Long caletaId) {
        // 1. AMERB -> centroide
        if (amerbId != null && amerbRepository != null) {
            com.trazalga.api.models.AmerbModel amerb = amerbRepository.findById(amerbId).orElse(null);
            if (amerb != null && amerb.getLatitud() != null && amerb.getLongitud() != null) {
                return new PuntoReferencia("AMERB", amerb.getNombre(), amerb.getLatitud(), amerb.getLongitud());
            }
        }
        // 2. Caleta con coordenadas directas
        if (caletaId != null && caletaRepository != null) {
            com.trazalga.api.models.CaletaModel caleta = caletaRepository.findById(caletaId).orElse(null);
            if (caleta != null && caleta.getLatitud() != null && caleta.getLongitud() != null) {
                return new PuntoReferencia("CALETA", caleta.getNombre(), caleta.getLatitud(), caleta.getLongitud());
            }
            // 3. Varadero asociado de la caleta
            if (caleta != null && caleta.getVaradero() != null &&
                    caleta.getVaradero().getLatitud() != null && caleta.getVaradero().getLongitud() != null) {
                return new PuntoReferencia("VARADERO", caleta.getVaradero().getNombre(),
                        caleta.getVaradero().getLatitud(), caleta.getVaradero().getLongitud());
            }
        }
        return null; // sin referencia
    }

    public Optional<com.trazalga.api.models.DeclaracionMarcaModel> verificarOrigenGeo(
            String tipo,
            Long id,
            String folio,
            Double lat,
            Double lon,
            Double precisionM,
            Boolean envioOffline,
            Long caletaId,
            Long amerbId) {
        if (!configuracionGeneralService.getBoolean("origen_geo_activo", true)) {
            return Optional.empty();
        }
        if (lat == null || lon == null) {
            return Optional.empty(); // sin GPS, no se puede evaluar
        }

        double distanciaMaxKm = configuracionGeneralService.getDouble("origen_geo_distancia_max_km", 30.0);
        double precisionMaxM = configuracionGeneralService.getDouble("origen_geo_precision_max_m", 500.0);

        PuntoReferencia ref = resolverReferencia(amerbId, caletaId);
        if (ref == null) {
            // Sin referencia: modo degradado, informa pero no marca
            return Optional.empty();
        }

        double distanciaKm = ReportService.haversineKm(lat, lon, ref.getLat(), ref.getLon());

        if (distanciaKm > distanciaMaxKm) {
            boolean precisionAceptable = precisionM == null || precisionM <= precisionMaxM;

            if (precisionAceptable) {
                String motivo = String.format(java.util.Locale.US,
                        "GPS a %.1f km de %s (%s). Referencia: (%.4f,%.4f). GPS: (%.4f,%.4f). Precisión: %.0f m. Offline: %s",
                        distanciaKm, ref.getNombre(), ref.getTipo(),
                        ref.getLat(), ref.getLon(), lat, lon,
                        precisionM != null ? precisionM : 0.0,
                        Boolean.TRUE.equals(envioOffline) ? "sí" : "no");

                if (declaracionMarcaService != null) {
                    return Optional.of(declaracionMarcaService.marcar(
                            tipo, id, "ORIGEN_GEO_INCONSISTENTE", motivo, null));
                }
            }
        }
        return Optional.empty();
    }
}
