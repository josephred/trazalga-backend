package com.trazalga.api.services.indicadores;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.DesembarqueAtipicoDTO;
import com.trazalga.api.dto.FiltroDesembarqueAtipico;
import com.trazalga.api.models.*;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de auditoría para Desembarques Físicos Atípicos (TD.1 / TD.2).
 * Combina en memoria de forma optimizada dos fuentes:
 * 1. Marcas oficiales DESEMBARQUE_ATIPICO registradas al declarar (conservando su umbral histórico).
 * 2. Declaraciones operativas sin marca que superan el umbral vigente (con rótulo 'umbral vigente').
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class DesembarqueAtipicoService {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    @Lazy
    private ConfiguracionGeneralService configService;

    @Autowired(required = false)
    private List<CriterioAtipico> criterios;

    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");

    public Page<DesembarqueAtipicoDTO> buscar(FiltroDesembarqueAtipico filtro, Pageable pageable) {
        double umbralVigente = (configService != null)
                ? configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)
                : 5000.0;

        String fuenteFiltro = (filtro != null && filtro.getFuente() != null)
                ? filtro.getFuente().trim().toUpperCase()
                : "TODAS";

        List<DesembarqueAtipicoDTO> resultados = new ArrayList<>();

        // ---------------------------------------------------------------------
        // FUENTE 1: Marcas DESEMBARQUE_ATIPICO registradas
        // ---------------------------------------------------------------------
        if (!"VIGENTE".equals(fuenteFiltro)) {
            List<DesembarqueAtipicoDTO> filasMarca = cargarMarcasAtipicas(filtro);
            resultados.addAll(filasMarca);
        }

        // Conjunto de claves ya marcadas para no duplicar en Fuente 2
        Set<String> clavesConMarca = resultados.stream()
                .map(r -> r.getTipo().toUpperCase() + ":" + r.getDeclaracionId())
                .collect(Collectors.toSet());

        // ---------------------------------------------------------------------
        // FUENTE 2: Declaraciones operativas sin marca sobre el umbral vigente
        // ---------------------------------------------------------------------
        if (!"MARCA".equals(fuenteFiltro)) {
            List<DesembarqueAtipicoDTO> filasVigentes = cargarDeclaracionesSobreUmbral(filtro, umbralVigente, clavesConMarca);
            resultados.addAll(filasVigentes);
        }

        // ---------------------------------------------------------------------
        // Filtros adicionales en memoria (criterio, estado de gestión, etc.)
        // ---------------------------------------------------------------------
        List<DesembarqueAtipicoDTO> filtrados = resultados.stream().filter(r -> {
            if (filtro == null) return true;

            if (filtro.getPerfil() != null && !"TODOS".equalsIgnoreCase(filtro.getPerfil())) {
                if (!filtro.getPerfil().equalsIgnoreCase(r.getPerfil())) return false;
            }

            if (filtro.getEstadoGestion() != null && !filtro.getEstadoGestion().isBlank()) {
                String eg = filtro.getEstadoGestion().trim().toUpperCase();
                if ("SIN_GESTIONAR".equals(eg)) {
                    if (!("PENDIENTE".equalsIgnoreCase(r.getEstadoGestion()) || "SIN_MARCA".equalsIgnoreCase(r.getEstadoGestion()))) {
                        return false;
                    }
                } else if (!eg.equalsIgnoreCase(r.getEstadoGestion())) {
                    return false;
                }
            }

            if (filtro.getCriterioTipo() != null && !filtro.getCriterioTipo().isBlank()) {
                String ct = filtro.getCriterioTipo().trim().toUpperCase();
                if ("ESTADISTICO".equals(ct)) {
                    if (!"desembarque_atipico_sigma".equalsIgnoreCase(r.getCriterioParametro())) return false;
                } else if ("UMBRAL".equals(ct)) {
                    if (!"desembarque_umbral_atipico_kg".equalsIgnoreCase(r.getCriterioParametro())) return false;
                }
            }

            return true;
        }).collect(Collectors.toList());

        // Orden cronológico descendente: fechaDeclaracion DESC, hora DESC
        filtrados.sort((a, b) -> {
            Date fa = a.getFechaDeclaracion() != null ? a.getFechaDeclaracion() : a.getFechaExtraccion();
            Date fb = b.getFechaDeclaracion() != null ? b.getFechaDeclaracion() : b.getFechaExtraccion();
            if (fa == null && fb == null) return 0;
            if (fa == null) return 1;
            if (fb == null) return -1;
            int cmp = fb.compareTo(fa);
            if (cmp != 0) return cmp;
            String ha = a.getHora() != null ? a.getHora() : "";
            String hb = b.getHora() != null ? b.getHora() : "";
            return hb.compareTo(ha);
        });

        // Paginación
        int total = filtrados.size();
        if (pageable == null) {
            return new PageImpl<>(filtrados);
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<DesembarqueAtipicoDTO> pagedList = (start < total) ? filtrados.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(pagedList, pageable, total);
    }

    private List<DesembarqueAtipicoDTO> cargarMarcasAtipicas(FiltroDesembarqueAtipico filtro) {
        StringBuilder hql = new StringBuilder("SELECT m FROM DeclaracionMarcaModel m WHERE m.marca = 'DESEMBARQUE_ATIPICO' ");
        Map<String, Object> params = new HashMap<>();

        if (filtro != null) {
            if (filtro.getPerfil() != null && !"TODOS".equalsIgnoreCase(filtro.getPerfil())) {
                hql.append(" AND UPPER(m.declaracionTipo) = :tipo ");
                params.put("tipo", filtro.getPerfil().trim().toUpperCase());
            }
            if (filtro.getEstadoGestion() != null && !filtro.getEstadoGestion().isBlank() && !"SIN_GESTIONAR".equalsIgnoreCase(filtro.getEstadoGestion())) {
                hql.append(" AND UPPER(m.estadoGestion) = :estadoGestion ");
                params.put("estadoGestion", filtro.getEstadoGestion().trim().toUpperCase());
            }
        }

        hql.append(" ORDER BY m.createdAt DESC, m.id DESC");
        var query = em.createQuery(hql.toString(), DeclaracionMarcaModel.class);
        params.forEach(query::setParameter);

        List<DeclaracionMarcaModel> marcas = query.getResultList();
        if (marcas.isEmpty()) {
            return Collections.emptyList();
        }

        // Hidratación en lote por tipo de declaración
        Map<Long, DeclaracionRecolectorModel> recolectores = hidratarRecolectores(marcas);
        Map<Long, DeclaracionArmadorModel> armadores = hidratarArmadores(marcas);
        Map<Long, DeclaracionAreaModel> areas = hidratarAreas(marcas);

        List<DesembarqueAtipicoDTO> dtos = new ArrayList<>();

        for (DeclaracionMarcaModel m : marcas) {
            String tipoNorm = m.getDeclaracionTipo() != null ? m.getDeclaracionTipo().toUpperCase() : "";
            Long declId = m.getDeclaracionId();

            DesembarqueAtipicoDTO dto = DesembarqueAtipicoDTO.builder()
                    .id("MARCA-" + m.getId())
                    .tipo(tipoNorm)
                    .perfil(tipoNorm)
                    .declaracionId(declId)
                    .marcaId(m.getId())
                    .resuelta(m.getResuelta())
                    .estadoGestion(m.getEstadoGestion() != null ? m.getEstadoGestion() : (Boolean.TRUE.equals(m.getResuelta()) ? "RESUELTA" : "PENDIENTE"))
                    .fuente("MARCA")
                    .criterioParametro(m.getCriterioParametro())
                    .criterioUmbral(m.getCriterioUmbral())
                    .criterioValor(m.getCriterioValor())
                    .criterioUnidad(m.getCriterioUnidad())
                    .build();

            // Criterio estructurado con conservación del umbral original registrado
            if (m.getCriterioParametro() != null) {
                if ("desembarque_atipico_sigma".equalsIgnoreCase(m.getCriterioParametro())) {
                    dto.setCriterioTexto(m.getDetalle());
                } else {
                    String umbralReg = m.getCriterioUmbral() != null ? formatearKilos(m.getCriterioUmbral()) : "5.000";
                    String valorReg = m.getCriterioValor() != null ? formatearKilos(m.getCriterioValor()) : "";
                    dto.setCriterioTexto(String.format("Desembarque de %s kg supera el umbral de %s kg (registrado)", valorReg, umbralReg));
                }
            } else {
                dto.setCriterioTexto(m.getDetalle() != null ? m.getDetalle() : "Desembarque atípico registrado");
            }

            // Enriquecer datos operativos
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
                dto.setProvincia(r.getComuna() != null && r.getComuna().getProvincia() != null ? r.getComuna().getProvincia().getNombre() : null);
                dto.setRegion(r.getComuna() != null && r.getComuna().getRegion() != null ? r.getComuna().getRegion().getNombre() : null);
                dto.setRut(r.getUsuario() != null ? r.getUsuario().getRut() : null);
                dto.setNombre(r.getNombre() != null ? r.getNombre() : (r.getUsuario() != null ? r.getUsuario().getNombreCompleto() : null));
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
                dto.setProvincia(a.getComuna() != null && a.getComuna().getProvincia() != null ? a.getComuna().getProvincia().getNombre() : null);
                dto.setRegion(a.getComuna() != null && a.getComuna().getRegion() != null ? a.getComuna().getRegion().getNombre() : null);
                dto.setRut(a.getUsuario() != null ? a.getUsuario().getRut() : null);
                dto.setNombre(a.getUsuario() != null ? a.getUsuario().getNombreCompleto() : null);
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
                dto.setRut(ar.getUsuario() != null ? ar.getUsuario().getRut() : null);
                dto.setNombre(ar.getUsuario() != null ? ar.getUsuario().getNombreCompleto() : null);
            }

            // Aplicar filtros dimensionales sobre los datos operativos
            if (cumpleFiltrosDimensionales(dto, filtro)) {
                dtos.add(dto);
            }
        }

        return dtos;
    }

    private List<DesembarqueAtipicoDTO> cargarDeclaracionesSobreUmbral(
            FiltroDesembarqueAtipico filtro,
            double umbralVigente,
            Set<String> clavesConMarca
    ) {
        List<DesembarqueAtipicoDTO> dtos = new ArrayList<>();
        String perfil = filtro != null ? filtro.getPerfil() : null;

        // 1. Recolectores
        if (perfil == null || "TODOS".equalsIgnoreCase(perfil) || "RECOLECTOR".equalsIgnoreCase(perfil)) {
            String jpql = "SELECT d FROM DeclaracionRecolectorModel d " +
                    "LEFT JOIN FETCH d.usuario " +
                    "LEFT JOIN FETCH d.especie " +
                    "LEFT JOIN FETCH d.humedadEstado " +
                    "LEFT JOIN FETCH d.caleta " +
                    "LEFT JOIN FETCH d.comuna c " +
                    "LEFT JOIN FETCH c.provincia p " +
                    "LEFT JOIN FETCH c.region " +
                    "WHERE d.desembarque > :umbral";
            var q = em.createQuery(jpql, DeclaracionRecolectorModel.class);
            q.setParameter("umbral", BigDecimal.valueOf(umbralVigente));
            for (DeclaracionRecolectorModel r : q.getResultList()) {
                if (clavesConMarca.contains("RECOLECTOR:" + r.getId())) continue;
                double kilos = toDouble(r.getDesembarque());
                DesembarqueAtipicoDTO d = DesembarqueAtipicoDTO.builder()
                        .id("VIGENTE-RECOLECTOR-" + r.getId())
                        .tipo("RECOLECTOR")
                        .perfil("RECOLECTOR")
                        .declaracionId(r.getId())
                        .folio(r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen())
                        .fechaDeclaracion(r.getFechaDeclaracion())
                        .fechaExtraccion(r.getFechaExtraccion())
                        .hora(r.getHora())
                        .kilos(kilos)
                        .captura(toDouble(r.getCaptura()))
                        .especie(r.getEspecie() != null ? r.getEspecie().getNombre() : null)
                        .humedad(r.getHumedadEstado() != null ? r.getHumedadEstado().getNombre() : r.getHumedad())
                        .caleta(r.getCaleta() != null ? r.getCaleta().getNombre() : null)
                        .comuna(r.getComuna() != null ? r.getComuna().getNombre() : null)
                        .provincia(r.getComuna() != null && r.getComuna().getProvincia() != null ? r.getComuna().getProvincia().getNombre() : null)
                        .region(r.getComuna() != null && r.getComuna().getRegion() != null ? r.getComuna().getRegion().getNombre() : null)
                        .rut(r.getUsuario() != null ? r.getUsuario().getRut() : null)
                        .nombre(r.getNombre() != null ? r.getNombre() : (r.getUsuario() != null ? r.getUsuario().getNombreCompleto() : null))
                        .fuente("VIGENTE")
                        .marcaId(null)
                        .resuelta(false)
                        .estadoGestion("SIN_MARCA")
                        .criterioParametro("desembarque_umbral_atipico_kg")
                        .criterioUmbral(String.valueOf(umbralVigente))
                        .criterioValor(String.valueOf(kilos))
                        .criterioUnidad("kg")
                        .criterioTexto(String.format("Desembarque de %s kg supera el umbral vigente de %s kg",
                                formatearKilos(String.valueOf(kilos)), formatearKilos(String.valueOf(umbralVigente))))
                        .build();

                if (cumpleFiltrosDimensionales(d, filtro)) {
                    dtos.add(d);
                }
            }
        }

        // 2. Armadores
        if (perfil == null || "TODOS".equalsIgnoreCase(perfil) || "ARMADOR".equalsIgnoreCase(perfil)) {
            String jpql = "SELECT d FROM DeclaracionArmadorModel d " +
                    "LEFT JOIN FETCH d.usuario " +
                    "LEFT JOIN FETCH d.embarcacion " +
                    "LEFT JOIN FETCH d.especie " +
                    "LEFT JOIN FETCH d.humedadEstado " +
                    "LEFT JOIN FETCH d.caleta " +
                    "LEFT JOIN FETCH d.comuna c " +
                    "LEFT JOIN FETCH c.provincia p " +
                    "LEFT JOIN FETCH c.region " +
                    "WHERE d.desembarque > :umbral";
            var q = em.createQuery(jpql, DeclaracionArmadorModel.class);
            q.setParameter("umbral", BigDecimal.valueOf(umbralVigente));
            for (DeclaracionArmadorModel a : q.getResultList()) {
                if (clavesConMarca.contains("ARMADOR:" + a.getId())) continue;
                double kilos = toDouble(a.getDesembarque());
                DesembarqueAtipicoDTO d = DesembarqueAtipicoDTO.builder()
                        .id("VIGENTE-ARMADOR-" + a.getId())
                        .tipo("ARMADOR")
                        .perfil("ARMADOR")
                        .declaracionId(a.getId())
                        .folio(a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen())
                        .fechaDeclaracion(a.getFechaDeclaracion())
                        .fechaExtraccion(a.getFechaExtraccion())
                        .hora(a.getHora())
                        .kilos(kilos)
                        .captura(a.getCaptura())
                        .especie(a.getEspecie() != null ? a.getEspecie().getNombre() : null)
                        .humedad(a.getHumedadEstado() != null ? a.getHumedadEstado().getNombre() : null)
                        .caleta(a.getCaleta() != null ? a.getCaleta().getNombre() : null)
                        .comuna(a.getComuna() != null ? a.getComuna().getNombre() : null)
                        .provincia(a.getComuna() != null && a.getComuna().getProvincia() != null ? a.getComuna().getProvincia().getNombre() : null)
                        .region(a.getComuna() != null && a.getComuna().getRegion() != null ? a.getComuna().getRegion().getNombre() : null)
                        .rut(a.getUsuario() != null ? a.getUsuario().getRut() : null)
                        .nombre(a.getUsuario() != null ? a.getUsuario().getNombreCompleto() : null)
                        .embarcacionCodigo(a.getEmbarcacion() != null ? a.getEmbarcacion().getCodigo() : a.getCodigoSernapescaEmbarcacion())
                        .embarcacionNombre(a.getEmbarcacion() != null ? a.getEmbarcacion().getNombre() : null)
                        .fuente("VIGENTE")
                        .marcaId(null)
                        .resuelta(false)
                        .estadoGestion("SIN_MARCA")
                        .criterioParametro("desembarque_umbral_atipico_kg")
                        .criterioUmbral(String.valueOf(umbralVigente))
                        .criterioValor(String.valueOf(kilos))
                        .criterioUnidad("kg")
                        .criterioTexto(String.format("Desembarque de %s kg supera el umbral vigente de %s kg",
                                formatearKilos(String.valueOf(kilos)), formatearKilos(String.valueOf(umbralVigente))))
                        .build();

                if (cumpleFiltrosDimensionales(d, filtro)) {
                    dtos.add(d);
                }
            }
        }

        // 3. Áreas de Manejo
        if (perfil == null || "TODOS".equalsIgnoreCase(perfil) || "AREA".equalsIgnoreCase(perfil)) {
            String jpql = "SELECT d FROM DeclaracionAreaModel d " +
                    "LEFT JOIN FETCH d.usuario " +
                    "LEFT JOIN FETCH d.especie " +
                    "LEFT JOIN FETCH d.humedadEstado " +
                    "LEFT JOIN FETCH d.caleta " +
                    "LEFT JOIN FETCH d.amerb a " +
                    "LEFT JOIN FETCH a.comuna c " +
                    "WHERE d.desembarque > :umbral";
            var q = em.createQuery(jpql, DeclaracionAreaModel.class);
            q.setParameter("umbral", umbralVigente);
            for (DeclaracionAreaModel ar : q.getResultList()) {
                if (clavesConMarca.contains("AREA:" + ar.getId())) continue;
                double kilos = ar.getDesembarque() != null ? ar.getDesembarque() : 0.0;
                DesembarqueAtipicoDTO d = DesembarqueAtipicoDTO.builder()
                        .id("VIGENTE-AREA-" + ar.getId())
                        .tipo("AREA")
                        .perfil("AREA")
                        .declaracionId(ar.getId())
                        .folio(ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb() : ar.getFolioOrigen())
                        .fechaDeclaracion(ar.getFechaDeclaracion())
                        .fechaExtraccion(ar.getFechaExtraccion())
                        .hora(ar.getHora())
                        .kilos(kilos)
                        .captura(ar.getCaptura())
                        .especie(ar.getEspecie() != null ? ar.getEspecie().getNombre() : null)
                        .humedad(ar.getHumedadEstado() != null ? ar.getHumedadEstado().getNombre() : null)
                        .caleta(ar.getCaleta() != null ? ar.getCaleta().getNombre() : null)
                        .comuna(ar.getAmerb() != null && ar.getAmerb().getComuna() != null ? ar.getAmerb().getComuna().getNombre() : null)
                        .rut(ar.getUsuario() != null ? ar.getUsuario().getRut() : null)
                        .nombre(ar.getUsuario() != null ? ar.getUsuario().getNombreCompleto() : null)
                        .fuente("VIGENTE")
                        .marcaId(null)
                        .resuelta(false)
                        .estadoGestion("SIN_MARCA")
                        .criterioParametro("desembarque_umbral_atipico_kg")
                        .criterioUmbral(String.valueOf(umbralVigente))
                        .criterioValor(String.valueOf(kilos))
                        .criterioUnidad("kg")
                        .criterioTexto(String.format("Desembarque de %s kg supera el umbral vigente de %s kg",
                                formatearKilos(String.valueOf(kilos)), formatearKilos(String.valueOf(umbralVigente))))
                        .build();

                if (cumpleFiltrosDimensionales(d, filtro)) {
                    dtos.add(d);
                }
            }
        }

        return dtos;
    }

    private boolean cumpleFiltrosDimensionales(DesembarqueAtipicoDTO d, FiltroDesembarqueAtipico filtro) {
        if (filtro == null) return true;

        Date fecha = d.getFechaDeclaracion() != null ? d.getFechaDeclaracion() : d.getFechaExtraccion();
        if (filtro.getStartDate() != null && fecha != null && fecha.before(filtro.getStartDate())) {
            return false;
        }
        if (filtro.getEndDate() != null && fecha != null && fecha.after(filtro.getEndDate())) {
            return false;
        }

        return true;
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
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.comuna c " +
                "LEFT JOIN FETCH c.provincia " +
                "LEFT JOIN FETCH c.region " +
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
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
                "LEFT JOIN FETCH d.caleta " +
                "LEFT JOIN FETCH d.comuna c " +
                "LEFT JOIN FETCH c.provincia " +
                "LEFT JOIN FETCH c.region " +
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
                "LEFT JOIN FETCH d.especie " +
                "LEFT JOIN FETCH d.humedadEstado " +
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

    private static String formatearKilos(String str) {
        if (str == null || str.isBlank()) return "";
        try {
            double d = Double.parseDouble(str.replace(",", "."));
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_CL);
            symbols.setGroupingSeparator('.');
            symbols.setDecimalSeparator(',');
            if (d == (long) d) {
                return new DecimalFormat("#,##0", symbols).format((long) d);
            } else {
                return new DecimalFormat("#,##0.##", symbols).format(d);
            }
        } catch (NumberFormatException e) {
            return str;
        }
    }
}
