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
    private com.trazalga.api.repositories.IAvisoEnviadoRepository avisoRepository;

    @Autowired
    private MarcaRetencionExecutorService marcaRetencionExecutorService;

    @Autowired
    @Lazy
    private BloqueoCargaService bloqueoCargaService;

    @Autowired
    @Lazy
    private ReportRepository reportRepository;

    public ParametrosRetencion getParametros() {
        return parametrosService != null ? parametrosService.retencion() : null;
    }

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

        // TR.2: Obtener lotes en bodega canónica (TR.1)
        FiltroRetencionBodega filtroBodega = FiltroRetencionBodega.builder()
                .startDate(startDate)
                .endDate(endDate)
                .soloEnBodega(true)
                .build();
        List<LoteBodegaDTO> lotesBodega = lotesEnBodega(filtroBodega);

        long cargasRetenidas = lotesBodega.stream()
                .filter(l -> Boolean.TRUE.equals(l.getBloqueada()))
                .count();

        // Estructura de la Matriz 4x3 canónica (Filas = Estados de Humedad, Columnas = Semáforo)
        Map<String, Map<String, Object>> matriz = new LinkedHashMap<>();
        String[] estados = {"HUMEDO", "SEMI_HUMEDO", "SEMI_SECO", "SECO"};
        for (String est : estados) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("VERDE", 0L);
            fila.put("AMARILLO", 0L);
            fila.put("ROJO", 0L);
            fila.put("totalLotes", 0L);
            fila.put("totalKg", 0.0);
            matriz.put(est, fila);
        }

        long verdeTotal = 0;
        long amarilloTotal = 0;
        long rojoTotal = 0;
        double totalKgEnBodega = 0.0;
        List<Map<String, Object>> lotesEvaluados = new ArrayList<>();
        List<Map<String, Object>> lotesRojos = new ArrayList<>();

        for (LoteBodegaDTO lote : lotesBodega) {
            String sem = lote.getSemaforo() != null ? lote.getSemaforo().toUpperCase() : "VERDE";
            switch (sem) {
                case "VERDE" -> verdeTotal++;
                case "AMARILLO" -> amarilloTotal++;
                case "ROJO" -> rojoTotal++;
                default -> verdeTotal++;
            }

            double kg = lote.getKg() != null ? lote.getKg() : 0.0;
            totalKgEnBodega += kg;

            String claveMatriz = PoliticaRetencion.claveMatrizHumedad(lote.getEstadoDeclarado());
            Map<String, Object> fila = matriz.get(claveMatriz);
            if (fila != null) {
                if (fila.containsKey(sem)) {
                    fila.put(sem, ((Number) fila.get(sem)).longValue() + 1);
                }
                fila.put("totalLotes", ((Number) fila.get("totalLotes")).longValue() + 1);
                fila.put("totalKg", Math.round((((Number) fila.get("totalKg")).doubleValue() + kg) * 100.0) / 100.0);
            }

            Map<String, Object> lMap = new LinkedHashMap<>();
            lMap.put("id", lote.getId());
            lMap.put("tipo", lote.getTipo());
            lMap.put("folio", lote.getFolio());
            lMap.put("especieId", lote.getEspecieId());
            lMap.put("especie", lote.getEspecie());
            lMap.put("estadoDeclarado", lote.getEstadoDeclarado());
            lMap.put("tramoReal", lote.getTramoReal());
            lMap.put("kg", lote.getKg());
            lMap.put("fechaDeclaracion", lote.getFechaDeclaracion());
            lMap.put("horaDeclaracion", lote.getHoraDeclaracion());
            lMap.put("horasTranscurridas", lote.getHorasTranscurridas());
            lMap.put("diasTranscurridos", lote.getDiasTranscurridos());
            lMap.put("plazoMaxHoras", lote.getPlazoMaxHoras());
            lMap.put("semaforo", lote.getSemaforo());
            lMap.put("enBodega", true);
            lMap.put("destinado", false);
            lMap.put("inconsistenciaHumedad", lote.getInconsistenciaHumedad());
            lMap.put("comercializadorId", lote.getComercializadorId());
            lMap.put("actorComercializador", lote.getActorComercializador());
            lMap.put("rutComercializador", lote.getRutComercializador());
            lMap.put("origenId", lote.getOrigenId());
            lMap.put("actorOrigen", lote.getActorOrigen());
            lMap.put("rutOrigen", lote.getRutOrigen());
            lMap.put("embarcacion", lote.getEmbarcacion());
            lMap.put("bloqueada", lote.getBloqueada());
            lMap.put("motivoBloqueo", lote.getMotivoBloqueo());
            lMap.put("marcaId", lote.getMarcaId());
            lMap.put("marcasActivas", lote.getMarcasActivas());

            lotesEvaluados.add(lMap);
            if ("ROJO".equals(sem)) {
                lotesRojos.add(lMap);
            }
        }

        // TR.2: Contar en tránsito a planta (declaraciones con destinatario planta aún no recibidas)
        long totalLotesEnTransitoAPlanta = contarLotesEnTransitoAPlanta(startDate, endDate);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("activo", true);
        metrics.put("controlDesactivado", false);
        metrics.put("totalLotesEnBodega", (long) lotesBodega.size());
        metrics.put("totalKgEnBodega", Math.round(totalKgEnBodega * 100.0) / 100.0);
        metrics.put("totalLotesEnTransitoAPlanta", totalLotesEnTransitoAPlanta);
        metrics.put("cargasRetenidas", cargasRetenidas);
        metrics.put("semaforoVerde", verdeTotal);
        metrics.put("semaforoAmarillo", amarilloTotal);
        metrics.put("semaforoRojo", rojoTotal);
        metrics.put("matriz", matriz);
        metrics.put("lotes", lotesEvaluados);
        metrics.put("lotesRojos", lotesRojos);

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
     * TR.2: Cuenta las declaraciones asignadas a una planta que aún no han sido recibidas en la romana.
     */
    private long contarLotesEnTransitoAPlanta(Date startDate, Date endDate) {
        String dateFilterRecolector = "";
        String dateFilterArmador = "";
        String dateFilterArea = "";
        String dateFilterComercializador = "";

        if (startDate != null && endDate != null) {
            dateFilterRecolector = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
            dateFilterArmador = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
            dateFilterArea = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
            dateFilterComercializador = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilterRecolector = " AND d.fecha_declaracion >= :startDate";
            dateFilterArmador = " AND d.fecha_declaracion >= :startDate";
            dateFilterArea = " AND d.fecha_declaracion >= :startDate";
            dateFilterComercializador = " AND d.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilterRecolector = " AND d.fecha_declaracion <= :endDate";
            dateFilterArmador = " AND d.fecha_declaracion <= :endDate";
            dateFilterArea = " AND d.fecha_declaracion <= :endDate";
            dateFilterComercializador = " AND d.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT (" +
                "SELECT COUNT(*) FROM declaracion_recolector d " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil p ON ud.perfil_id = p.id AND p.nombre = 'PLANTA' " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA'))" + dateFilterRecolector +
                ") + (" +
                "SELECT COUNT(*) FROM declaracion_armador d " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil p ON ud.perfil_id = p.id AND p.nombre = 'PLANTA' " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA'))" + dateFilterArmador +
                ") + (" +
                "SELECT COUNT(*) FROM declaracion_area d " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil p ON ud.perfil_id = p.id AND p.nombre = 'PLANTA' " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA'))" + dateFilterArea +
                ") + (" +
                "SELECT COUNT(*) FROM declaracion_comercializador d " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil p ON ud.perfil_id = p.id AND p.nombre = 'PLANTA' " +
                "WHERE d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA'))" + dateFilterComercializador +
                ")";

        try {
            Query q = entityManager.createNativeQuery(sql);
            if (startDate != null) q.setParameter("startDate", startDate);
            if (endDate != null) q.setParameter("endDate", endDate);
            Object res = q.getSingleResult();
            return res != null ? ((Number) res).longValue() : 0L;
        } catch (Exception ex) {
            log.warn("Error contando cargas en tránsito a planta: {}", ex.getMessage());
            return 0L;
        }
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
        return registrarMarcaRetencionExcedida(tipo, declaracionId, motivo, usuarioDeclaranteId, regionId, null, null, "VALIDACION", null);
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
        return registrarMarcaRetencionExcedida(tipo, declaracionId, motivo, usuarioDeclaranteId, regionId, umbralParam, valorParam, origenParam, null);
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
            String origenParam,
            String parametroNombre
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
                    origenParam,
                    parametroNombre
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

        // TR.3: Determinar umbral. Si no viene en umbralParam, intentar resolverlo desde el motivo
        String umbral = (umbralParam != null && !umbralParam.isBlank()) ? umbralParam.trim() : null;
        if (umbral == null && motivo != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:límite de bloqueo de|bloqueo automático a las|retención máxima de|excedió\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:h|horas)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(motivo);
            if (m.find()) {
                umbral = m.group(1);
            }
        }

        if (umbral == null || umbral.isBlank()) {
            log.warn("Se intentó registrar RETENCION_EXCEDIDA para {}:{} sin umbral de bloqueo válido.", tipoNorm, declaracionId);
            return null;
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
                valorObs = (valorParam != null && !valorParam.isBlank()) ? valorParam : (umbral != null ? umbral : "");
            }
        }
        if (valorObs.length() > 40) {
            valorObs = valorObs.substring(0, 40);
        }

        if (umbral.length() > 40) {
            umbral = umbral.substring(0, 40);
        }

        String origen = (origenParam != null && !origenParam.isBlank()) ? origenParam.trim().toUpperCase() : "VALIDACION";
        if (origen.length() > 20) {
            origen = origen.substring(0, 20);
        }

        String paramFinal = (parametroNombre != null && !parametroNombre.isBlank())
                ? parametroNombre.trim()
                : (motivo != null && (motivo.contains("SEMI HÚMEDO") || motivo.contains("SEMI_HUMEDO"))
                        ? PoliticaRetencion.parametroBloqueoNombre("SEMI_HUMEDO")
                        : (motivo != null && (motivo.contains("SEMI SECO") || motivo.contains("SEMI_SECO"))
                                ? PoliticaRetencion.parametroBloqueoNombre("SEMI_SECO")
                                : PoliticaRetencion.parametroBloqueoNombre("HUMEDO")));

        CriterioHallazgo criterio = new CriterioHallazgo(
                paramFinal,
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
                                String.valueOf(eval.getUmbralBloqueoHoras()),
                                String.valueOf(Math.round(lote.getHorasTranscurridas())),
                                "BARRIDO",
                                eval.getParametroBloqueoNombre()
                        );
                        bloqueosAplicados++;
                        log.warn("Bloqueo automático RETENCION_EXCEDIDA aplicado a lote {}:{} ({} h en bodega)",
                                lote.getTipo(), lote.getId(), lote.getHorasTranscurridas());
                    } catch (Exception ex) {
                        log.error("Error aplicando bloqueo a lote {}:{}: {}", lote.getTipo(), lote.getId(), ex.getMessage());
                    }
                }
            }

            // 2. Preaviso informativo de retención al comercializador (TR.3):
            // SÓLO cuando el bloqueo esté activo y esté en preaviso de bloqueo (ej. 80% de 120h = 96h).
            // Se registra en aviso_enviado con clave RETENCION_PREAVISO:<TIPO>:<ID>, NO como hallazgo.
            if (params.bloqueoActivo() && eval.isEnPreavisoBloqueo()) {
                try {
                    String claveAviso = String.format("RETENCION_PREAVISO:%s:%d", lote.getTipo(), lote.getId());
                    if (avisoRepository != null && !avisoRepository.existsById(claveAviso)) {
                        avisoRepository.save(new com.trazalga.api.models.AvisoEnviadoModel(claveAviso, new Date()));
                        preavisosEmitidos++;
                        log.info("Preaviso de retención registrado en aviso_enviado para {}:{}", lote.getTipo(), lote.getId());
                    }
                } catch (Exception ex) {
                    log.debug("Preaviso no registrado para {}:{}: {}", lote.getTipo(), lote.getId(), ex.getMessage());
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
     * Si ha excedido el plazo de retención configurado para bloqueo y la política de bloqueo está activa,
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

        String sql = "SELECT d.id, d.fecha_declaracion, d.hora, d.especie_id, e.nombre, COALESCE(h.nombre, 'HÚMEDO'), d.usuario_destinatario_id " +
                "FROM " + tabla + " d " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil pud ON ud.perfil_id = pud.id AND pud.nombre = 'COMERCIALIZADOR' " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "WHERE d.id = :id AND d.usuario_destinatario_id IS NOT NULL AND d.declaracion_destinatario_id IS NULL " +
                "AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA'))";

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
            Long usuarioDestinatarioId = r[6] != null ? ((Number) r[6]).longValue() : null;

            LocalDateTime inicioTs = ReportService.parseTimestamp(fecha, hora);
            double horas = CadenaCustodiaService.horasEnBodega(inicioTs, LocalDateTime.now());

            ResultadoEvaluacionRetencion eval = politicaRetencion.evaluar(espId, espNom, hum, horas, params);
            if (eval.isBloqueoRequerido()) {
                DeclaracionMarcaModel marca = marcaRetencionExecutorService.registrarMarcaRetencionExcedida(
                        tipo, id, eval.getMotivoDetalle(), usuarioDestinatarioId, null,
                        String.valueOf(eval.getUmbralBloqueoHoras()),
                        String.valueOf(Math.round(horas)),
                        "VALIDACION",
                        eval.getParametroBloqueoNombre());
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
                "COALESCE(h.nombre, 'HÚMEDO'), d.usuario_destinatario_id, " +
                "TRIM(CONCAT(COALESCE(ud.nombres, ''), ' ', COALESCE(ud.apellidop, ''))), ud.rut, " +
                "d.usuario_id, TRIM(CONCAT(COALESCE(uo.nombres, ''), ' ', COALESCE(uo.apellidop, ''))), uo.rut, " +
                "NULL, " +
                "(SELECT dm.id FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'COMERCIALIZADOR' AND dm.marca = 'RETENCION_EXCEDIDA' AND dm.resuelta = false LIMIT 1), " +
                "(SELECT dm.resolucion_tipo FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'COMERCIALIZADOR' AND dm.marca = 'RETENCION_EXCEDIDA' ORDER BY dm.id DESC LIMIT 1), " +
                "(SELECT GROUP_CONCAT(DISTINCT dm.marca) FROM declaracion_marca dm WHERE dm.declaracion_id = d.id AND dm.declaracion_tipo = 'COMERCIALIZADOR' AND dm.resuelta = false) " +
                "FROM declaracion_comercializador d " +
                "INNER JOIN usuario uo ON d.usuario_id = uo.id " +
                "INNER JOIN usuario ud ON d.usuario_destinatario_id = ud.id " +
                "INNER JOIN perfil pud ON ud.perfil_id = pud.id AND pud.nombre = 'COMERCIALIZADOR' " +
                "INNER JOIN especie e ON d.especie_id = e.id " +
                "LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id " +
                "WHERE d.usuario_destinatario_id IS NOT NULL AND d.declaracion_destinatario_id IS NULL AND (d.estado IS NULL OR d.estado NOT IN ('ANULADA', 'RECHAZADA')) " + dateFilterComercializador + " " +
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
