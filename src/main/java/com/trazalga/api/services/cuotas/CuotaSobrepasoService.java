package com.trazalga.api.services.cuotas;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.ConsumoPersonaDTO;
import com.trazalga.api.dto.CuotaSobrepasoDTO;
import com.trazalga.api.dto.FiltroSobrepasos;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de auditoría y reporte para Sobrepasos de Cuotas (TQ.1).
 * Resuelve consultas paginadas unificando marcas (CUOTA_EXCEDIDA, POSTERIOR_CIERRE, DECLARACION_EXTEMPORANEA),
 * hidratando entidades en lote y calculando porcentajes y excesos exactos.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class CuotaSobrepasoService {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    @Lazy
    private CuotaExtraccionService cuotaService;

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private ConsumoIndividualQuery consumoIndividualQuery;

    private static final List<String> MARCAS_DEFAULT = List.of(
            "CUOTA_EXCEDIDA", "POSTERIOR_CIERRE", "DECLARACION_EXTEMPORANEA"
    );

    public Page<CuotaSobrepasoDTO> buscar(FiltroSobrepasos filtro, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        List<String> marcasFiltro = MARCAS_DEFAULT;
        if (filtro != null && filtro.getMarcas() != null && !filtro.getMarcas().isEmpty()) {
            marcasFiltro = filtro.getMarcas().stream().map(String::toUpperCase).collect(Collectors.toList());
        }
        where.append(" AND m.marca IN :marcas ");
        params.put("marcas", marcasFiltro);

        if (filtro != null) {
            if (filtro.getCuotaId() != null) {
                where.append(" AND m.reglaId = :cuotaId ");
                params.put("cuotaId", filtro.getCuotaId());
            }
            if (filtro.getStartDate() != null) {
                where.append(" AND m.createdAt >= :startDate ");
                params.put("startDate", filtro.getStartDate());
            }
            if (filtro.getEndDate() != null) {
                where.append(" AND m.createdAt <= :endDate ");
                params.put("endDate", filtro.getEndDate());
            }
            if (filtro.getResuelta() != null) {
                where.append(" AND m.resuelta = :resuelta ");
                params.put("resuelta", filtro.getResuelta());
            }
            if (filtro.getEstadoGestion() != null && !filtro.getEstadoGestion().isBlank()) {
                where.append(" AND UPPER(m.estadoGestion) = :estadoGestion ");
                params.put("estadoGestion", filtro.getEstadoGestion().trim().toUpperCase());
            }
        }

        // Consulta de marcas
        TypedQuery<DeclaracionMarcaModel> query = em.createQuery(
                "SELECT m FROM DeclaracionMarcaModel m " + where + " ORDER BY m.createdAt DESC, m.id DESC",
                DeclaracionMarcaModel.class);
        params.forEach(query::setParameter);
        List<DeclaracionMarcaModel> todasMarcas = query.getResultList();

        if (todasMarcas.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable != null ? pageable : Pageable.unpaged(), 0);
        }

        // Hidratación en lote de entidades operativas
        Map<Long, DeclaracionRecolectorModel> recolectores = hidratarRecolectores(todasMarcas);
        Map<Long, DeclaracionArmadorModel> armadores = hidratarArmadores(todasMarcas);
        Map<Long, DeclaracionAreaModel> areas = hidratarAreas(todasMarcas);

        // Hidratación en lote de cuotas asociadas
        Set<Long> cuotaIds = todasMarcas.stream()
                .map(DeclaracionMarcaModel::getReglaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CuotaExtraccionModel> cuotasMap = new HashMap<>();
        if (!cuotaIds.isEmpty()) {
            List<CuotaExtraccionModel> cuotas = em.createQuery(
                    "SELECT c FROM CuotaExtraccionModel c " +
                    "LEFT JOIN FETCH c.especie " +
                    "LEFT JOIN FETCH c.extraccionTipo " +
                    "LEFT JOIN FETCH c.humedadEstado " +
                    "LEFT JOIN FETCH c.region " +
                    "LEFT JOIN FETCH c.provincia " +
                    "WHERE c.id IN :cuotaIds", CuotaExtraccionModel.class)
                    .setParameter("cuotaIds", cuotaIds)
                    .getResultList();
            cuotas.forEach(c -> cuotasMap.put(c.getId(), c));
        }

        List<CuotaSobrepasoDTO> todosDTOs = new ArrayList<>();

        for (DeclaracionMarcaModel m : todasMarcas) {
            String tipoNorm = m.getDeclaracionTipo() != null ? m.getDeclaracionTipo().toUpperCase() : "";
            Long declId = m.getDeclaracionId();
            Long cId = m.getReglaId();
            CuotaExtraccionModel cuota = cId != null ? cuotasMap.get(cId) : null;

            CuotaSobrepasoDTO dto = CuotaSobrepasoDTO.builder()
                    .marcaId(m.getId())
                    .marca(m.getMarca())
                    .detalle(m.getDetalle())
                    .resuelta(m.getResuelta())
                    .estadoGestion(m.getEstadoGestion() != null ? m.getEstadoGestion() : (Boolean.TRUE.equals(m.getResuelta()) ? "RESUELTA" : "PENDIENTE"))
                    .createdAt(m.getCreatedAt())
                    .criterioParametro(m.getCriterioParametro())
                    .criterioUmbral(m.getCriterioUmbral())
                    .criterioValor(m.getCriterioValor())
                    .criterioUnidad(m.getCriterioUnidad())
                    .declaracionTipo(tipoNorm)
                    .declaracionId(declId)
                    .cuotaId(cId)
                    .build();

            // Criterio estructurado textual
            if (m.getCriterioParametro() != null) {
                CriterioHallazgo crit = new CriterioHallazgo(
                        m.getCriterioParametro(),
                        m.getCriterioUmbral(),
                        m.getCriterioValor(),
                        m.getCriterioUnidad()
                );
                dto.setCriterioTexto(crit.texto() != null ? crit.texto() : m.getDetalle());
            } else {
                dto.setCriterioTexto(m.getDetalle());
            }

            // Datos de la cuota
            Double limiteEfectivo = null;
            if (cuota != null) {
                dto.setCuotaAlcance(cuotaService != null ? cuotaService.describirAlcance(cuota) : cuota.getNivelAgregacion());
                dto.setCuotaEspecie(cuota.getEspecie() != null ? cuota.getEspecie().getNombre() : null);
                dto.setCuotaMetodo(cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getNombre() : null);
                dto.setCuotaVigencia(cuota.getPeriodo());
                dto.setCuotaHumedad(cuota.getHumedadEstado() != null ? cuota.getHumedadEstado().getNombre() : null);

                if (cuotaService != null) {
                    BigDecimal le = cuotaService.calcularLimiteEfectivo(cuota, new Date());
                    if (le != null) {
                        limiteEfectivo = le.doubleValue();
                    }
                }
                if (limiteEfectivo == null && cuota.getLimiteKg() != null) {
                    limiteEfectivo = cuota.getLimiteKg();
                }
                dto.setCuotaLimiteEfectivo(limiteEfectivo);
            }

            // Métricas calculadas del sobrepaso
            Double consumo = parseDouble(m.getCriterioValor());
            Double umbral = parseDouble(m.getCriterioUmbral());
            if (umbral == null && limiteEfectivo != null) {
                umbral = limiteEfectivo;
            }

            if (consumo != null) {
                dto.setConsumoAcumulado(consumo);
                if (umbral != null && umbral > 0) {
                    double pct = (consumo / umbral) * 100.0;
                    dto.setPorcentajeConsumo(Math.round(pct * 10.0) / 10.0);
                    dto.setExcesoKg(Math.max(0.0, consumo - umbral));
                }
            }

            // Enriquecer datos de la declaración causante
            Long declComunaId = null;
            if ("RECOLECTOR".equals(tipoNorm) && recolectores.containsKey(declId)) {
                DeclaracionRecolectorModel r = recolectores.get(declId);
                dto.setFolio(r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen());
                dto.setFechaDeclaracion(r.getFechaDeclaracion());
                dto.setFechaExtraccion(r.getFechaExtraccion());
                dto.setHora(r.getHora());
                dto.setKilos(toDouble(r.getDesembarque()));
                dto.setCaptura(toDouble(r.getCaptura()));
                dto.setCaleta(r.getCaleta() != null ? r.getCaleta().getNombre() : null);
                dto.setComuna(r.getComuna() != null ? r.getComuna().getNombre() : null);
                dto.setActorRut(r.getUsuario() != null ? r.getUsuario().getRut() : null);
                dto.setActorNombre(r.getNombre() != null ? r.getNombre() : (r.getUsuario() != null ? r.getUsuario().getNombreCompleto() : null));
                dto.setActorPerfil("RECOLECTOR");
                declComunaId = r.getComuna() != null ? r.getComuna().getId() : null;
            } else if ("ARMADOR".equals(tipoNorm) && armadores.containsKey(declId)) {
                DeclaracionArmadorModel a = armadores.get(declId);
                dto.setFolio(a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen());
                dto.setFechaDeclaracion(a.getFechaDeclaracion());
                dto.setFechaExtraccion(a.getFechaExtraccion());
                dto.setHora(a.getHora());
                dto.setKilos(toDouble(a.getDesembarque()));
                dto.setCaptura(a.getCaptura());
                dto.setCaleta(a.getCaleta() != null ? a.getCaleta().getNombre() : null);
                dto.setComuna(a.getComuna() != null ? a.getComuna().getNombre() : null);
                dto.setActorRut(a.getUsuario() != null ? a.getUsuario().getRut() : null);
                dto.setActorNombre(a.getUsuario() != null ? a.getUsuario().getNombreCompleto() : null);
                dto.setActorPerfil("ARMADOR");
                dto.setEmbarcacionCodigo(a.getEmbarcacion() != null ? a.getEmbarcacion().getCodigo() : a.getCodigoSernapescaEmbarcacion());
                dto.setEmbarcacionNombre(a.getEmbarcacion() != null ? a.getEmbarcacion().getNombre() : null);
                declComunaId = a.getComuna() != null ? a.getComuna().getId() : null;
            } else if ("AREA".equals(tipoNorm) && areas.containsKey(declId)) {
                DeclaracionAreaModel ar = areas.get(declId);
                dto.setFolio(ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb() : ar.getFolioOrigen());
                dto.setFechaDeclaracion(ar.getFechaDeclaracion());
                dto.setFechaExtraccion(ar.getFechaExtraccion());
                dto.setHora(ar.getHora());
                dto.setKilos(ar.getDesembarque());
                dto.setCaptura(ar.getCaptura());
                dto.setCaleta(ar.getCaleta() != null ? ar.getCaleta().getNombre() : null);
                dto.setComuna(ar.getAmerb() != null && ar.getAmerb().getComuna() != null ? ar.getAmerb().getComuna().getNombre() : null);
                dto.setActorRut(ar.getUsuario() != null ? ar.getUsuario().getRut() : null);
                dto.setActorNombre(ar.getUsuario() != null ? ar.getUsuario().getNombreCompleto() : null);
                dto.setActorPerfil("AREA");
                declComunaId = ar.getAmerb() != null && ar.getAmerb().getComuna() != null ? ar.getAmerb().getComuna().getId() : null;
            }

            // Filtro por comuna si aplica
            if (filtro != null && filtro.getComunaId() != null) {
                if (declComunaId == null || !declComunaId.equals(filtro.getComunaId())) {
                    continue;
                }
            }

            todosDTOs.add(dto);
        }

        // Paginación
        int total = todosDTOs.size();
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(todosDTOs, Pageable.unpaged(), total);
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<CuotaSobrepasoDTO> pagedList = (start < total) ? todosDTOs.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(pagedList, pageable, total);
    }

    /**
     * Consulta el desglose individual por persona para una cuota (TM.2 / TQ.1),
     * con opción de filtrar únicamente las personas que rebasaron su tope individual.
     */
    public List<ConsumoPersonaDTO> obtenerPersonasCuota(Long cuotaId, Boolean soloSobreLimite) {
        if (cuotaId == null) {
            return Collections.emptyList();
        }

        CuotaExtraccionModel cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuota #" + cuotaId + " no encontrada"));

        List<ConsumoPersonaDTO> personas = consumoIndividualQuery.porPersona(cuota, new Date());

        if (Boolean.TRUE.equals(soloSobreLimite)) {
            return personas.stream().filter(p -> {
                boolean excesoPositivo = p.getExceso() != null && p.getExceso().compareTo(BigDecimal.ZERO) > 0;
                boolean pctExcedido = p.getPorcentaje() != null && p.getPorcentaje() > 100.0;
                return excesoPositivo || pctExcedido;
            }).collect(Collectors.toList());
        }

        return personas;
    }

    private Map<Long, DeclaracionRecolectorModel> hidratarRecolectores(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "RECOLECTOR".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) return Collections.emptyMap();

        List<DeclaracionRecolectorModel> list = em.createQuery(
                "SELECT d FROM DeclaracionRecolectorModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.comuna " +
                "WHERE d.id IN :ids", DeclaracionRecolectorModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionRecolectorModel> map = new HashMap<>();
        list.forEach(r -> map.put(r.getId(), r));
        return map;
    }

    private Map<Long, DeclaracionArmadorModel> hidratarArmadores(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "ARMADOR".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) return Collections.emptyMap();

        List<DeclaracionArmadorModel> list = em.createQuery(
                "SELECT d FROM DeclaracionArmadorModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.embarcacion " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.comuna " +
                "WHERE d.id IN :ids", DeclaracionArmadorModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionArmadorModel> map = new HashMap<>();
        list.forEach(a -> map.put(a.getId(), a));
        return map;
    }

    private Map<Long, DeclaracionAreaModel> hidratarAreas(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "AREA".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) return Collections.emptyMap();

        List<DeclaracionAreaModel> list = em.createQuery(
                "SELECT d FROM DeclaracionAreaModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.amerb a " +
                "LEFT JOIN FETCH a.comuna " +
                "WHERE d.id IN :ids", DeclaracionAreaModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionAreaModel> map = new HashMap<>();
        list.forEach(ar -> map.put(ar.getId(), ar));
        return map;
    }

    private static Double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : null;
    }

    private static Double parseDouble(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Double.parseDouble(str.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
