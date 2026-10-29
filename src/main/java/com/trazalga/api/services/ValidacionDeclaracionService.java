package com.trazalga.api.services;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.dto.ResultadoValidacion.DecisionValidacion;
import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.services.geo.ReferenciaOrigenService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import com.trazalga.api.services.hallazgos.HallazgoFactory;
import com.trazalga.api.services.hallazgos.HallazgoService;

@Service
public class ValidacionDeclaracionService {

    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");

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
    private HallazgoService hallazgoService;

    @Autowired(required = false)
    private HallazgoFactory hallazgoFactory;

    @Autowired(required = false)
    private ReferenciaOrigenService referenciaOrigenService;

    @Autowired(required = false)
    private com.trazalga.api.repositories.ReportRepository reportRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.ICaletaRepository caletaRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IAmerbRepository amerbRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IHumedadEstadoRepository humedadEstadoRepository;

    @Autowired(required = false)
    private com.trazalga.api.services.cuotas.ResolutorImputacionTerritorial resolutorImputacion;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService imputacionCruzadaVedaService;

    public void setImputacionCruzadaVedaService(com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService imputacionCruzadaVedaService) {
        this.imputacionCruzadaVedaService = imputacionCruzadaVedaService;
    }

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.trazalga.api.services.indicadores.DesembarqueAtipicoService desembarqueAtipicoService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.trazalga.api.services.parametros.ParametrosService parametrosService;

    public void setParametrosService(com.trazalga.api.services.parametros.ParametrosService parametrosService) {
        this.parametrosService = parametrosService;
    }

    public void setReferenciaOrigenService(ReferenciaOrigenService referenciaOrigenService) {
        this.referenciaOrigenService = referenciaOrigenService;
    }

    public void setHallazgoFactory(HallazgoFactory hallazgoFactory) {
        this.hallazgoFactory = hallazgoFactory;
    }

    public void setHallazgoService(HallazgoService hallazgoService) {
        this.hallazgoService = hallazgoService;
    }

    private HallazgoFactory getHallazgoFactory() {
        if (this.hallazgoFactory == null) {
            this.hallazgoFactory = new HallazgoFactory();
        }
        return this.hallazgoFactory;
    }

    private com.trazalga.api.services.parametros.ParametrosService getParametrosService() {
        if (this.parametrosService != null) {
            return this.parametrosService;
        }
        if (this.configuracionGeneralService != null) {
            return new com.trazalga.api.services.parametros.ParametrosService(this.configuracionGeneralService);
        }
        return null;
    }

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

        // Comuna de imputación de cuota (TC.8). Se resuelve antes de la veda porque la imputación
        // cruzada por veda (refinamiento 23-10) depende de las cuotas de esa comuna.
        Long comunaImputacion;
        if (resolutorImputacion != null) {
            comunaImputacion = resolutorImputacion.resolver(ctx.getTipoDeclaracion()).comunaImputacion(ctx);
        } else {
            comunaImputacion = "RECOLECTOR".equalsIgnoreCase(ctx.getTipoDeclaracion())
                    ? (ctx.getComunaInscripcionId() != null ? ctx.getComunaInscripcionId() : ctx.getComunaDesembarqueId())
                    : (ctx.getComunaDesembarqueId() != null ? ctx.getComunaDesembarqueId() : ctx.getComunaInscripcionId());
        }

        // Método con que la declaración consume cuota: el declarado, salvo imputación cruzada por veda.
        Long metodoCuotaId = ctx.getExtraccionTipoId();
        Long extraccionTipoImputacionId = null;
        String imputacionDetalle = null;

