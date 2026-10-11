package com.trazalga.api.repositories;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.FiltroHallazgos;
import com.trazalga.api.dto.HallazgoDetalleDTO;
import com.trazalga.api.models.*;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;

/**
 * Repositorio de consulta optimizado para la Consola de Hallazgos (TA.3).
 * Implementa hidratación en lote en dos pasos para garantizar que cualquier página
 * se resuelva en exactamente 7 consultas o menos:
 * 1. Conteo total de marcas filtradas
 * 2. Página de marcas (declaracion_marca)
 * 3 a 7. Una consulta por tipo de declaración presente en la página con WHERE id IN (:ids)
 */
@Repository
@Transactional(readOnly = true)
public class HallazgoQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public Page<HallazgoDetalleDTO> buscar(FiltroHallazgos filtro, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        if (filtro != null) {
            if (filtro.getMarca() != null && !filtro.getMarca().isBlank()) {
                where.append(" AND m.marca = :marca ");
                params.put("marca", filtro.getMarca().trim().toUpperCase());
            }
            if (filtro.getResuelta() != null) {
                where.append(" AND m.resuelta = :resuelta ");
                params.put("resuelta", filtro.getResuelta());
            }
            if (filtro.getEstadoGestion() != null && !filtro.getEstadoGestion().isBlank()) {
                where.append(" AND m.estadoGestion = :estadoGestion ");
                params.put("estadoGestion", filtro.getEstadoGestion().trim().toUpperCase());
            }
            if (filtro.getTipo() != null && !filtro.getTipo().isBlank()) {
                where.append(" AND m.declaracionTipo = :tipo ");
                params.put("tipo", filtro.getTipo().trim().toUpperCase());
            }
            if (filtro.getReglaId() != null) {
                where.append(" AND m.reglaId = :reglaId ");
                params.put("reglaId", filtro.getReglaId());
            }
            if (filtro.getDesde() != null) {
                where.append(" AND m.createdAt >= :desde ");
                params.put("desde", filtro.getDesde());
            }
            if (filtro.getHasta() != null) {
                where.append(" AND m.createdAt <= :hasta ");
                params.put("hasta", filtro.getHasta());
            }
        }

        // Consulta 1: Conteo total
        TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(m) FROM DeclaracionMarcaModel m " + where, Long.class);
        params.forEach(countQuery::setParameter);
        Long total = countQuery.getSingleResult();

        if (total == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Consulta 2: Página de marcas
        TypedQuery<DeclaracionMarcaModel> pageQuery = em.createQuery(
                "SELECT m FROM DeclaracionMarcaModel m " + where + " ORDER BY m.createdAt DESC, m.id DESC",
                DeclaracionMarcaModel.class);
        params.forEach(pageQuery::setParameter);
        pageQuery.setFirstResult((int) pageable.getOffset());
        pageQuery.setMaxResults(pageable.getPageSize());
        List<DeclaracionMarcaModel> marcas = pageQuery.getResultList();

        // Consultas 3 a 7: Hidratación por lotes de entidades operativas presentes
        Map<Long, DeclaracionRecolectorModel> recolectores = hidratarRecolectores(marcas);
        Map<Long, DeclaracionArmadorModel> armadores = hidratarArmadores(marcas);
        Map<Long, DeclaracionAreaModel> areas = hidratarAreas(marcas);
        Map<Long, DeclaracionComercializadorModel> comercializadores = hidratarComercializadores(marcas);
        Map<Long, DeclaracionPlantaAbastecimientoModel> plantas = hidratarPlantas(marcas);

        // Mapeo a HallazgoDetalleDTO
        List<HallazgoDetalleDTO> dtos = marcas.stream().map(m -> {
            HallazgoDetalleDTO dto = HallazgoDetalleDTO.builder()
                    .id(m.getId())
                    .marca(m.getMarca())
                    .reglaId(m.getReglaId())
                    .detalle(m.getDetalle())
                    .resuelta(m.getResuelta())
                    .estadoGestion(m.getEstadoGestion())
                    .resolucionTipo(m.getResolucionTipo())
                    .observacionResolucion(m.getObservacionResolucion())
                    .fechaResolucion(m.getFechaResolucion())
                    .resueltaPorUsuarioId(m.getResueltaPorUsuarioId())
                    .createdAt(m.getCreatedAt())
                    .origen(m.getOrigen())
                    .claveIdempotencia(m.getClaveIdempotencia())
                    .criterioParametro(m.getCriterioParametro())
                    .criterioUmbral(m.getCriterioUmbral())
                    .criterioValor(m.getCriterioValor())
                    .criterioUnidad(m.getCriterioUnidad())
                    .declaracionTipo(m.getDeclaracionTipo())
                    .declaracionId(m.getDeclaracionId())
                    .build();

            // Criterio estructurado con respaldo en detalle histórico
            if (m.getCriterioParametro() != null) {
                CriterioHallazgo crit = new CriterioHallazgo(
                        m.getCriterioParametro(),
                        m.getCriterioUmbral(),
                        m.getCriterioValor(),
                        m.getCriterioUnidad()
                );
                String txt = crit.texto();
                dto.setCriterioTexto(txt != null && !txt.isBlank() ? txt : m.getDetalle());
            } else {
                dto.setCriterioTexto(m.getDetalle());
            }

            // Enriquecer según tipo de declaración
            String tipoNorm = m.getDeclaracionTipo() != null ? m.getDeclaracionTipo().toUpperCase() : "";
            Long declId = m.getDeclaracionId();

            if ("RECOLECTOR".equals(tipoNorm) && recolectores.containsKey(declId)) {
                DeclaracionRecolectorModel r = recolectores.get(declId);
                dto.setFolio(r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen());
                dto.setFechaDeclaracion(r.getFechaDeclaracion());
                dto.setFechaExtraccion(r.getFechaExtraccion());
                dto.setHora(r.getHora());
                dto.setKilos(toDouble(r.getDesembarque()));
                dto.setCaptura(toDouble(r.getCaptura()));
                dto.setEspecie(r.getEspecie() != null ? r.getEspecie().getNombre() : null);
                dto.setHumedad(r.getHumedadEstado() != null ? r.getHumedadEstado().getNombre() : r.getHumedad());
                dto.setCaleta(r.getCaleta() != null ? r.getCaleta().getNombre() : null);
                dto.setComuna(r.getComuna() != null ? r.getComuna().getNombre() : null);
                dto.setActorRut(r.getUsuario() != null ? r.getUsuario().getRut() : null);
                dto.setActorNombre(r.getNombre() != null ? r.getNombre() : (r.getUsuario() != null ? r.getUsuario().getNombreCompleto() : null));
                dto.setActorPerfil("RECOLECTOR");
                dto.setRpa(r.getCodigoSernapesca());
            } else if ("ARMADOR".equals(tipoNorm) && armadores.containsKey(declId)) {
                DeclaracionArmadorModel a = armadores.get(declId);
                dto.setFolio(a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen());
                dto.setFechaDeclaracion(a.getFechaDeclaracion());
                dto.setFechaExtraccion(a.getFechaExtraccion());
                dto.setHora(a.getHora());
                dto.setKilos(toDouble(a.getDesembarque()));
                dto.setCaptura(a.getCaptura());
                dto.setEspecie(a.getEspecie() != null ? a.getEspecie().getNombre() : null);
                dto.setHumedad(a.getHumedadEstado() != null ? a.getHumedadEstado().getNombre() : null);
                dto.setCaleta(a.getCaleta() != null ? a.getCaleta().getNombre() : null);
                dto.setComuna(a.getComuna() != null ? a.getComuna().getNombre() : (a.getCaleta() != null && a.getCaleta().getComuna() != null ? a.getCaleta().getComuna().getNombre() : null));
                dto.setActorRut(a.getUsuario() != null ? a.getUsuario().getRut() : null);
                dto.setActorNombre(a.getUsuario() != null ? a.getUsuario().getNombreCompleto() : null);
                dto.setActorPerfil("ARMADOR");
                dto.setRpa(a.getCodigoSernapescaEmbarcacion());
                dto.setEmbarcacionCodigo(a.getEmbarcacion() != null ? a.getEmbarcacion().getCodigo() : a.getCodigoSernapescaEmbarcacion());
                dto.setEmbarcacionNombre(a.getEmbarcacion() != null ? a.getEmbarcacion().getNombre() : null);
            } else if ("AREA".equals(tipoNorm) && areas.containsKey(declId)) {
                DeclaracionAreaModel ar = areas.get(declId);
                dto.setFolio(ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb() : ar.getFolioOrigen());
                dto.setFechaDeclaracion(ar.getFechaDeclaracion());
                dto.setFechaExtraccion(ar.getFechaExtraccion());
                dto.setHora(ar.getHora());
                dto.setKilos(ar.getDesembarque());
                dto.setCaptura(ar.getCaptura());
                dto.setEspecie(ar.getEspecie() != null ? ar.getEspecie().getNombre() : null);
                dto.setHumedad(ar.getHumedadEstado() != null ? ar.getHumedadEstado().getNombre() : null);
                dto.setCaleta(ar.getCaleta() != null ? ar.getCaleta().getNombre() : null);
                dto.setComuna(ar.getAmerb() != null && ar.getAmerb().getComuna() != null ? ar.getAmerb().getComuna().getNombre() : null);
                dto.setActorRut(ar.getUsuario() != null ? ar.getUsuario().getRut() : null);
                dto.setActorNombre(ar.getUsuario() != null ? ar.getUsuario().getNombreCompleto() : null);
                dto.setActorPerfil("AREA");
                dto.setRpa(ar.getCodigoSernapescaAmerb());
            } else if ("COMERCIALIZADOR".equals(tipoNorm) && comercializadores.containsKey(declId)) {
                DeclaracionComercializadorModel c = comercializadores.get(declId);
                dto.setFolio(c.getFolioDesembarqueAc() != null ? c.getFolioDesembarqueAc() : c.getFolioOrigen());
                dto.setFechaDeclaracion(c.getFechaDeclaracion());
                dto.setFechaExtraccion(c.getFechaTraslado() != null ? c.getFechaTraslado() : c.getFechaDeclaracion());
                dto.setHora(c.getHora());
                dto.setKilos(toDouble(c.getCantidad()));
                dto.setCaptura(null);
                dto.setEspecie(c.getEspecie() != null ? c.getEspecie().getNombre() : null);
                dto.setHumedad(c.getHumedadEstado() != null ? c.getHumedadEstado().getNombre() : null);
                dto.setCaleta(null);
                dto.setComuna(null);
                dto.setActorRut(c.getUsuario() != null ? c.getUsuario().getRut() : null);
                dto.setActorNombre(c.getNombreComercializador() != null ? c.getNombreComercializador() : (c.getUsuario() != null ? c.getUsuario().getNombreCompleto() : null));
                dto.setActorPerfil("COMERCIALIZADOR");
                dto.setRpa(c.getCodigoSernapesca());
            } else if (("PLANTA".equals(tipoNorm) || "PLANTA_ABASTECIMIENTO".equals(tipoNorm)) && plantas.containsKey(declId)) {
                DeclaracionPlantaAbastecimientoModel p = plantas.get(declId);
                dto.setFolio(p.getFolioDeclaracionAPla() != null ? p.getFolioDeclaracionAPla() : p.getFolioOrigen());
                dto.setFechaDeclaracion(p.getFechaIngresoPlanta());
                dto.setFechaExtraccion(p.getFechaTraslado() != null ? p.getFechaTraslado() : p.getFechaIngresoPlanta());
                dto.setHora(p.getHora());
                dto.setKilos(toDouble(p.getCantidad()));
                dto.setCaptura(null);
                dto.setEspecie(p.getEspecie() != null ? p.getEspecie().getNombre() : null);
                dto.setHumedad(p.getHumedadEstado() != null ? p.getHumedadEstado().getNombre() : null);
                dto.setCaleta(null);
                dto.setComuna(null);
                dto.setActorRut(p.getUsuario() != null ? p.getUsuario().getRut() : null);
                dto.setActorNombre(p.getNombrePlanta() != null ? p.getNombrePlanta() : (p.getUsuario() != null ? p.getUsuario().getNombreCompleto() : null));
                dto.setActorPerfil("PLANTA");
                dto.setRpa(p.getCodigoSernapesca());
            }

            return dto;
        }).collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, total);
    }

    private Map<Long, DeclaracionRecolectorModel> hidratarRecolectores(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "RECOLECTOR".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DeclaracionRecolectorModel> list = em.createQuery(
                "SELECT d FROM DeclaracionRecolectorModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.comuna " +
                "WHERE d.id IN :ids", DeclaracionRecolectorModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionRecolectorModel> map = new HashMap<>();
        list.forEach(d -> map.put(d.getId(), d));
        return map;
    }

    private Map<Long, DeclaracionArmadorModel> hidratarArmadores(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "ARMADOR".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DeclaracionArmadorModel> list = em.createQuery(
                "SELECT d FROM DeclaracionArmadorModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.comuna " +
                "LEFT JOIN FETCH d.embarcacion " +
                "WHERE d.id IN :ids", DeclaracionArmadorModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionArmadorModel> map = new HashMap<>();
        list.forEach(d -> map.put(d.getId(), d));
        return map;
    }

    private Map<Long, DeclaracionAreaModel> hidratarAreas(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "AREA".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DeclaracionAreaModel> list = em.createQuery(
                "SELECT d FROM DeclaracionAreaModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.amerb " +
                "WHERE d.id IN :ids", DeclaracionAreaModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionAreaModel> map = new HashMap<>();
        list.forEach(d -> map.put(d.getId(), d));
        return map;
    }

    private Map<Long, DeclaracionComercializadorModel> hidratarComercializadores(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> "COMERCIALIZADOR".equalsIgnoreCase(m.getDeclaracionTipo()) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DeclaracionComercializadorModel> list = em.createQuery(
                "SELECT d FROM DeclaracionComercializadorModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "WHERE d.id IN :ids", DeclaracionComercializadorModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionComercializadorModel> map = new HashMap<>();
        list.forEach(d -> map.put(d.getId(), d));
        return map;
    }

    private Map<Long, DeclaracionPlantaAbastecimientoModel> hidratarPlantas(List<DeclaracionMarcaModel> marcas) {
        Set<Long> ids = marcas.stream()
                .filter(m -> ("PLANTA".equalsIgnoreCase(m.getDeclaracionTipo()) || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(m.getDeclaracionTipo())) && m.getDeclaracionId() != null)
                .map(DeclaracionMarcaModel::getDeclaracionId)
                .collect(Collectors.toSet());

        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DeclaracionPlantaAbastecimientoModel> list = em.createQuery(
                "SELECT d FROM DeclaracionPlantaAbastecimientoModel d " +
                "LEFT JOIN FETCH d.usuario " +
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "WHERE d.id IN :ids", DeclaracionPlantaAbastecimientoModel.class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, DeclaracionPlantaAbastecimientoModel> map = new HashMap<>();
        list.forEach(d -> map.put(d.getId(), d));
        return map;
    }

    private Double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : null;
    }
}
