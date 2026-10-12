package com.trazalga.api.services.retencion;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.ReportRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.CadenaCustodiaService;
import com.trazalga.api.services.ReportService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import com.trazalga.api.services.hallazgos.HallazgoService;
import com.trazalga.api.services.parametros.ParametrosRetencion;
import com.trazalga.api.services.parametros.ParametrosService;

/**
 * Servicio canónico de gestión de Retención en Bodega Virtual y Bloqueo Automático (TR.1, TR.2, TR.3).
 * Administra el inventario en bodega virtual de comercializadores, la matriz humedad × semáforo (Res. 3602)
 * y el bloqueo automático preventivo de cargas excedidas (D3).
 */
@Service
public class RetencionCustodiaService {

    private static final Logger log = LoggerFactory.getLogger(RetencionCustodiaService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ParametrosService parametrosService;

    @Autowired
    private PoliticaRetencion politicaRetencion;

    @Autowired
    private HallazgoService hallazgoService;

    @Autowired
    private IDeclaracionMarcaRepository marcaRepository;

    @Autowired
    private MarcaRetencionExecutorService marcaRetencionExecutorService;

    @Autowired
    @Lazy
    private BloqueoCargaService bloqueoCargaService;

    @Autowired
    @Lazy
    private ReportRepository reportRepository;

    /**
     * TR.1: Consulta canónica de lotes actualmente en bodega virtual de comercializadores.
     * Cero consultas N+1 y enriquecimiento con política de semáforo y estado de bloqueo.
     */
    @Transactional(readOnly = true)
    public List<LoteBodegaDTO> lotesEnBodega(FiltroRetencionBodega filtro) {
        ParametrosRetencion params = parametrosService.retencion();
        LocalDateTime ahora = LocalDateTime.now();
        Set<String> marcasBloqueantes = bloqueoCargaService != null ? bloqueoCargaService.getMarcasBloqueantes() : Collections.emptySet();

        String sql = buildSqlLotesBodega(filtro);
        Query query = entityManager.createNativeQuery(sql);
        bindSqlLotesBodegaParams(query, filtro);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<LoteBodegaDTO> resultado = new ArrayList<>();

        for (Object[] r : rows) {
            Long id = col(r, 0) != null ? ((Number) col(r, 0)).longValue() : null;
            String tipo = col(r, 1) != null ? col(r, 1).toString() : "";
            String folio = col(r, 2) != null ? col(r, 2).toString() : "";
            Date fecha = toDate(col(r, 3));
            String hora = col(r, 4) != null ? col(r, 4).toString() : null;
            Double kg = col(r, 5) != null ? ((Number) col(r, 5)).doubleValue() : 0.0;
            Long especieId = col(r, 6) != null ? ((Number) col(r, 6)).longValue() : null;
            String especieNombre = col(r, 7) != null ? col(r, 7).toString() : "";
            String humedadRaw = col(r, 8) != null ? col(r, 8).toString() : "HÚMEDO";
            Long comercializadorId = col(r, 9) != null ? ((Number) col(r, 9)).longValue() : null;
            String nomComercializador = col(r, 10) != null ? col(r, 10).toString().trim() : "";
            String rutComercializador = col(r, 11) != null ? col(r, 11).toString().trim() : "";
            Long origenId = col(r, 12) != null ? ((Number) col(r, 12)).longValue() : null;
            String nomOrigen = col(r, 13) != null ? col(r, 13).toString().trim() : "";
            String rutOrigen = col(r, 14) != null ? col(r, 14).toString().trim() : "";
            String embarcacion = col(r, 15) != null ? col(r, 15).toString().trim() : null;
            Long retencionMarcaId = col(r, 16) != null ? ((Number) col(r, 16)).longValue() : null;
            String resolucionTipo = col(r, 17) != null ? col(r, 17).toString().trim() : null;
            String marcasActivasStr = col(r, 18) != null ? col(r, 18).toString().trim() : null;

            LocalDateTime inicioTs = ReportService.parseTimestamp(fecha, hora);
            double horas = CadenaCustodiaService.horasEnBodega(inicioTs, ahora);

            ResultadoEvaluacionRetencion eval = politicaRetencion.evaluar(especieId, especieNombre, humedadRaw, horas, params);

            // Determinar si está bloqueada
            boolean bloqueada = false;
            String motivoBloqueo = null;
            Long marcaBloqueanteId = retencionMarcaId;

            if (retencionMarcaId != null && !"LIBERADA".equalsIgnoreCase(resolucionTipo)) {
                bloqueada = true;
                motivoBloqueo = "Retenida por RETENCION_EXCEDIDA (hallazgo #" + retencionMarcaId + ")";
            } else if (marcasActivasStr != null && !marcasActivasStr.isBlank()) {
                String[] mTokens = marcasActivasStr.split(",");
                for (String m : mTokens) {
                    String cleanM = m.trim().toUpperCase();
                    if (marcasBloqueantes.contains(cleanM)) {
                        bloqueada = true;
                        motivoBloqueo = "Retenida por " + cleanM;
                        break;
                    }
                }
            }

            // Si el filtro exige sólo retenidas y no está bloqueada, omitir
            if (filtro != null && Boolean.TRUE.equals(filtro.getSoloRetenidas()) && !bloqueada) {
                continue;
            }

            // Si hay filtro de semáforo
            if (filtro != null && filtro.getSemaforo() != null && !filtro.getSemaforo().equalsIgnoreCase("TODOS")) {
                if (!eval.getSemaforo().equalsIgnoreCase(filtro.getSemaforo())) {
                    continue;
                }
            }

            LoteBodegaDTO dto = LoteBodegaDTO.builder()
                    .id(id)
                    .tipo(tipo)
                    .folio(folio)
                    .especieId(especieId)
                    .especie(especieNombre)
                    .estadoDeclarado(eval.getEstadoDeclarado())
                    .tramoReal(eval.getTramoReal())
                    .kg(Math.round(kg * 100.0) / 100.0)
                    .fechaDeclaracion(fecha)
                    .horaDeclaracion(hora)
                    .horasTranscurridas(horas)
                    .diasTranscurridos((int) (horas / 24))
                    .plazoMaxHoras(eval.getPlazoMaxHoras() == Integer.MAX_VALUE ? null : eval.getPlazoMaxHoras())
                    .semaforo(eval.getSemaforo())
                    .enBodega(true)
                    .destinado(false)
                    .inconsistenciaHumedad(eval.isInconsistenciaHumedad())
                    .comercializadorId(comercializadorId)
                    .actorComercializador(nomComercializador.isEmpty() ? nomOrigen : nomComercializador)
                    .rutComercializador(rutComercializador.isEmpty() ? rutOrigen : rutComercializador)
                    .origenId(origenId)
                    .actorOrigen(nomOrigen)
                    .rutOrigen(rutOrigen)
                    .embarcacion(embarcacion)
                    .bloqueada(bloqueada)
                    .motivoBloqueo(motivoBloqueo)
                    .marcaId(marcaBloqueanteId)
                    .marcasActivas(marcasActivasStr)
                    .build();

            resultado.add(dto);
        }

        return resultado;
    }

    /**
     * TR.2: Métricas agregadas del Indicador 7 sobre RetencionCustodiaService.
     * Retorna la matriz humedad × semáforo (Res. 3602), contadores en bodega y en tránsito.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerMetricasRetencion(Date startDate, Date endDate) {
        ParametrosRetencion params = parametrosService.retencion();
        if (!params.retencionActivo()) {
            Map<String, Object> disabled = new LinkedHashMap<>();
            disabled.put("activo", false);
            disabled.put("controlDesactivado", true);
            disabled.put("mensaje", "Control de retención en bodega desactivado en Administración");
            disabled.put("totalLotesEnBodega", 0);
            disabled.put("totalKgEnBodega", 0.0);
            disabled.put("totalLotesEnTransitoAPlanta", 0);
            disabled.put("cargasRetenidas", 0);
            disabled.put("humedoMaxHoras", params.humedoMaxHoras());
            disabled.put("semihumedoMaxHoras", params.semihumedoMaxHoras());
            disabled.put("semisecoMaxHoras", params.semisecoMaxHoras());
            disabled.put("preavisoPct", params.semaforoPreavisoPct());
            disabled.put("semaforoVerde", 0L);
            disabled.put("semaforoAmarillo", 0L);
            disabled.put("semaforoNaranja", 0L);
            disabled.put("semaforoRojo", 0L);
            disabled.put("lotes", Collections.emptyList());
            disabled.put("lotesRojos", Collections.emptyList());
            disabled.put("matriz", Collections.emptyMap());
            return disabled;
        }

        // Obtener la evaluación canónica por tramos de humedad desde el repositorio de reportes
        Map<String, Object> metrics = reportRepository.getRetencionPorHumedad(
                startDate, endDate,
                params.humedoMaxHoras(),
                params.semihumedoMaxHoras(),
                params.semisecoMaxHoras(),
                (int) Math.round(params.semaforoPreavisoPct())
        );

        // Contar cargas activas retenidas / bloqueadas en bodega
        FiltroRetencionBodega filtroBodega = FiltroRetencionBodega.builder()
                .startDate(startDate)
                .endDate(endDate)
                .soloEnBodega(true)
                .build();
        List<LoteBodegaDTO> lotesBodega = lotesEnBodega(filtroBodega);

        long cargasRetenidas = lotesBodega.stream()
                .filter(l -> Boolean.TRUE.equals(l.getBloqueada()))
                .count();

        // Enriquecer el listado de lotes para reflejar titularidad de comercializador y marcas de retención
        Map<String, LoteBodegaDTO> mapaBodega = lotesBodega.stream()
                .filter(l -> l.getFolio() != null && !l.getFolio().isBlank())
                .collect(Collectors.toMap(LoteBodegaDTO::getFolio, l -> l, (a, b) -> a));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotesOriginales = (List<Map<String, Object>>) metrics.get("lotes");
        if (lotesOriginales != null) {
            for (Map<String, Object> loteMap : lotesOriginales) {
                String folio = (String) loteMap.get("folio");
                if (folio != null && mapaBodega.containsKey(folio)) {
                    LoteBodegaDTO bDto = mapaBodega.get(folio);
                    loteMap.put("actorComercializador", bDto.getActorComercializador());
                    loteMap.put("bloqueada", bDto.getBloqueada());
                    loteMap.put("motivoBloqueo", bDto.getMotivoBloqueo());
                    loteMap.put("marcaId", bDto.getMarcaId());
                    loteMap.put("marcasActivas", bDto.getMarcasActivas());
                }
            }
        }

        // Calcular lotes en tránsito a planta: lotes que no están en bodega y fueron destinados
        long lotesDestinados = metrics.containsKey("lotesDestinados")
                ? ((Number) metrics.get("lotesDestinados")).longValue()
                : 0L;
        long totalLotesEnBodega = lotesBodega.size();
        long totalLotesEnTransitoAPlanta = Math.max(0L, lotesDestinados);

        metrics.put("activo", true);
        metrics.put("controlDesactivado", false);
        metrics.put("totalLotesEnBodega", totalLotesEnBodega);
        metrics.put("totalLotesEnTransitoAPlanta", totalLotesEnTransitoAPlanta);
        metrics.put("cargasRetenidas", cargasRetenidas);

        // Parámetros normativos dinámicos
        Map<String, Object> pMap = new LinkedHashMap<>();
        pMap.put("humedoMaxHoras", params.humedoMaxHoras());
        pMap.put("semihumedoMaxHoras", params.semihumedoMaxHoras());
        pMap.put("semisecoMaxHoras", params.semisecoMaxHoras());
        pMap.put("preavisoPct", params.semaforoPreavisoPct());
        pMap.put("bloqueoActivo", params.bloqueoActivo());
        pMap.put("bloqueoHumedoHoras", params.bloqueoHumedoHoras());
        pMap.put("bloqueoSemihumedoHoras", params.bloqueoSemihumedoHoras());
        pMap.put("bloqueoSemisecoHoras", params.bloqueoSemisecoHoras());
        pMap.put("bloqueoEspecies", params.bloqueoEspecies());
        pMap.put("bloqueoPreavisoPct", params.bloqueoPreavisoPct());
        metrics.put("parametros", pMap);

        return metrics;
    }

    /**
     * TR.3: Registra de forma transaccional independiente (REQUIRES_NEW) la marca bloqueante
     * RETENCION_EXCEDIDA delegando en MarcaRetencionExecutorService para garantizar proxy AOP.
     */
    @Transactional
    public DeclaracionMarcaModel registrarMarcaRetencionExcedida(
            String tipo,
            Long declaracionId,
            String motivo,
            Long usuarioDeclaranteId,
            Long regionId
    ) {
        return registrarMarcaRetencionExcedida(tipo, declaracionId, motivo, usuarioDeclaranteId, regionId, null, null, "VALIDACION");
    }

    @Transactional
    public DeclaracionMarcaModel registrarMarcaRetencionExcedida(
            String tipo,
            Long declaracionId,
            String motivo,
            Long usuarioDeclaranteId,
            Long regionId,
            String umbralParam,
            String valorParam,
            String origenParam
    ) {
        if (marcaRetencionExecutorService != null) {
            return marcaRetencionExecutorService.registrarMarcaRetencionExcedida(
                    tipo,
                    declaracionId,
                    motivo,
                    usuarioDeclaranteId,
                    regionId,
                    umbralParam,
                    valorParam,
                    origenParam
            );
        }

        // Fallback cuando se ejecuta en pruebas unitarias aisladas sin proxy AOP
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "DESCONOCIDO";
        List<DeclaracionMarcaModel> marcasPrevias = marcaRepository.findByDeclaracionTipoAndDeclaracionId(tipoNorm, declaracionId);
        boolean yaLiberada = marcasPrevias.stream()
                .anyMatch(m -> "RETENCION_EXCEDIDA".equalsIgnoreCase(m.getMarca()) && "LIBERADA".equalsIgnoreCase(m.getResolucionTipo()));

        if (yaLiberada) {
            log.info("Carga {}:{} ya fue formalmente LIBERADA por fiscalizador. Se omite re-bloqueo.", tipoNorm, declaracionId);
            return marcasPrevias.stream()
                    .filter(m -> "RETENCION_EXCEDIDA".equalsIgnoreCase(m.getMarca()))
                    .findFirst()
                    .orElse(null);
        }

        String valorObs = valorParam;
        if (valorObs == null || valorObs.isBlank()) {
            if (motivo != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*h", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(motivo);
                if (matcher.find()) {
                    valorObs = matcher.group(1);
                } else {
                    valorObs = motivo.length() <= 40 ? motivo : motivo.substring(0, 40);
                }
            } else {
                valorObs = "120+";
            }
        }
        if (valorObs.length() > 40) {
            valorObs = valorObs.substring(0, 40);
        }

        String umbral = (umbralParam != null && !umbralParam.isBlank()) ? umbralParam : "120";
        if (umbral.length() > 40) {
            umbral = umbral.substring(0, 40);
        }

        String origen = (origenParam != null && !origenParam.isBlank()) ? origenParam.trim().toUpperCase() : "VALIDACION";
        if (origen.length() > 20) {
            origen = origen.substring(0, 20);
        }

        CriterioHallazgo criterio = new CriterioHallazgo(
                "retencion_horas",
                umbral,
                valorObs,
                "h"
        );

        String detalleCausa = (motivo != null && !motivo.isBlank())
                ? motivo
                : String.format("Carga de %s supera el umbral de retención máxima de %s h.", tipoNorm, umbral);

        return hallazgoService.registrar(
                tipoNorm,
                declaracionId,
                "RETENCION_EXCEDIDA",
                "D3",
                criterio,
                detalleCausa,
                "BODEGA_VIRTUAL_AUTOMA",
                usuarioDeclaranteId,
                regionId
        );
    }

    /**
     * TR.3 / TA.6: Barrido periódico de todas las cargas en bodega virtual.
     * Evalúa tiempos de frescura, emite preavisos idempotentes y aplica bloqueo automático D3.
     */
    @Transactional
    public ResumenBarridoRetencion barrer(LocalDateTime ahora) {
        ParametrosRetencion params = parametrosService.retencion();
        log.info("Iniciando barrido de retención en bodega virtual. Bloqueo activo: {}", params.bloqueoActivo());

        FiltroRetencionBodega filtro = FiltroRetencionBodega.builder()
                .soloEnBodega(true)
                .build();
        List<LoteBodegaDTO> lotes = lotesEnBodega(filtro);

        int evaluados = lotes.size();
        int bloqueosAplicados = 0;
        int preavisosEmitidos = 0;

        for (LoteBodegaDTO lote : lotes) {
            ResultadoEvaluacionRetencion eval = politicaRetencion.evaluar(
                    lote.getEspecieId(),
                    lote.getEspecie(),
                    lote.getEstadoDeclarado(),
                    lote.getHorasTranscurridas(),
                    params
            );

            // 1. Bloqueo automático D3 si corresponde y bloqueo está activo
            if (eval.isBloqueoRequerido() && params.bloqueoActivo()) {
                if (!Boolean.TRUE.equals(lote.getBloqueada())) {
                    try {
                        registrarMarcaRetencionExcedida(
                                lote.getTipo(),
                                lote.getId(),
                                eval.getMotivoDetalle(),
                                lote.getOrigenId(),
                                null,
                                String.valueOf(eval.getPlazoMaxHoras()),
                                String.valueOf(Math.round(lote.getHorasTranscurridas())),
                                "BARRIDO"
                        );
                        bloqueosAplicados++;
                        log.warn("Bloqueo automático RETENCION_EXCEDIDA aplicado a lote {}:{} ({} h en bodega)",
                                lote.getTipo(), lote.getId(), lote.getHorasTranscurridas());
                    } catch (Exception ex) {
                        log.error("Error aplicando bloqueo a lote {}:{}: {}", lote.getTipo(), lote.getId(), ex.getMessage());
                    }
                }
            }

            // 2. Preaviso informativo de retención: SÓLO cuando el bloqueo esté activo (plan 09-10)
            if (params.bloqueoActivo() && (eval.isEnPreavisoBloqueo() || eval.isEnPreavisoSemaforo())) {
                try {
                    String reglaPreaviso = "PREAVISO_" + (eval.isEnPreavisoBloqueo() ? "BLOQUEO" : "SEMAFORO");
                    int umbralHoras = (int) Math.round(eval.getPlazoMaxHoras() * (params.semaforoPreavisoPct() / 100.0));
                    CriterioHallazgo crit = new CriterioHallazgo(
                            "preaviso_retencion",
                            String.valueOf(umbralHoras > 0 ? umbralHoras : 96),
                            String.valueOf(Math.round(lote.getHorasTranscurridas())),
                            "h"
                    );

                    hallazgoService.registrar(
                            lote.getTipo(),
                            lote.getId(),
                            "RETENCION_PREAVISO",
                            reglaPreaviso,
                            crit,
                            eval.getMotivoDetalle(),
                            "BARRIDO",
                            lote.getOrigenId(),
                            null
                    );
                    preavisosEmitidos++;
                } catch (Exception ex) {
                    log.debug("Preaviso ya registrado o no despachado para {}:{}: {}", lote.getTipo(), lote.getId(), ex.getMessage());
                }
            }
        }

        log.info("Barrido de retención finalizado: {} lotes evaluados, {} bloqueos aplicados, {} preavisos emitidos",
                evaluados, bloqueosAplicados, preavisosEmitidos);

        return new ResumenBarridoRetencion(evaluados, bloqueosAplicados, preavisosEmitidos);
    }

    public record ResumenBarridoRetencion(int lotesEvaluados, int bloqueosAplicados, int preavisosEmitidos) {}

    /**
     * TR.3: Evaluación Just-In-Time de una carga específica al momento de intentar despacharla o recibirla.
     * Si ha excedido el plazo de retención configurado para bloqueo y retencion_bloqueo_activo es true,
     * persiste inmediatamente la marca RETENCION_EXCEDIDA con REQUIRES_NEW mediante MarcaRetencionExecutorService.
     */
    public Optional<DeclaracionMarcaModel> evaluarYBloquearJustInTime(String tipo, Long id) {
        ParametrosRetencion params = parametrosService.retencion();
        if (!params.bloqueoActivo() || tipo == null || id == null) {
            return Optional.empty();
        }

        String tabla;
        switch (tipo.toUpperCase().trim()) {
            case "RECOLECTOR": tabla = "declaracion_recolector"; break;
            case "ARMADOR": tabla = "declaracion_armador"; break;
            case "AREA": tabla = "declaracion_area"; break;
            case "COMERCIALIZADOR": tabla = "declaracion_comercializador"; break;
            default: return Optional.empty();
        }

        String sql = "SELECT d.id, d.fecha_declaracion, d.hora, d.especie_id, e.nombre, COALESCE(h.nombre, 'HÚMEDO'), d.usuario_id " +
                "FROM " + tabla + " d " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "WHERE d.id = :id";

        try {
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("id", id);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            if (rows.isEmpty()) return Optional.empty();

            Object[] r = rows.get(0);
            Date fecha = toDate(r[1]);
            String hora = r[2] != null ? r[2].toString() : null;
            Long espId = r[3] != null ? ((Number) r[3]).longValue() : null;
            String espNom = r[4] != null ? r[4].toString() : "";
            String hum = r[5] != null ? r[5].toString() : "HÚMEDO";
            Long usuarioId = r[6] != null ? ((Number) r[6]).longValue() : null;

            LocalDateTime inicioTs = ReportService.parseTimestamp(fecha, hora);
            double horas = CadenaCustodiaService.horasEnBodega(inicioTs, LocalDateTime.now());

            ResultadoEvaluacionRetencion eval = politicaRetencion.evaluar(espId, espNom, hum, horas, params);
            if (eval.isBloqueoRequerido()) {
                DeclaracionMarcaModel marca = marcaRetencionExecutorService.registrarMarcaRetencionExcedida(
                        tipo, id, eval.getMotivoDetalle(), usuarioId, null,
                        String.valueOf(eval.getPlazoMaxHoras()),
                        String.valueOf(Math.round(horas)),
                        "VALIDACION");
                return Optional.ofNullable(marca);
            }
        } catch (Exception ex) {
            log.warn("Error en evaluación just-in-time para {}:{}: {}", tipo, id, ex.getMessage());
        }
        return Optional.empty();
    }

    // --- Métodos auxiliares de construcción SQL ---

    private String buildSqlLotesBodega(FiltroRetencionBodega filtro) {
        String dateFilterRecolector = "";
        String dateFilterArmador = "";
        String dateFilterArea = "";
        String dateFilterComercializador = "";

        if (filtro != null && !Boolean.TRUE.equals(filtro.getSoloEnBodega())) {
            if (filtro.getStartDate() != null && filtro.getEndDate() != null) {
                dateFilterRecolector = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
                dateFilterArmador = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
                dateFilterArea = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
                dateFilterComercializador = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
            } else if (filtro.getStartDate() != null) {
                dateFilterRecolector = " AND d.fecha_declaracion >= :startDate";
                dateFilterArmador = " AND d.fecha_declaracion >= :startDate";
                dateFilterArea = " AND d.fecha_declaracion >= :startDate";
                dateFilterComercializador = " AND d.fecha_declaracion >= :startDate";
            } else if (filtro.getEndDate() != null) {
                dateFilterRecolector = " AND d.fecha_declaracion <= :endDate";
                dateFilterArmador = " AND d.fecha_declaracion <= :endDate";
                dateFilterArea = " AND d.fecha_declaracion <= :endDate";
                dateFilterComercializador = " AND d.fecha_declaracion <= :endDate";
            }
        }

        return "SELECT d.id, 'RECOLECTOR' as tipo_declaracion, " +
                "COALESCE(d.folio_origen, d.folio_desembarque_ro, CONCAT('RO-', d.id)) as folio, " +
                "d.fecha_declaracion, d.hora, d.desembarque as kg, d.especie_id, e.nombre as especie_nombre, " +
                "COALESCE(h.nombre, 'HÚMEDO') as humedad_nombre, d.usuario_destinatario_id as comercializador_id, " +
                "TRIM(CONCAT(COALESCE(ud.nombres, ''), ' ', COALESCE(ud.apellidop, ''))) as comercializador_nombre, ud.rut as comercializador_rut, " +
                "d.usuario_id as origen_id, TRIM(CONCAT(COALESCE(uo.nombres, ''), ' ', COALESCE(uo.apellidop, ''))) as origen_nombre, uo.rut as origen_rut, " +
                "NULL as embarcacion, " +
                "(SELECT dm.id FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'RECOLECTOR' AND dm.marca = 'RETENCION_EXCEDIDA' AND dm.resuelta = false LIMIT 1) as retencion_marca_id, " +
                "(SELECT dm.resolucion_tipo FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'RECOLECTOR' AND dm.marca = 'RETENCION_EXCEDIDA' ORDER BY dm.id DESC LIMIT 1) as retencion_resolucion, " +
                "(SELECT GROUP_CONCAT(DISTINCT dm.marca) FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'RECOLECTOR' AND dm.resuelta = false) as marcas_activas " +
                "FROM declaracion_recolector d " +
                "INNER JOIN usuario uo ON d.usuario_id = uo.id " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil pud ON ud.perfil_id = pud.id AND pud.nombre = 'COMERCIALIZADOR' " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA')) " + dateFilterRecolector + " " +
                "UNION ALL " +
                "SELECT d.id, 'ARMADOR', " +
                "COALESCE(d.folio_origen, d.folio_desembarque_da, CONCAT('DA-', d.id)), " +
                "d.fecha_declaracion, d.hora, d.desembarque, d.especie_id, e.nombre, " +
                "COALESCE(h.nombre, 'HÚMEDO'), d.usuario_destinatario_id, " +
                "TRIM(CONCAT(COALESCE(ud.nombres, ''), ' ', COALESCE(ud.apellidop, ''))), ud.rut, " +
                "d.usuario_id, TRIM(CONCAT(COALESCE(uo.nombres, ''), ' ', COALESCE(uo.apellidop, ''))), uo.rut, " +
                "emb.nombre as embarcacion, " +
                "(SELECT dm.id FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'ARMADOR' AND dm.marca = 'RETENCION_EXCEDIDA' AND dm.resuelta = false LIMIT 1), " +
                "(SELECT dm.resolucion_tipo FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'ARMADOR' AND dm.marca = 'RETENCION_EXCEDIDA' ORDER BY dm.id DESC LIMIT 1), " +
                "(SELECT GROUP_CONCAT(DISTINCT dm.marca) FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'ARMADOR' AND dm.resuelta = false) " +
                "FROM declaracion_armador d " +
                "INNER JOIN usuario uo ON d.usuario_id = uo.id " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil pud ON ud.perfil_id = pud.id AND pud.nombre = 'COMERCIALIZADOR' " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "LEFT JOIN embarcacion emb ON d.embarcacion_id = emb.id " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA')) " + dateFilterArmador + " " +
                "UNION ALL " +
                "SELECT d.id, 'AREA', " +
                "COALESCE(d.folio_origen, d.folio_desembarque_amerb, CONCAT('DAM-', d.id)), " +
                "d.fecha_declaracion, d.hora, d.desembarque, d.especie_id, e.nombre, " +
                "COALESCE(h.nombre, 'HÚMEDO'), d.usuario_destinatario_id, " +
                "TRIM(CONCAT(COALESCE(ud.nombres, ''), ' ', COALESCE(ud.apellidop, ''))), ud.rut, " +
                "d.usuario_id, TRIM(CONCAT(COALESCE(uo.nombres, ''), ' ', COALESCE(uo.apellidop, ''))), uo.rut, " +
                "NULL, " +
                "(SELECT dm.id FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'AREA' AND dm.marca = 'RETENCION_EXCEDIDA' AND dm.resuelta = false LIMIT 1), " +
                "(SELECT dm.resolucion_tipo FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'AREA' AND dm.marca = 'RETENCION_EXCEDIDA' ORDER BY dm.id DESC LIMIT 1), " +
                "(SELECT GROUP_CONCAT(DISTINCT dm.marca) FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'AREA' AND dm.resuelta = false) " +
                "FROM declaracion_area d " +
                "INNER JOIN usuario uo ON d.usuario_id = uo.id " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil pud ON ud.perfil_id = pud.id AND pud.nombre = 'COMERCIALIZADOR' " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA')) " + dateFilterArea + " " +
                "UNION ALL " +
                "SELECT d.id, 'COMERCIALIZADOR', " +
                "COALESCE(d.folio_origen, CONCAT('DC-', d.id)), " +
                "d.fecha_declaracion, d.hora, d.cantidad, d.especie_id, e.nombre, " +
                "COALESCE(h.nombre, 'HÚMEDO'), d.usuario_id, " +
                "TRIM(CONCAT(COALESCE(uo.nombres, ''), ' ', COALESCE(uo.apellidop, ''))), uo.rut, " +
                "d.usuario_id, TRIM(CONCAT(COALESCE(uo.nombres, ''), ' ', COALESCE(uo.apellidop, ''))), uo.rut, " +
                "NULL, " +
                "(SELECT dm.id FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'COMERCIALIZADOR' AND dm.marca = 'RETENCION_EXCEDIDA' AND dm.resuelta = false LIMIT 1), " +
                "(SELECT dm.resolucion_tipo FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'COMERCIALIZADOR' AND dm.marca = 'RETENCION_EXCEDIDA' ORDER BY dm.id DESC LIMIT 1), " +
                "(SELECT GROUP_CONCAT(DISTINCT dm.marca) FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'COMERCIALIZADOR' AND dm.resuelta = false) " +
                "FROM declaracion_comercializador d " +
                "INNER JOIN usuario uo ON d.usuario_id = uo.id " +
                "INNER JOIN perfil puo ON uo.perfil_id = puo.id AND puo.nombre = 'COMERCIALIZADOR' " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "WHERE d.declaracion_destinatario_id IS NULL AND d.consumida_por_tipo IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA')) " + dateFilterComercializador + " " +
                "ORDER BY fecha_declaracion DESC";
    }

    private void bindSqlLotesBodegaParams(Query query, FiltroRetencionBodega filtro) {
        if (filtro != null && !Boolean.TRUE.equals(filtro.getSoloEnBodega())) {
            if (filtro.getStartDate() != null) {
                query.setParameter("startDate", filtro.getStartDate());
            }
            if (filtro.getEndDate() != null) {
                query.setParameter("endDate", filtro.getEndDate());
            }
        }
    }

    private Object col(Object[] r, int idx) {
        return (r != null && idx >= 0 && idx < r.length) ? r[idx] : null;
    }

    private Date toDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp) return new Date(((java.sql.Timestamp) o).getTime());
        if (o instanceof java.sql.Date) return new Date(((java.sql.Date) o).getTime());
        if (o instanceof Date) return (Date) o;
        return null;
    }
}