        if (vedaRes.isEnVeda()) {
            if (vedaRes.isBloquear()) {
                marcas.add(MarcaItem.builder()
                        .marca("EN_VEDA")
                        .detalle(vedaRes.getMensaje())
                        .reglaId(vedaRes.getVedaAplicada() != null ? vedaRes.getVedaAplicada().getId() : null)
                        .build());
                return ResultadoValidacion.builder()
                        .decision(DecisionValidacion.RECHAZAR)
                        .motivoRechazo(vedaRes.getMensaje())
                        .marcas(marcas)
                        .capturaCalculada(capturaCalculada)
                        .factorAplicado(factorAplicado)
                        .factorConversionId(factorConversionId)
                        .build();
            }

            // Refinamiento 23-10 · punto 1: un método en veda sin cuota propia (barreteado) se imputa a la
            // cuota del método de destino (varado) de la misma especie, comuna y período. La alerta se mantiene.
            String detalleVeda = vedaRes.getMensaje();
            if (imputacionCruzadaVedaService != null) {
                com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService.Resultado imp =
                        imputacionCruzadaVedaService.resolver(
                                com.trazalga.api.services.cuotas.ImputacionCruzadaVedaService.ContextoImputacion.builder()
                                        .perfil(ctx.getTipoDeclaracion())
                                        .usuarioId(ctx.getUsuarioId())
                                        .amerbId(ctx.getAmerbId())
                                        .especieId(ctx.getEspecieId())
                                        .extraccionTipoId(ctx.getExtraccionTipoId())
                                        .comunaImputacionId(comunaImputacion)
                                        .fechaExtraccion(ctx.getFechaExtraccion())
                                        .fechaDeclaracion(ctx.getFechaDeclaracion())
                                        .enVeda(true)
                                        .build());
                if (imp != null) {
                    if (imp.isCruzada()) {
                        metodoCuotaId = imp.getMetodoImputacionId();
                        extraccionTipoImputacionId = imp.getMetodoImputacionId();
                    }
                    imputacionDetalle = imp.texto(capturaCalculada);
                    if (imputacionDetalle != null) {
                        detalleVeda = (detalleVeda != null ? detalleVeda + ". " : "") + imputacionDetalle;
                    }
                }
            }
            marcas.add(MarcaItem.builder()
                    .marca("EN_VEDA")
                    .detalle(detalleVeda)
                    .reglaId(vedaRes.getVedaAplicada() != null ? vedaRes.getVedaAplicada().getId() : null)
                    .build());
            advertencias.add(detalleVeda);
        }

        // ---------------------------------------------------------------------
        // 3. Evaluación de Cuotas de Extracción (TC.8)
        // ---------------------------------------------------------------------
        CuotaExtraccionService.EvaluacionCuotaResult cuotaRes;
        if (Boolean.TRUE.equals(ctx.getEsEdicion())) {
            cuotaRes = cuotaExtraccionService.evaluarCuotaDeclaracion(
                    ctx.getTipoDeclaracion(),
                    ctx.getUsuarioId(),
                    ctx.getAmerbId(),
                    ctx.getEspecieId(),
                    metodoCuotaId,
                    comunaImputacion,
                    ctx.getFechaExtraccion(),
                    ctx.getFechaDeclaracion(),
                    ctx.getDesembarqueKg(),
                    capturaCalculada,
                    true,
                    ctx.getDeclaracionId());
        } else {
            cuotaRes = cuotaExtraccionService.evaluarCuotaDeclaracion(
                    ctx.getTipoDeclaracion(),
                    ctx.getUsuarioId(),
                    ctx.getAmerbId(),
                    ctx.getEspecieId(),
                    metodoCuotaId,
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
                    .extraccionTipoImputacionId(extraccionTipoImputacionId)
                    .imputacionDetalle(imputacionDetalle)
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
                        .extraccionTipoImputacionId(extraccionTipoImputacionId)
                        .imputacionDetalle(imputacionDetalle)
                        .build();
            } else {
                advertencias.add(ledRes.getMensaje());
            }
        }

