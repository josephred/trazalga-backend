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
        // 3. Evaluación de Cuotas de Extracción
        // ---------------------------------------------------------------------
        Long comunaImputacion = "RECOLECTOR".equalsIgnoreCase(ctx.getTipoDeclaracion())
                ? (ctx.getComunaInscripcionId() != null ? ctx.getComunaInscripcionId() : ctx.getComunaDesembarqueId())
                : (ctx.getComunaDesembarqueId() != null ? ctx.getComunaDesembarqueId() : ctx.getComunaInscripcionId());

        CuotaExtraccionService.EvaluacionCuotaResult cuotaRes = cuotaExtraccionService.evaluarCuotaDeclaracion(
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

        if (cuotaRes.getMarca() != null) {
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
            String msgAtipico = String.format(java.util.Locale.US, "Faena de %.2f kg supera el umbral operativo de %.2f kg",
                    ctx.getDesembarqueKg().doubleValue(), umbralAtipico);
            marcas.add(MarcaItem.builder()
                    .marca("DESEMBARQUE_ATIPICO")
                    .detalle(msgAtipico)
                    .reglaId(null)
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
}
