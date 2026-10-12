package com.trazalga.api.services.indicadores;

import com.trazalga.api.dto.*;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.FactorConversionService;
import com.trazalga.api.services.parametros.ParametrosService;
import com.trazalga.api.services.parametros.ParametrosVariacion;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * TV.1 · Servicio central de Variación de Peso Comercializador → Planta.
 * Single Source of Truth para el contraste entre despachos consumidos y peso neto en romana.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariacionPesoService {

    @PersistenceContext
    private final EntityManager entityManager;

    private final IDeclaracionPlantaAbastecimientoRepository plantaRepository;
    private final IDeclaracionComercializadorRepository comercializadorRepository;
    private final IDeclaracionRecolectorRepository recolectorRepository;
    private final IDeclaracionArmadorRepository armadorRepository;
    private final IDeclaracionAreaRepository areaRepository;

    private final FactorConversionService factorConversionService;
    private final ParametrosService parametrosService;
    private final EvaluadorCoherenciaBiologica evaluadorCoherenciaBiologica;

    /**
     * Evalúa una recepción individual en planta con su romana.
     */
    @Transactional(readOnly = true)
    public ResultadoVariacionPesoDTO evaluarRecepcion(DeclaracionPlantaAbastecimientoModel planta) {
        if (planta == null) return null;
        ParametrosVariacion params = parametrosService != null ? parametrosService.variacion() : null;
        List<DespachoVariacionDTO> despachos = obtenerDespachosParaPlanta(planta);
        Map<String, BigDecimal> factorCache = new HashMap<>();
        return calcularResultado(planta, despachos, params, factorCache);
    }

    /**
     * Evalúa una recepción por su ID de planta.
     */
    @Transactional(readOnly = true)
    public ResultadoVariacionPesoDTO evaluarRecepcionPorId(Long plantaId) {
        if (plantaId == null) return null;
        return plantaRepository.findById(plantaId)
                .map(this::evaluarRecepcion)
                .orElse(null);
    }

    /**
     * Evalúa un conjunto paginado de recepciones aplicando filtros (TV.3).
     * Resuelve todo el período con 3 consultas sin N+1.
     */
    @Transactional(readOnly = true)
    public PageDTO<ResultadoVariacionPesoDTO> evaluarRecepciones(FiltroRecepcionesVariacion filtro, Pageable pageable) {
        // Consulta 1: Plantas en el período
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM DeclaracionPlantaAbastecimientoModel p " +
                "WHERE p.pesoRomanaKg IS NOT NULL AND p.pesoRomanaKg > 0 "
        );
        StringBuilder countJpql = new StringBuilder(
                "SELECT COUNT(p) FROM DeclaracionPlantaAbastecimientoModel p " +
                "WHERE p.pesoRomanaKg IS NOT NULL AND p.pesoRomanaKg > 0 "
        );
        Map<String, Object> params = new HashMap<>();

        if (filtro != null) {
            if (filtro.getStartDate() != null) {
                jpql.append("AND p.fechaIngresoPlanta >= :startDate ");
                countJpql.append("AND p.fechaIngresoPlanta >= :startDate ");
                params.put("startDate", filtro.getStartDate());
            }
            if (filtro.getEndDate() != null) {
                jpql.append("AND p.fechaIngresoPlanta <= :endDate ");
                countJpql.append("AND p.fechaIngresoPlanta <= :endDate ");
                params.put("endDate", filtro.getEndDate());
            }
            if (filtro.getEspecieId() != null) {
                jpql.append("AND p.especie.id = :especieId ");
                countJpql.append("AND p.especie.id = :especieId ");
                params.put("especieId", filtro.getEspecieId());
            }
            if (filtro.getPlantaId() != null) {
                jpql.append("AND p.id = :plantaId ");
                countJpql.append("AND p.id = :plantaId ");
                params.put("plantaId", filtro.getPlantaId());
            }
        }

        jpql.append("ORDER BY p.fechaIngresoPlanta DESC, p.id DESC");

        TypedQuery<DeclaracionPlantaAbastecimientoModel> query = entityManager.createQuery(jpql.toString(), DeclaracionPlantaAbastecimientoModel.class);
        params.forEach(query::setParameter);

        if (pageable != null) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }

        List<DeclaracionPlantaAbastecimientoModel> plantas = query.getResultList();
        if (plantas.isEmpty()) {
            return PageDTO.vacia(pageable != null ? pageable.getPageNumber() : 0, pageable != null ? pageable.getPageSize() : 20);
        }

        List<Long> plantaIds = plantas.stream().map(DeclaracionPlantaAbastecimientoModel::getId).toList();

        // Consulta 2: Despachos asociados en lote (IN plantaIds)
        Map<Long, List<DespachoVariacionDTO>> despachosPorPlanta = obtenerDespachosEnLote(plantaIds, plantas);

        // Consulta 3: Factores en caché
        ParametrosVariacion paramVar = parametrosService != null ? parametrosService.variacion() : null;
        Map<String, BigDecimal> factorCache = new ConcurrentHashMap<>();

        List<ResultadoVariacionPesoDTO> resultados = new ArrayList<>();
        for (DeclaracionPlantaAbastecimientoModel p : plantas) {
            List<DespachoVariacionDTO> despachos = despachosPorPlanta.getOrDefault(p.getId(), Collections.emptyList());
            ResultadoVariacionPesoDTO dto = calcularResultado(p, despachos, paramVar, factorCache);

            // Filtrado post-cálculo si se requiere por semáforo o tramo
            if (filtro != null) {
                if (filtro.getSemaforo() != null && !filtro.getSemaforo().equalsIgnoreCase("TODOS")) {
                    if (!filtro.getSemaforo().equalsIgnoreCase(dto.getSemaforo())) {
                        continue;
                    }
                }
                if (filtro.getTramo() != null && !filtro.getTramo().equalsIgnoreCase("TODOS")) {
                    if (!filtro.getTramo().equalsIgnoreCase(dto.getTramo())) {
                        continue;
                    }
                }
            }

            resultados.add(dto);
        }

        // Conteo total para paginación
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        params.forEach(countQuery::setParameter);
        long totalElements = countQuery.getSingleResult();

        int pageNumber = pageable != null ? pageable.getPageNumber() : 0;
        int pageSize = pageable != null ? pageable.getPageSize() : (resultados.isEmpty() ? 20 : resultados.size());

        return PageDTO.of(resultados, pageNumber, pageSize, totalElements);
    }

    /**
     * Resumen comercializadorPlanta para el endpoint general GET /api/reportes/variacion-peso.
     */
    @Transactional(readOnly = true)
    public ResumenComercializadorPlantaDTO obtenerResumenComercializadorPlanta(Date startDate, Date endDate) {
        return obtenerResumenComercializadorPlanta(startDate, endDate, null, null);
    }

    @Transactional(readOnly = true)
    public ResumenComercializadorPlantaDTO obtenerResumenComercializadorPlanta(
            Date startDate, Date endDate, Long especieId, Long plantaId) {

        FiltroRecepcionesVariacion filtro = FiltroRecepcionesVariacion.builder()
                .startDate(startDate)
                .endDate(endDate)
                .especieId(especieId)
                .plantaId(plantaId)
                .build();

        // Obtenemos los primeros 100 resultados para cálculo de métricas
        PageDTO<ResultadoVariacionPesoDTO> page = evaluarRecepciones(filtro, org.springframework.data.domain.PageRequest.of(0, 100));
        List<ResultadoVariacionPesoDTO> items = page.getContent();

        long verde = 0;
        long amarillo = 0;
        long rojo = 0;
        long sinHumedad = 0;
        double sumDelta = 0.0;
        int countDelta = 0;

        for (ResultadoVariacionPesoDTO r : items) {
            if ("VERDE".equalsIgnoreCase(r.getSemaforo())) verde++;
            else if ("AMARILLO".equalsIgnoreCase(r.getSemaforo())) amarillo++;
            else if ("ROJO".equalsIgnoreCase(r.getSemaforo())) rojo++;

            if (!r.isHumedadVerificada()) sinHumedad++;

            sumDelta += r.getDeltaPct();
            countDelta++;
        }

        Double promDelta = countDelta > 0
                ? Math.round((sumDelta / countDelta) * 10.0) / 10.0
                : null;

        java.util.Map<String, Long> totalesPorSemaforo = new java.util.LinkedHashMap<>();
        totalesPorSemaforo.put("VERDE", verde);
        totalesPorSemaforo.put("AMARILLO", amarillo);
        totalesPorSemaforo.put("ROJO", rojo);

        List<ResultadoVariacionPesoDTO> topDesviaciones = items.stream()
                .sorted((a, b) -> Double.compare(Math.abs(b.getDeltaPct()), Math.abs(a.getDeltaPct())))
                .limit(5)
                .collect(Collectors.toList());

        return ResumenComercializadorPlantaDTO.builder()
                .totalRecepciones(page.getTotalElements())
                .recepcionesVerde(verde)
                .recepcionesAmarillo(amarillo)
                .recepcionesRojo(rojo)
                .totalesPorSemaforo(totalesPorSemaforo)
                .promedioDeltaPct(promDelta)
                .sinHumedadVerificada(sinHumedad)
                .topDesviaciones(topDesviaciones)
                .items(items)
                .build();
    }

    /**
     * TV.3 · Obtiene los orígenes biológicos que alimentaron los despachos de una planta.
     * Es estrictamente informativo y no se compara con la romana.
     */
    @Transactional(readOnly = true)
    public List<OrigenInformativoDTO> obtenerOrigenesInformativos(Long plantaId) {
        if (plantaId == null) return Collections.emptyList();

        DeclaracionPlantaAbastecimientoModel planta = plantaRepository.findById(plantaId).orElse(null);
        if (planta == null) return Collections.emptyList();

        List<DespachoVariacionDTO> despachos = obtenerDespachosParaPlanta(planta);
        List<OrigenInformativoDTO> origenes = new ArrayList<>();

        for (DespachoVariacionDTO d : despachos) {
            if ("COMERCIALIZADOR".equalsIgnoreCase(d.getTipoDeclaracion())) {
                // Rastrear orígenes consumidos por este comercializador
                Long cId = d.getDeclaracionId();
                origenes.addAll(obtenerOrigenesDeComercializador(cId));
            } else {
                // Venta directa: el despacho es directamente el origen
                origenes.add(OrigenInformativoDTO.builder()
                        .origenId(d.getDeclaracionId())
                        .tipoDeclaracion(d.getTipoDeclaracion())
                        .folio(d.getFolio())
                        .declaradorNombre(d.getDeclaradorNombre())
                        .declaradorRut(d.getDeclaradorRut())
                        .kg(d.getKg())
                        .humedadNombre(d.getHumedadNombre())
                        .fechaDeclaracion(d.getFechaSalida())
                        .leyendaInformativa("informativo: no se compara")
                        .build());
            }
        }

        return origenes;
    }

    // =========================================================================
    // Métodos internos de cálculo y resolución de datos
    // =========================================================================

    private ResultadoVariacionPesoDTO calcularResultado(
            DeclaracionPlantaAbastecimientoModel p,
            List<DespachoVariacionDTO> despachos,
            ParametrosVariacion params,
            Map<String, BigDecimal> factorCache
    ) {
        double kgRomana = p.getPesoRomanaKg() != null ? p.getPesoRomanaKg().doubleValue() : 0.0;
        double kgDespacho = 0.0;
        Map<String, Double> kgPorHumedadDespacho = new HashMap<>();
        Map<String, Long> idPorHumedadDespacho = new HashMap<>();

        LocalDateTime llegadaPlanta = combinarFechaHora(p.getFechaIngresoPlanta(), p.getHora());

        double sumaHorasPonderadas = 0.0;
        Double minHoras = null;
        Double maxHoras = null;

        boolean todosComercializador = !despachos.isEmpty();
        boolean todosDirecto = !despachos.isEmpty();

        for (DespachoVariacionDTO d : despachos) {
            kgDespacho += d.getKg();

            String hum = d.getHumedadNombre() != null ? d.getHumedadNombre() : "HÚMEDO";
            kgPorHumedadDespacho.put(hum, kgPorHumedadDespacho.getOrDefault(hum, 0.0) + d.getKg());
            if (d.getHumedadId() != null) {
                idPorHumedadDespacho.put(hum, d.getHumedadId());
            }

            if (!"COMERCIALIZADOR".equalsIgnoreCase(d.getTipoDeclaracion())) {
                todosComercializador = false;
            } else {
                todosDirecto = false;
            }

            // Cálculo de tiempo de tránsito para este despacho
            LocalDateTime salidaDespacho = d.getFechaSalida() != null
                    ? d.getFechaSalida().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : null;

            if (salidaDespacho != null && llegadaPlanta != null) {
                double horas = Math.max(0.0, ChronoUnit.MINUTES.between(salidaDespacho, llegadaPlanta) / 60.0);
                d.setHorasTransito(Math.round(horas * 10.0) / 10.0);
                sumaHorasPonderadas += horas * d.getKg();

                if (minHoras == null || horas < minHoras) minHoras = horas;
                if (maxHoras == null || horas > maxHoras) maxHoras = horas;
            }
        }

        String tramo;
        if (despachos.isEmpty()) {
            tramo = "SIN_DESPACHOS";
        } else if (todosComercializador) {
            tramo = "COMERCIALIZADOR_PLANTA";
        } else if (todosDirecto) {
            tramo = "DIRECTO";
        } else {
            tramo = "MIXTO";
        }

        // Delta físico
        double deltaKg = kgRomana - kgDespacho;
        double deltaPct = kgDespacho > 0
                ? Math.round(((deltaKg / kgDespacho) * 100.0) * 10.0) / 10.0
                : 0.0;

        // Horas promedio y días transcurridos
        double horasPromedio = kgDespacho > 0 ? (sumaHorasPonderadas / kgDespacho) : 0.0;
        double diasTransito = Math.round((horasPromedio / 24.0) * 10.0) / 10.0;

        // Humedad predominante en despacho
        String humedadDespachoNombre = "HÚMEDO";
        Long humedadDespachoId = null;
        double maxKgHum = -1.0;
        for (Map.Entry<String, Double> e : kgPorHumedadDespacho.entrySet()) {
            if (e.getValue() > maxKgHum) {
                maxKgHum = e.getValue();
                humedadDespachoNombre = e.getKey();
                humedadDespachoId = idPorHumedadDespacho.get(e.getKey());
            }
        }

        // Humedad en recepción
        boolean humedadVerificada = p.getHumedadEstadoRecepcion() != null;
        String humedadRecepcionNombre = humedadVerificada
                ? p.getHumedadEstadoRecepcion().getNombre()
                : (p.getHumedadEstado() != null ? p.getHumedadEstado().getNombre() : humedadDespachoNombre);
        Long humedadRecepcionId = humedadVerificada
                ? p.getHumedadEstadoRecepcion().getId()
                : (p.getHumedadEstado() != null ? p.getHumedadEstado().getId() : null);

        // Delta equivalente con factores de conversión
        Long espId = p.getEspecie() != null ? p.getEspecie().getId() : null;
        Double capturaDespachoEq = null;
        Double capturaRecepcionEq = null;
        Double deltaEqPct = null;

        if (espId != null && kgDespacho > 0) {
            double capDespTotal = 0.0;
            for (DespachoVariacionDTO d : despachos) {
                BigDecimal factorD = obtenerFactorCached(espId, d.getHumedadId(), d.getFechaSalida(), factorCache);
                capDespTotal += d.getKg() * factorD.doubleValue();
            }
            BigDecimal factorRec = obtenerFactorCached(espId, humedadRecepcionId, p.getFechaIngresoPlanta(), factorCache);
            double capRecTotal = kgRomana * factorRec.doubleValue();

            capturaDespachoEq = Math.round(capDespTotal * 10.0) / 10.0;
            capturaRecepcionEq = Math.round(capRecTotal * 10.0) / 10.0;

            if (capturaDespachoEq > 0) {
                deltaEqPct = Math.round(((capturaRecepcionEq - capturaDespachoEq) / capturaDespachoEq * 100.0) * 10.0) / 10.0;
            }
        }

        // Evaluación biológica (TV.2)
        EvaluadorCoherenciaBiologica.ResultadoEvaluacion eval = evaluadorCoherenciaBiologica.evaluar(
                humedadDespachoNombre,
                humedadRecepcionNombre,
                deltaPct,
                deltaEqPct,
                diasTransito,
                params
        );

        String plantaNombre = p.getUsuario() != null
                ? (p.getUsuario().getNombres() + " " + (p.getUsuario().getApellidop() != null ? p.getUsuario().getApellidop() : "")).trim()
                : (p.getNombrePlanta() != null ? p.getNombrePlanta() : "Planta #" + p.getId());

        String plantaRut = p.getUsuario() != null ? p.getUsuario().getRut() : "";

        return ResultadoVariacionPesoDTO.builder()
                .plantaId(p.getId())
                .folioPlanta(p.getFolioDeclaracionAPla() != null ? p.getFolioDeclaracionAPla() : p.getFolioOrigen())
                .voucherRomanaNumero(p.getVoucherRomanaNumero())
                .voucherRomanaAdjunto(p.getVoucherRomanaAdjunto())
                .fechaPesaje(p.getFechaPesaje())
                .fechaIngresoPlanta(p.getFechaIngresoPlanta())
                .hora(p.getHora())
                .plantaNombre(plantaNombre)
                .plantaRut(plantaRut)
                .especieId(espId)
                .especieNombre(p.getEspecie() != null ? p.getEspecie().getNombre() : "")
                .tramo(tramo)
                .kgDespacho(Math.round(kgDespacho * 100.0) / 100.0)
                .kgRomana(Math.round(kgRomana * 100.0) / 100.0)
                .deltaKg(Math.round(deltaKg * 100.0) / 100.0)
                .deltaPct(deltaPct)
                .horasTransitoPromedio(Math.round(horasPromedio * 10.0) / 10.0)
                .horasTransitoMin(minHoras != null ? Math.round(minHoras * 10.0) / 10.0 : null)
                .horasTransitoMax(maxHoras != null ? Math.round(maxHoras * 10.0) / 10.0 : null)
                .diasTransito(diasTransito)
                .humedadDespachoId(humedadDespachoId)
                .humedadDespachoNombre(humedadDespachoNombre)
                .humedadRecepcionId(humedadRecepcionId)
                .humedadRecepcionNombre(humedadRecepcionNombre)
                .humedadVerificada(humedadVerificada)
                .capturaDespachoEq(capturaDespachoEq)
                .capturaRecepcionEq(capturaRecepcionEq)
                .deltaEqPct(deltaEqPct)
                .semaforo(eval.semaforo().name())
                .criterio(eval.criterio())
                .descripcion(eval.descripcion())
                .alerta(eval.alerta())
                .despachos(despachos)
                .build();
    }

    private List<DespachoVariacionDTO> obtenerDespachosParaPlanta(DeclaracionPlantaAbastecimientoModel planta) {
        Map<Long, List<DespachoVariacionDTO>> batch = obtenerDespachosEnLote(List.of(planta.getId()), List.of(planta));
        return batch.getOrDefault(planta.getId(), Collections.emptyList());
    }

    /**
     * Consulta 2 (sin N+1): Resuelve en batch los despachos asociados a un conjunto de plantas.
     */
    private Map<Long, List<DespachoVariacionDTO>> obtenerDespachosEnLote(
            List<Long> plantaIds, List<DeclaracionPlantaAbastecimientoModel> plantas) {

        Map<Long, List<DespachoVariacionDTO>> map = new HashMap<>();
        if (plantaIds == null || plantaIds.isEmpty()) return map;

        // 1. Comercializadores asociados directamente por puntero
        List<DeclaracionComercializadorModel> comList = comercializadorRepository.findAll().stream()
                .filter(c -> "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(c.getConsumidaPorTipo())
                        && c.getDeclaracionDestinatario() != null
                        && plantaIds.contains(c.getDeclaracionDestinatario()))
                .toList();

        for (DeclaracionComercializadorModel c : comList) {
            map.computeIfAbsent(c.getDeclaracionDestinatario(), k -> new ArrayList<>())
                    .add(mapComercializador(c));
        }

        // 2. Orígenes asociados directamente por puntero (Venta directa C8)
        List<DeclaracionRecolectorModel> recList = recolectorRepository.findAll().stream()
                .filter(r -> "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(r.getConsumidaPorTipo())
                        && r.getDeclaracionDestinatario() != null
                        && plantaIds.contains(r.getDeclaracionDestinatario()))
                .toList();
        for (DeclaracionRecolectorModel r : recList) {
            map.computeIfAbsent(r.getDeclaracionDestinatario(), k -> new ArrayList<>())
                    .add(mapRecolector(r));
        }

        List<DeclaracionArmadorModel> armList = armadorRepository.findAll().stream()
                .filter(a -> "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(a.getConsumidaPorTipo())
                        && a.getDeclaracionDestinatario() != null
                        && plantaIds.contains(a.getDeclaracionDestinatario()))
                .toList();
        for (DeclaracionArmadorModel a : armList) {
            map.computeIfAbsent(a.getDeclaracionDestinatario(), k -> new ArrayList<>())
                    .add(mapArmador(a));
        }

        List<DeclaracionAreaModel> arList = areaRepository.findAll().stream()
                .filter(ar -> "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(ar.getConsumidaPorTipo())
                        && ar.getDeclaracionDestinatario() != null
                        && plantaIds.contains(ar.getDeclaracionDestinatario()))
                .toList();
        for (DeclaracionAreaModel ar : arList) {
            map.computeIfAbsent(ar.getDeclaracionDestinatario(), k -> new ArrayList<>())
                    .add(mapArea(ar));
        }

        // 3. Fallback para plantas sin despachos encontrados por puntero: buscar por tokens de declaraciones_seleccionadas
        for (DeclaracionPlantaAbastecimientoModel p : plantas) {
            if (!map.containsKey(p.getId()) && p.getDeclaracionesSeleccionadas() != null && !p.getDeclaracionesSeleccionadas().isBlank()) {
                Map<String, List<Long>> parsed = SeleccionTokens.parse(p.getDeclaracionesSeleccionadas());
                List<DespachoVariacionDTO> listaTokens = new ArrayList<>();

                List<Long> cIds = SeleccionTokens.idsParaTipo(parsed, "COMERCIALIZADOR");
                if (!cIds.isEmpty()) {
                    comercializadorRepository.findAllById(cIds).forEach(c -> listaTokens.add(mapComercializador(c)));
                }

                List<Long> recIds = SeleccionTokens.idsParaTipo(parsed, "RECOLECTOR");
                if (!recIds.isEmpty()) {
                    recolectorRepository.findAllById(recIds).forEach(r -> listaTokens.add(mapRecolector(r)));
                }

                List<Long> armIds = SeleccionTokens.idsParaTipo(parsed, "ARMADOR");
                if (!armIds.isEmpty()) {
                    armadorRepository.findAllById(armIds).forEach(a -> listaTokens.add(mapArmador(a)));
                }

                List<Long> areaIds = SeleccionTokens.idsParaTipo(parsed, "AREA");
                if (!areaIds.isEmpty()) {
                    areaRepository.findAllById(areaIds).forEach(ar -> listaTokens.add(mapArea(ar)));
                }

                if (!listaTokens.isEmpty()) {
                    map.put(p.getId(), listaTokens);
                }
            }
        }

        return map;
    }

    private DespachoVariacionDTO mapComercializador(DeclaracionComercializadorModel c) {
        String declNombre = c.getUsuario() != null
                ? (c.getUsuario().getNombres() + " " + (c.getUsuario().getApellidop() != null ? c.getUsuario().getApellidop() : "")).trim()
                : "";
        Date salidaBase = c.getFechaTraslado() != null ? c.getFechaTraslado() : c.getFechaDeclaracion();
        Date salida = combinarFechaYHoraADate(salidaBase, c.getHora());
        String docNum = c.getDocumentoTributarioDestinoNumero() != null
                ? c.getDocumentoTributarioDestinoNumero() : c.getDocumentoTributarioOrigenNumero();
        String docTipo = c.getDocumentoTributarioDestinoTipo() != null
                ? c.getDocumentoTributarioDestinoTipo() : c.getDocumentoTributarioOrigenTipo();

        return DespachoVariacionDTO.builder()
                .declaracionId(c.getId())
                .tipoDeclaracion("COMERCIALIZADOR")
                .folio(c.getFolioDesembarqueAc() != null ? c.getFolioDesembarqueAc() : c.getFolioOrigen())
                .guiaDespacho(docNum)
                .documentoTributarioTipo(docTipo)
                .documentoTributarioNumero(docNum)
                .patente(c.getPlacaPatente() != null && !c.getPlacaPatente().isBlank() ? c.getPlacaPatente() : c.getPatente())
                .declaradorNombre(declNombre)
                .declaradorRut(c.getUsuario() != null ? c.getUsuario().getRut() : "")
                .kg(c.getCantidad() != null ? c.getCantidad().doubleValue() : 0.0)
                .humedadId(c.getHumedadEstado() != null ? c.getHumedadEstado().getId() : null)
                .humedadNombre(c.getHumedadEstado() != null ? c.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaSalida(salida)
                .build();
    }

    private DespachoVariacionDTO mapRecolector(DeclaracionRecolectorModel r) {
        String declNombre = r.getUsuario() != null
                ? (r.getUsuario().getNombres() + " " + (r.getUsuario().getApellidop() != null ? r.getUsuario().getApellidop() : "")).trim()
                : "";
        Date salida = combinarFechaYHoraADate(r.getFechaDeclaracion(), r.getHora());
        return DespachoVariacionDTO.builder()
                .declaracionId(r.getId())
                .tipoDeclaracion("RECOLECTOR")
                .folio(r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen())
                .declaradorNombre(declNombre)
                .declaradorRut(r.getUsuario() != null ? r.getUsuario().getRut() : "")
                .kg(r.getDesembarque() != null ? r.getDesembarque().doubleValue() : 0.0)
                .humedadId(r.getHumedadEstado() != null ? r.getHumedadEstado().getId() : null)
                .humedadNombre(r.getHumedadEstado() != null ? r.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaSalida(salida)
                .build();
    }

    private DespachoVariacionDTO mapArmador(DeclaracionArmadorModel a) {
        String declNombre = a.getUsuario() != null
                ? (a.getUsuario().getNombres() + " " + (a.getUsuario().getApellidop() != null ? a.getUsuario().getApellidop() : "")).trim()
                : "";
        Date salida = combinarFechaYHoraADate(a.getFechaDeclaracion(), a.getHora());
        return DespachoVariacionDTO.builder()
                .declaracionId(a.getId())
                .tipoDeclaracion("ARMADOR")
                .folio(a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen())
                .declaradorNombre(declNombre)
                .declaradorRut(a.getUsuario() != null ? a.getUsuario().getRut() : "")
                .kg(a.getDesembarque() != null ? a.getDesembarque().doubleValue() : 0.0)
                .humedadId(a.getHumedadEstado() != null ? a.getHumedadEstado().getId() : null)
                .humedadNombre(a.getHumedadEstado() != null ? a.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaSalida(salida)
                .build();
    }

    private DespachoVariacionDTO mapArea(DeclaracionAreaModel ar) {
        String declNombre = ar.getUsuario() != null
                ? (ar.getUsuario().getNombres() + " " + (ar.getUsuario().getApellidop() != null ? ar.getUsuario().getApellidop() : "")).trim()
                : "";
        Date salida = combinarFechaYHoraADate(ar.getFechaDeclaracion(), ar.getHora());
        return DespachoVariacionDTO.builder()
                .declaracionId(ar.getId())
                .tipoDeclaracion("AREA")
                .folio(ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb() : ar.getFolioOrigen())
                .declaradorNombre(declNombre)
                .declaradorRut(ar.getUsuario() != null ? ar.getUsuario().getRut() : "")
                .kg(ar.getDesembarque() != null ? ar.getDesembarque().doubleValue() : 0.0)
                .humedadId(ar.getHumedadEstado() != null ? ar.getHumedadEstado().getId() : null)
                .humedadNombre(ar.getHumedadEstado() != null ? ar.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaSalida(salida)
                .build();
    }

    private List<OrigenInformativoDTO> obtenerOrigenesDeComercializador(Long comercializadorId) {
        List<OrigenInformativoDTO> origenes = new ArrayList<>();
        if (comercializadorId == null) return origenes;

        DeclaracionComercializadorModel c = comercializadorRepository.findById(comercializadorId).orElse(null);
        if (c == null) return origenes;

        // Por puntero consumidaPorTipo = 'COMERCIALIZADOR' y declaracionDestinatario = c.id
        recolectorRepository.findAll().stream()
                .filter(r -> "COMERCIALIZADOR".equalsIgnoreCase(r.getConsumidaPorTipo())
                        && Objects.equals(r.getDeclaracionDestinatario(), c.getId()))
                .forEach(r -> origenes.add(mapOrigenRecolector(r)));

        armadorRepository.findAll().stream()
                .filter(a -> "COMERCIALIZADOR".equalsIgnoreCase(a.getConsumidaPorTipo())
                        && Objects.equals(a.getDeclaracionDestinatario(), c.getId()))
                .forEach(a -> origenes.add(mapOrigenArmador(a)));

        areaRepository.findAll().stream()
                .filter(ar -> "COMERCIALIZADOR".equalsIgnoreCase(ar.getConsumidaPorTipo())
                        && Objects.equals(ar.getDeclaracionDestinatario(), c.getId()))
                .forEach(ar -> origenes.add(mapOrigenArea(ar)));

        // Fallback tokens de comercializador
        if (origenes.isEmpty() && c.getDeclaracionesSeleccionadas() != null && !c.getDeclaracionesSeleccionadas().isBlank()) {
            Map<String, List<Long>> parsed = SeleccionTokens.parse(c.getDeclaracionesSeleccionadas());
            List<Long> rIds = SeleccionTokens.idsParaTipo(parsed, "RECOLECTOR");
            if (!rIds.isEmpty()) recolectorRepository.findAllById(rIds).forEach(r -> origenes.add(mapOrigenRecolector(r)));

            List<Long> aIds = SeleccionTokens.idsParaTipo(parsed, "ARMADOR");
            if (!aIds.isEmpty()) armadorRepository.findAllById(aIds).forEach(a -> origenes.add(mapOrigenArmador(a)));

            List<Long> arIds = SeleccionTokens.idsParaTipo(parsed, "AREA");
            if (!arIds.isEmpty()) areaRepository.findAllById(arIds).forEach(ar -> origenes.add(mapOrigenArea(ar)));
        }

        return origenes;
    }

    private OrigenInformativoDTO mapOrigenRecolector(DeclaracionRecolectorModel r) {
        String nombre = r.getUsuario() != null
                ? (r.getUsuario().getNombres() + " " + (r.getUsuario().getApellidop() != null ? r.getUsuario().getApellidop() : "")).trim()
                : "";
        return OrigenInformativoDTO.builder()
                .origenId(r.getId())
                .tipoDeclaracion("RECOLECTOR")
                .folio(r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen())
                .declaradorNombre(nombre)
                .declaradorRut(r.getUsuario() != null ? r.getUsuario().getRut() : "")
                .kg(r.getDesembarque() != null ? r.getDesembarque().doubleValue() : 0.0)
                .humedadNombre(r.getHumedadEstado() != null ? r.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaDeclaracion(r.getFechaDeclaracion())
                .caletaNombre(r.getCaleta() != null ? r.getCaleta().getNombre() : null)
                .comunaNombre(r.getCaleta() != null && r.getCaleta().getComuna() != null ? r.getCaleta().getComuna().getNombre() : null)
                .leyendaInformativa("informativo: no se compara")
                .build();
    }

    private OrigenInformativoDTO mapOrigenArmador(DeclaracionArmadorModel a) {
        String nombre = a.getUsuario() != null
                ? (a.getUsuario().getNombres() + " " + (a.getUsuario().getApellidop() != null ? a.getUsuario().getApellidop() : "")).trim()
                : "";
        return OrigenInformativoDTO.builder()
                .origenId(a.getId())
                .tipoDeclaracion("ARMADOR")
                .folio(a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen())
                .declaradorNombre(nombre)
                .declaradorRut(a.getUsuario() != null ? a.getUsuario().getRut() : "")
                .kg(a.getDesembarque() != null ? a.getDesembarque().doubleValue() : 0.0)
                .humedadNombre(a.getHumedadEstado() != null ? a.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaDeclaracion(a.getFechaDeclaracion())
                .caletaNombre(a.getCaleta() != null ? a.getCaleta().getNombre() : null)
                .comunaNombre(a.getCaleta() != null && a.getCaleta().getComuna() != null ? a.getCaleta().getComuna().getNombre() : null)
                .leyendaInformativa("informativo: no se compara")
                .build();
    }

    private OrigenInformativoDTO mapOrigenArea(DeclaracionAreaModel ar) {
        String nombre = ar.getUsuario() != null
                ? (ar.getUsuario().getNombres() + " " + (ar.getUsuario().getApellidop() != null ? ar.getUsuario().getApellidop() : "")).trim()
                : "";
        return OrigenInformativoDTO.builder()
                .origenId(ar.getId())
                .tipoDeclaracion("AREA")
                .folio(ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb() : ar.getFolioOrigen())
                .declaradorNombre(nombre)
                .declaradorRut(ar.getUsuario() != null ? ar.getUsuario().getRut() : "")
                .kg(ar.getDesembarque() != null ? ar.getDesembarque().doubleValue() : 0.0)
                .humedadNombre(ar.getHumedadEstado() != null ? ar.getHumedadEstado().getNombre() : "HÚMEDO")
                .fechaDeclaracion(ar.getFechaDeclaracion())
                .leyendaInformativa("informativo: no se compara")
                .build();
    }

    private BigDecimal obtenerFactorCached(Long especieId, Long humedadId, Date fecha, Map<String, BigDecimal> cache) {
        if (especieId == null || humedadId == null || factorConversionService == null) {
            return BigDecimal.ONE;
        }
        String key = especieId + ":" + humedadId + ":" + (fecha != null ? fecha.toString().substring(0, 7) : "ALL");
        return cache.computeIfAbsent(key, k -> {
            try {
                return factorConversionService.findFactorVigente(especieId, humedadId, fecha)
                        .map(FactorConversionModel::getFactor)
                        .orElse(BigDecimal.ONE);
            } catch (Exception e) {
                return BigDecimal.ONE;
            }
        });
    }

    private LocalDateTime combinarFechaHora(Date fecha, String horaStr) {
        if (fecha == null) return null;
        LocalDate ld;
        if (fecha instanceof java.sql.Date sqlDate) {
            ld = sqlDate.toLocalDate();
        } else {
            ld = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        LocalTime lt = LocalTime.MIDNIGHT;
        if (horaStr != null && !horaStr.isBlank()) {
            try {
                lt = LocalTime.parse(horaStr.trim().length() == 5 ? horaStr.trim() + ":00" : horaStr.trim());
            } catch (Exception ignored) {
                lt = LocalTime.MIDNIGHT;
            }
        }
        return LocalDateTime.of(ld, lt);
    }

    private Date combinarFechaYHoraADate(Date fecha, String horaStr) {
        if (fecha == null) return null;
        LocalDateTime ldt = combinarFechaHora(fecha, horaStr);
        if (ldt == null) return fecha;
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