        // ---------------------------------------------------------------------
        // 5. Desembarque Atípico (Auditoría de Pesaje) (TD.1 / TD.2)
        // ---------------------------------------------------------------------
        if (ctx.getDesembarqueKg() != null) {
            double kilos = ctx.getDesembarqueKg().doubleValue();
            Date fDecl = ctx.getFechaDeclaracion() != null ? ctx.getFechaDeclaracion() : new Date();
            Long comId = ctx.getComunaDesembarqueId() != null ? ctx.getComunaDesembarqueId() : ctx.getComunaInscripcionId();

            List<Double> historico = java.util.Collections.emptyList();
            com.trazalga.api.services.parametros.ParametrosService ps = getParametrosService();
            boolean estadisticoActivo = ps != null && ps.desembarque() != null
                    && ps.desembarque().estadisticoActivo();

            if (estadisticoActivo && desembarqueAtipicoService != null) {
                historico = desembarqueAtipicoService.obtenerHistoricoKilos(
                        ctx.getTipoDeclaracion(),
                        ctx.getCaletaId(),
                        comId,
                        ctx.getEspecieId(),
                        ctx.getHumedadEstadoId(),
                        fDecl,
                        ctx.getDeclaracionId());
            }

            com.trazalga.api.services.indicadores.ContextoEvaluacionAtipico ctxAtipico =
                    com.trazalga.api.services.indicadores.ContextoEvaluacionAtipico.builder()
                            .tipo(ctx.getTipoDeclaracion())
                            .declaracionId(ctx.getDeclaracionId())
                            .kilos(kilos)
                            .perfil(ctx.getTipoDeclaracion())
                            .especieId(ctx.getEspecieId())
                            .humedadId(ctx.getHumedadEstadoId())
                            .caletaId(ctx.getCaletaId())
                            .comunaId(comId)
                            .usuarioId(ctx.getUsuarioId())
                            .fechaDeclaracion(fDecl)
                            .historicoKilos(historico)
                            .build();

            List<com.trazalga.api.services.indicadores.ResultadoCriterioAtipico> resultadosAtipicos;
            if (desembarqueAtipicoService != null) {
                resultadosAtipicos = desembarqueAtipicoService.evaluarAtipicos(ctxAtipico);
            } else {
                double umbralAtipico = configuracionGeneralService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
                if (kilos > umbralAtipico) {
                    com.trazalga.api.services.hallazgos.CriterioHallazgo crit =
                            com.trazalga.api.services.hallazgos.CriterioHallazgo.deKilos(
                                    "desembarque_umbral_atipico_kg", umbralAtipico, kilos);
                    resultadosAtipicos = List.of(com.trazalga.api.services.indicadores.ResultadoCriterioAtipico.builder()
                            .atipico(true)
                            .criterioClave("UMBRAL_ABSOLUTO")
                            .parametro("desembarque_umbral_atipico_kg")
                            .umbral(String.valueOf(umbralAtipico))
                            .valor(String.valueOf(kilos))
                            .unidad("kg")
                            .texto(crit.texto())
                            .build());
                } else {
                    resultadosAtipicos = java.util.Collections.emptyList();
                }
            }

            for (com.trazalga.api.services.indicadores.ResultadoCriterioAtipico resAtipico : resultadosAtipicos) {
                com.trazalga.api.services.hallazgos.CriterioHallazgo crit =
                        new com.trazalga.api.services.hallazgos.CriterioHallazgo(
                                resAtipico.getParametro(),
                                resAtipico.getUmbral(),
                                resAtipico.getValor(),
                                resAtipico.getUnidad());
                String msgAtipico = resAtipico.getTexto();
                String reglaAtipico = (resAtipico.getCriterioClave() != null && !resAtipico.getCriterioClave().isBlank())
                        ? resAtipico.getCriterioClave().trim()
                        : (resAtipico.getParametro() != null && resAtipico.getParametro().contains("sigma") ? "DESVIACION_ESTADISTICA" : "UMBRAL_ABSOLUTO");
                marcas.add(MarcaItem.builder()
                        .marca("DESEMBARQUE_ATIPICO")
                        .detalle(msgAtipico)
                        .regla(reglaAtipico)
                        .reglaId(null)
                        .criterio(crit)
                        .build());
                advertencias.add(msgAtipico);
            }
        }

