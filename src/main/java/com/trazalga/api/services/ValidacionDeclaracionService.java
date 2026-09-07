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
            if (vedaRes.isBloquear()) {
                return ResultadoValidacion.builder()
                        .decision(DecisionValidacion.RECHAZAR)
                        .motivoRechazo(vedaRes.getMensaje())
                        .capturaCalculada(capturaCalculada)
                        .factorAplicado(factorAplicado)
                        .factorConversionId(factorConversionId)
                        .build();
            } else {
                marcas.add(MarcaItem.builder()
                        .marca("EN_VEDA")
                        .detalle(vedaRes.getMensaje())
                        .reglaId(vedaRes.getVedaAplicada() != null ? vedaRes.getVedaAplicada().getId() : null)
                        .build());
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

        if (cuotaRes.isBloquear()) {
            return ResultadoValidacion.builder()
                    .decision(DecisionValidacion.RECHAZAR)
                    .motivoRechazo(cuotaRes.getMensaje())
                    .capturaCalculada(capturaCalculada)
                    .factorAplicado(factorAplicado)
                    .factorConversionId(factorConversionId)
                    .build();
        }

        if (cuotaRes.getMarca() != null) {
            marcas.add(MarcaItem.builder()
                    .marca(cuotaRes.getMarca())
                    .detalle(cuotaRes.getMensaje())
                    .reglaId(cuotaRes.getCuotaAplicada() != null ? cuotaRes.getCuotaAplicada().getId() : null)
                    .build());
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
            if (ledRes.isBloquear()) {
                return ResultadoValidacion.builder()
                        .decision(DecisionValidacion.RECHAZAR)
                        .motivoRechazo(ledRes.getMensaje())
                        .capturaCalculada(capturaCalculada)
                        .factorAplicado(factorAplicado)
                        .factorConversionId(factorConversionId)
                        .build();
            } else {
                marcas.add(MarcaItem.builder()
                        .marca("LED_EXCEDIDO")
                        .detalle(ledRes.getMensaje())
                        .reglaId(ledRes.getReglaAplicada() != null ? ledRes.getReglaAplicada().getId() : null)
                        .build());
                advertencias.add(ledRes.getMensaje());
            }
        }

        // ---------------------------------------------------------------------
        // 5. Desembarque Atípico (Auditoría de Pesaje)
        // ---------------------------------------------------------------------
        double umbralAtipico = configuracionGeneralService.getDouble("desembarque_umbral_atipico_kg", 5000.0);
        if (ctx.getDesembarqueKg() != null && ctx.getDesembarqueKg().doubleValue() > umbralAtipico) {
            String msgAtipico = String.format("Desembarque individual atípico: %.2f kg declarados supera el umbral de alerta preventiva (%.2f kg).",
                    ctx.getDesembarqueKg(), umbralAtipico);
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
}