        // ---------------------------------------------------------------------
        // 6. Coherencia de Humedad desde la Extracción (TH.2 / Res. 3602)
        // ---------------------------------------------------------------------
        com.trazalga.api.services.parametros.ParametrosService psCoh = getParametrosService();
        com.trazalga.api.services.indicadores.CoherenciaHumedad.ParametrosCoherencia pCoh =
                psCoh != null ? psCoh.coherenciaHumedad() : null;
        if (pCoh != null && pCoh.isActivo()) {
            String humNom = ctx.getHumedadNombre();
            if ((humNom == null || humNom.isBlank()) && ctx.getHumedadEstadoId() != null && humedadEstadoRepository != null) {
                humNom = humedadEstadoRepository.findById(ctx.getHumedadEstadoId())
                        .map(com.trazalga.api.models.HumedadEstadoModel::getNombre)
                        .orElse(null);
            }
            java.time.LocalDateTime momentoDecl = ReportService.parseTimestamp(ctx.getFechaDeclaracion(), ctx.getHora());
            com.trazalga.api.services.indicadores.CoherenciaHumedad.ResultadoCoherencia resCoh =
                    com.trazalga.api.services.indicadores.CoherenciaHumedad.evaluar(
                            humNom, ctx.getFechaExtraccion(), momentoDecl, pCoh);
            if (resCoh.isIncoherente()) {
                marcas.add(MarcaItem.builder()
                        .marca("HUMEDAD_INCOHERENTE")
                        .regla("TH2_ORIGEN")
                        .detalle(resCoh.getExplicacion())
                        .criterio(resCoh.getCriterio())
                        .build());
                advertencias.add(resCoh.getExplicacion());
            }
        }

        DecisionValidacion decision = marcas.isEmpty() ? DecisionValidacion.PERMITIR : DecisionValidacion.MARCAR;

        return ResultadoValidacion.builder()
                .decision(decision)
                .marcas(marcas)
                .advertencias(advertencias)
                .capturaCalculada(capturaCalculada)
                .factorAplicado(factorAplicado)
                .factorConversionId(factorConversionId)
                .extraccionTipoImputacionId(extraccionTipoImputacionId)
                .imputacionDetalle(imputacionDetalle)
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
    // INDICADOR 9 — ORIGEN REAL VS GPS CAPTURADO (TG.2 / TG.5)
    // =========================================================================

    private com.trazalga.api.dto.geo.PuntoReferencia resolverReferenciaConServicioORespaldo(
            String tipo, Long caletaId, Long amerbId, Long varaderoId, String textoVaradero, Long comunaId) {
        if (referenciaOrigenService != null) {
            com.trazalga.api.dto.geo.PuntoReferencia ref =
                    referenciaOrigenService.resolverPorIds(tipo, caletaId, amerbId, varaderoId, textoVaradero, comunaId);
            if (ref != null) {
                return ref;
            }
        }
        // Respaldo para pruebas unitarias sin ReferenciaOrigenService inyectado
        if (amerbId != null && amerbRepository != null) {
            com.trazalga.api.models.AmerbModel amerb = amerbRepository.findById(amerbId).orElse(null);
            if (amerb != null && amerb.getLatitud() != null && amerb.getLongitud() != null) {
                String fuente = amerb.getFuenteCoordenada() != null ? amerb.getFuenteCoordenada() : "AMERB";
                return new com.trazalga.api.dto.geo.PuntoReferencia(
                        "AMERB", amerb.getId(), amerb.getNombre(), amerb.getLatitud(), amerb.getLongitud(), fuente);
            }
        }
        if (caletaId != null && caletaRepository != null) {
            com.trazalga.api.models.CaletaModel caleta = caletaRepository.findById(caletaId).orElse(null);
            if (caleta != null && caleta.getLatitud() != null && caleta.getLongitud() != null) {
                String fuente = caleta.getFuenteCoordenada() != null ? caleta.getFuenteCoordenada() : "CALETA";
                return new com.trazalga.api.dto.geo.PuntoReferencia(
                        "CALETA", caleta.getId(), caleta.getNombre(), caleta.getLatitud(), caleta.getLongitud(), fuente);
            }
            if (caleta != null && caleta.getVaradero() != null
                    && caleta.getVaradero().getLatitud() != null && caleta.getVaradero().getLongitud() != null) {
                String fuente = caleta.getVaradero().getFuenteCoordenada() != null
                        ? caleta.getVaradero().getFuenteCoordenada() : "VARADERO_CALETA";
                return new com.trazalga.api.dto.geo.PuntoReferencia(
                        "VARADERO", caleta.getVaradero().getId(), caleta.getVaradero().getNombre(),
                        caleta.getVaradero().getLatitud(), caleta.getVaradero().getLongitud(), fuente);
            }
        }
        return null;
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
        return verificarOrigenGeo(tipo, id, folio, lat, lon, precisionM, envioOffline, caletaId, amerbId, null, null, null);
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
            Long amerbId,
            Long varaderoId,
            String textoVaradero,
            Long comunaId) {
        if (!configuracionGeneralService.getBoolean("origen_geo_activo", true)) {
            return Optional.empty();
        }
        if (lat == null || lon == null) {
            return Optional.empty(); // sin GPS, no se puede evaluar
        }

        double distanciaMaxKm = configuracionGeneralService.getDouble("origen_geo_distancia_max_km", 30.0);
        double precisionMaxM = configuracionGeneralService.getDouble("origen_geo_precision_max_m", 500.0);

        com.trazalga.api.dto.geo.PuntoReferencia ref =
                resolverReferenciaConServicioORespaldo(tipo, caletaId, amerbId, varaderoId, textoVaradero, comunaId);
        if (ref == null || ref.getLat() == null || ref.getLon() == null) {
            // Sin referencia: modo degradado, informa pero no marca
            return Optional.empty();
        }

        double distanciaKm = ReportService.haversineKm(lat, lon, ref.getLat(), ref.getLon());

        if (distanciaKm > distanciaMaxKm) {
            boolean precisionAceptable = precisionM == null || precisionM <= precisionMaxM;

            if (precisionAceptable) {
                CriterioHallazgo criterio = CriterioHallazgo.deKilometros(
                        "origen_geo_distancia_max_km", distanciaMaxKm, distanciaKm);
                String motivo = redactarMotivoOrigenGeoEsCl(
                        distanciaKm, distanciaMaxKm, precisionM, envioOffline, ref);

                DeclaracionMarcaModel construida = getHallazgoFactory().crear(
                        tipo, id, "ORIGEN_GEO_INCONSISTENTE", null, criterio, motivo, "VALIDACION");

                if (hallazgoService != null) {
                    return Optional.of(hallazgoService.registrar(
                            tipo, id, "ORIGEN_GEO_INCONSISTENTE", null, criterio, motivo, "VALIDACION"));
                }
                if (declaracionMarcaService != null) {
                    DeclaracionMarcaModel marcada = declaracionMarcaService.marcar(
                            tipo, id, "ORIGEN_GEO_INCONSISTENTE", motivo, null);
                    if (marcada != null) {
                        if (marcada.getClaveIdempotencia() == null) {
                            marcada.setClaveIdempotencia(construida.getClaveIdempotencia());
                        }
                        if (marcada.getCriterioParametro() == null) {
                            marcada.setCriterioParametro(construida.getCriterioParametro());
                            marcada.setCriterioUmbral(construida.getCriterioUmbral());
                            marcada.setCriterioValor(construida.getCriterioValor());
                            marcada.setCriterioUnidad(construida.getCriterioUnidad());
                        }
                        if (marcada.getDetalle() == null) {
                            marcada.setDetalle(motivo);
                        }
                        return Optional.of(marcada);
                    }
                }
                return Optional.of(construida);
            }
        }
        return Optional.empty();
    }

    private String redactarMotivoOrigenGeoEsCl(
            double distanciaKm,
            double distanciaMaxKm,
            Double precisionM,
            Boolean envioOffline,
            com.trazalga.api.dto.geo.PuntoReferencia ref) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(LOCALE_CL);
        sym.setDecimalSeparator(',');
        sym.setGroupingSeparator('.');

        String distStr = new DecimalFormat("#,##0.0", sym).format(distanciaKm);
        String umbralStr = (distanciaMaxKm == (long) distanciaMaxKm)
                ? new DecimalFormat("#,##0", sym).format((long) distanciaMaxKm)
                : new DecimalFormat("#,##0.0", sym).format(distanciaMaxKm);
        String precStr = new DecimalFormat("#,##0", sym).format(precisionM != null ? Math.round(precisionM) : 0L);

        String tipoEntidad = ref.getTipo() != null ? ref.getTipo().toUpperCase(LOCALE_CL) : "";
        String entidadFrase;
        if ("VARADERO".equals(tipoEntidad)) {
            entidadFrase = "del varadero " + ref.getNombre();
        } else if ("CALETA".equals(tipoEntidad)) {
            entidadFrase = "de la caleta " + ref.getNombre();
        } else if ("AMERB".equals(tipoEntidad)) {
            entidadFrase = "del área " + ref.getNombre();
        } else {
            entidadFrase = "de " + ref.getNombre();
        }

        String fuenteRaw = ref.getFuente() != null ? ref.getFuente().toUpperCase(LOCALE_CL) : "";
        String fuenteFrase;
        if (fuenteRaw.contains("PLANILLA")) {
            fuenteFrase = "planilla Sernapesca";
        } else if ("MANUAL".equals(fuenteRaw)) {
            fuenteFrase = "edición manual";
        } else if ("VARADERO_DECLARADO".equals(fuenteRaw)) {
            fuenteFrase = "varadero declarado";
        } else if ("VARADERO_CALETA".equals(fuenteRaw)) {
            fuenteFrase = "varadero de caleta";
        } else if ("CALETA".equals(fuenteRaw)) {
            fuenteFrase = "caleta";
        } else if ("AMERB".equals(fuenteRaw)) {
            fuenteFrase = "AMERB";
        } else {
            fuenteFrase = "planilla Sernapesca";
        }

        String conexionFrase = Boolean.TRUE.equals(envioOffline)
                ? "Enviada sin conexión."
                : "Enviada con conexión.";

        return String.format(
                "Declaración enviada a %s km %s (referencia: %s). Umbral: %s km. Precisión GPS: %s m. %s",
                distStr, entidadFrase, fuenteFrase, umbralStr, precStr, conexionFrase);
    }

    // =========================================================================
    // TH.2 / TH.3 — COHERENCIA DE HUMEDAD DESDE LA EXTRACCIÓN (Res. 3602)
    // =========================================================================

    public Optional<DeclaracionMarcaModel> verificarCoherenciaHumedad(
            String tipo,
            Long id,
            String estadoHumedad,
            Date fechaExtraccion,
            Date fechaEvento,
            String horaEvento,
            Long usuarioId,
            Long regionId) {
        com.trazalga.api.services.parametros.ParametrosService ps = getParametrosService();
        if (ps == null) {
            return Optional.empty();
        }
        com.trazalga.api.services.indicadores.CoherenciaHumedad.ParametrosCoherencia pCoh = ps.coherenciaHumedad();
        if (pCoh == null || !pCoh.isActivo()) {
            return Optional.empty();
        }
        java.time.LocalDateTime momentoEvento = ReportService.parseTimestamp(fechaEvento, horaEvento);
        com.trazalga.api.services.indicadores.CoherenciaHumedad.ResultadoCoherencia resCoh =
                com.trazalga.api.services.indicadores.CoherenciaHumedad.evaluar(
                        estadoHumedad, fechaExtraccion, momentoEvento, pCoh);
        if (!resCoh.isIncoherente()) {
            return Optional.empty();
        }

        CriterioHallazgo criterio = resCoh.getCriterio();
        String motivo = resCoh.getExplicacion();
        DeclaracionMarcaModel construida = getHallazgoFactory().crear(
                tipo, id, "HUMEDAD_INCOHERENTE", null, criterio, motivo, "VALIDACION");

        if (hallazgoService != null) {
            return Optional.of(hallazgoService.registrar(
                    tipo, id, "HUMEDAD_INCOHERENTE", null, criterio, motivo, "VALIDACION", usuarioId, regionId));
        }
        if (declaracionMarcaService != null) {
            DeclaracionMarcaModel marcada = declaracionMarcaService.marcar(
                    tipo, id, "HUMEDAD_INCOHERENTE", motivo, null);
            if (marcada != null) {
                if (marcada.getClaveIdempotencia() == null) {
                    marcada.setClaveIdempotencia(construida.getClaveIdempotencia());
                }
                if (marcada.getCriterioParametro() == null && criterio != null) {
                    marcada.setCriterioParametro(criterio.parametro());
                    marcada.setCriterioUmbral(criterio.umbral());
                    marcada.setCriterioValor(criterio.valorObservado());
                    marcada.setCriterioUnidad(criterio.unidad());
                }
                if (marcada.getDetalle() == null) {
                    marcada.setDetalle(motivo);
                }
                return Optional.of(marcada);
            }
        }
        return Optional.of(construida);
    }
}
