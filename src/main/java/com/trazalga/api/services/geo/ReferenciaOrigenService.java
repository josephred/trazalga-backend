package com.trazalga.api.services.geo;

import com.trazalga.api.dto.geo.PuntoReferencia;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servicio centralizado de resolución de punto geográfico de referencia (TG.2).
 * Implementa la jerarquía de cascada por tipo de declaración y mantiene
 * una caché en memoria de maestros (caleta, varadero, amerb).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferenciaOrigenService {

    private final ICaletaRepository caletaRepository;
    private final IVaraderoRepository varaderoRepository;
    private final IAmerbRepository amerbRepository;
    private final IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    // Caché en memoria de maestros
    private final Map<Long, CaletaModel> cacheCaleta = new ConcurrentHashMap<>();
    private final Map<Long, VaraderoModel> cacheVaradero = new ConcurrentHashMap<>();
    private final Map<Long, AmerbModel> cacheAmerb = new ConcurrentHashMap<>();

    // Índice auxiliar: comunaId -> lista de varaderos
    private final Map<Long, List<VaraderoModel>> varaderosPorComuna = new ConcurrentHashMap<>();

    @PostConstruct
    public void inicializarCache() {
        recargarCache();
    }

    public synchronized void invalidarCache() {
        recargarCache();
        log.info("Caché de ReferenciaOrigenService invalidada y recargada exitosamente.");
    }

    private void recargarCache() {
        try {
            cacheCaleta.clear();
            cacheVaradero.clear();
            cacheAmerb.clear();
            varaderosPorComuna.clear();

            List<CaletaModel> caletas = caletaRepository.findAll();
            for (CaletaModel c : caletas) {
                if (c.getId() != null) cacheCaleta.put(c.getId(), c);
            }

            List<VaraderoModel> varaderos = varaderoRepository.findAll();
            for (VaraderoModel v : varaderos) {
                if (v.getId() != null) {
                    cacheVaradero.put(v.getId(), v);
                    if (v.getComuna() != null && v.getComuna().getId() != null) {
                        varaderosPorComuna.computeIfAbsent(v.getComuna().getId(), k -> new ArrayList<>()).add(v);
                    }
                }
            }

            List<AmerbModel> amerbs = amerbRepository.findAll();
            for (AmerbModel a : amerbs) {
                if (a.getId() != null) cacheAmerb.put(a.getId(), a);
            }

            log.info("Caché ReferenciaOrigenService cargada: {} caletas, {} varaderos, {} AMERBs",
                    cacheCaleta.size(), cacheVaradero.size(), cacheAmerb.size());
        } catch (Exception e) {
            log.error("Error al inicializar/recargar caché de ReferenciaOrigenService", e);
        }
    }

    // -------------------------------------------------------------
    // Calce de varadero por texto y comuna
    // -------------------------------------------------------------
    public VaraderoModel calzarVaradero(String textoVaradero, Long comunaId) {
        if (textoVaradero == null || textoVaradero.trim().isEmpty() || comunaId == null) {
            return null;
        }
        String textoNorm = CoordenadaNormalizer.normalizarTexto(textoVaradero);
        if (textoNorm.isEmpty()) return null;

        List<VaraderoModel> varaderosComuna = varaderosPorComuna.get(comunaId);
        if (varaderosComuna == null || varaderosComuna.isEmpty()) {
            return null;
        }

        List<VaraderoModel> calzados = varaderosComuna.stream()
                .filter(v -> v.getNombre() != null && CoordenadaNormalizer.normalizarTexto(v.getNombre()).equals(textoNorm))
                .collect(Collectors.toList());

        if (calzados.size() == 1) {
            return calzados.get(0);
        } else if (calzados.size() > 1) {
            log.warn("Ambigüedad de varadero en comuna {}: texto '{}' calza con {} varaderos",
                    comunaId, textoVaradero, calzados.size());
            return null;
        }
        return null;
    }

    // -------------------------------------------------------------
    // Proceso histórico de vinculación
    // -------------------------------------------------------------
    @Transactional
    public int vincularVaraderosHistoricos() {
        List<DeclaracionRecolectorModel> lista = declaracionRecolectorRepository.findAll();
        int vinculados = 0;
        for (DeclaracionRecolectorModel dr : lista) {
            if (dr.getVaraderoRelacion() == null && dr.getVaradero() != null && !dr.getVaradero().trim().isEmpty()) {
                Long comunaId = dr.getComuna() != null ? dr.getComuna().getId() : (dr.getCaleta() != null && dr.getCaleta().getComuna() != null ? dr.getCaleta().getComuna().getId() : null);
                if (comunaId != null) {
                    VaraderoModel calzado = calzarVaradero(dr.getVaradero(), comunaId);
                    if (calzado != null) {
                        dr.setVaraderoRelacion(calzado);
                        declaracionRecolectorRepository.save(dr);
                        vinculados++;
                    }
                }
            }
        }
        log.info("Vinculación histórica TG.2 completada: {} declaraciones de recolector asociadas a varadero_id", vinculados);
        return vinculados;
    }

    // -------------------------------------------------------------
    // Métodos de resolución por tipo de entidad
    // -------------------------------------------------------------
    public PuntoReferencia resolverRecolector(DeclaracionRecolectorModel dr) {
        if (dr == null) return null;

        // 1. Varadero declarado (relación explícita o match por texto)
        if (dr.getVaraderoRelacion() != null && dr.getVaraderoRelacion().getId() != null) {
            VaraderoModel v = cacheVaradero.get(dr.getVaraderoRelacion().getId());
            if (v == null) v = dr.getVaraderoRelacion();
            if (v != null && v.getLatitud() != null && v.getLongitud() != null) {
                return PuntoReferencia.builder()
                        .tipo("VARADERO")
                        .id(v.getId())
                        .nombre(v.getNombre())
                        .lat(v.getLatitud())
                        .lon(v.getLongitud())
                        .fuente("VARADERO_DECLARADO")
                        .build();
            }
        } else if (dr.getVaradero() != null && !dr.getVaradero().trim().isEmpty()) {
            Long comunaId = dr.getComuna() != null ? dr.getComuna().getId() : (dr.getCaleta() != null && dr.getCaleta().getComuna() != null ? dr.getCaleta().getComuna().getId() : null);
            VaraderoModel vCalzado = calzarVaradero(dr.getVaradero(), comunaId);
            if (vCalzado != null && vCalzado.getLatitud() != null && vCalzado.getLongitud() != null) {
                return PuntoReferencia.builder()
                        .tipo("VARADERO")
                        .id(vCalzado.getId())
                        .nombre(vCalzado.getNombre())
                        .lat(vCalzado.getLatitud())
                        .lon(vCalzado.getLongitud())
                        .fuente("VARADERO_DECLARADO")
                        .build();
            }
        }

        // 2. Caleta declarada
        Long caletaId = dr.getCaleta() != null ? dr.getCaleta().getId() : null;
        if (caletaId != null) {
            CaletaModel c = cacheCaleta.get(caletaId);
            if (c == null) c = dr.getCaleta();
            if (c != null && c.getLatitud() != null && c.getLongitud() != null) {
                return PuntoReferencia.builder()
                        .tipo("CALETA")
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .lat(c.getLatitud())
                        .lon(c.getLongitud())
                        .fuente("CALETA")
                        .build();
            }
            // 3. Varadero de la caleta
            if (c != null && c.getVaradero() != null && c.getVaradero().getId() != null) {
                VaraderoModel vc = cacheVaradero.get(c.getVaradero().getId());
                if (vc == null) vc = c.getVaradero();
                if (vc != null && vc.getLatitud() != null && vc.getLongitud() != null) {
                    return PuntoReferencia.builder()
                            .tipo("VARADERO")
                            .id(vc.getId())
                            .nombre(vc.getNombre())
                            .lat(vc.getLatitud())
                            .lon(vc.getLongitud())
                            .fuente("VARADERO_CALETA")
                            .build();
                }
            }
        }
        return null;
    }

    public PuntoReferencia resolverArmador(DeclaracionArmadorModel da) {
        if (da == null) return null;

        Long caletaId = da.getCaleta() != null ? da.getCaleta().getId() : null;
        if (caletaId != null) {
            // 1. Caleta de desembarque
            CaletaModel c = cacheCaleta.get(caletaId);
            if (c == null) c = da.getCaleta();
            if (c != null && c.getLatitud() != null && c.getLongitud() != null) {
                return PuntoReferencia.builder()
                        .tipo("CALETA")
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .lat(c.getLatitud())
                        .lon(c.getLongitud())
                        .fuente("CALETA")
                        .build();
            }
            // 2. Varadero de la caleta
            if (c != null && c.getVaradero() != null && c.getVaradero().getId() != null) {
                VaraderoModel vc = cacheVaradero.get(c.getVaradero().getId());
                if (vc == null) vc = c.getVaradero();
                if (vc != null && vc.getLatitud() != null && vc.getLongitud() != null) {
                    return PuntoReferencia.builder()
                            .tipo("VARADERO")
                            .id(vc.getId())
                            .nombre(vc.getNombre())
                            .lat(vc.getLatitud())
                            .lon(vc.getLongitud())
                            .fuente("VARADERO_CALETA")
                            .build();
                }
            }
        }
        return null;
    }

    public PuntoReferencia resolverArea(DeclaracionAreaModel dam) {
        if (dam == null) return null;

        // 1. AMERB
        Long amerbId = dam.getAmerb() != null ? dam.getAmerb().getId() : null;
        if (amerbId != null) {
            AmerbModel am = cacheAmerb.get(amerbId);
            if (am == null) am = dam.getAmerb();
            if (am != null && am.getLatitud() != null && am.getLongitud() != null) {
                return PuntoReferencia.builder()
                        .tipo("AMERB")
                        .id(am.getId())
                        .nombre(am.getNombre())
                        .lat(am.getLatitud())
                        .lon(am.getLongitud())
                        .fuente("AMERB")
                        .build();
            }
        }

        // 2. Caleta
        Long caletaId = dam.getCaleta() != null ? dam.getCaleta().getId() : null;
        if (caletaId != null) {
            CaletaModel c = cacheCaleta.get(caletaId);
            if (c == null) c = dam.getCaleta();
            if (c != null && c.getLatitud() != null && c.getLongitud() != null) {
                return PuntoReferencia.builder()
                        .tipo("CALETA")
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .lat(c.getLatitud())
                        .lon(c.getLongitud())
                        .fuente("CALETA")
                        .build();
            }
            // 3. Varadero de la caleta
            if (c != null && c.getVaradero() != null && c.getVaradero().getId() != null) {
                VaraderoModel vc = cacheVaradero.get(c.getVaradero().getId());
                if (vc == null) vc = c.getVaradero();
                if (vc != null && vc.getLatitud() != null && vc.getLongitud() != null) {
                    return PuntoReferencia.builder()
                            .tipo("VARADERO")
                            .id(vc.getId())
                            .nombre(vc.getNombre())
                            .lat(vc.getLatitud())
                            .lon(vc.getLongitud())
                            .fuente("VARADERO_CALETA")
                            .build();
                }
            }
        }
        return null;
    }

    public PuntoReferencia resolverComercializador(DeclaracionComercializadorModel dc) {
        if (dc == null) return null;
        if (dc.getLatitud() != null && dc.getLongitud() != null) {
            return PuntoReferencia.builder()
                    .tipo("LUGAR_DECLARACION")
                    .id(dc.getId())
                    .nombre("Lugar de Declaración Comercializador")
                    .lat(dc.getLatitud())
                    .lon(dc.getLongitud())
                    .fuente("GPS_DECLARACION")
                    .build();
        }
        return null;
    }

    public PuntoReferencia resolverPlanta(DeclaracionPlantaAbastecimientoModel dp) {
        if (dp == null) return null;
        if (dp.getLatitud() != null && dp.getLongitud() != null) {
            return PuntoReferencia.builder()
                    .tipo("PLANTA_RECEPCION")
                    .id(dp.getId())
                    .nombre("Lugar de Recepción Planta")
                    .lat(dp.getLatitud())
                    .lon(dp.getLongitud())
                    .fuente("GPS_DECLARACION")
                    .build();
        }
        return null;
    }

    /**
     * Firma unificada requerida por TG.2:
     * resolver(tipoDeclaracion, declaracion)
     */
    public PuntoReferencia resolver(String tipoDeclaracion, Object declaracion) {
        if (tipoDeclaracion == null || declaracion == null) return null;
        String tipoNorm = tipoDeclaracion.trim().toUpperCase();

        switch (tipoNorm) {
            case "RECOLECTOR":
                if (declaracion instanceof DeclaracionRecolectorModel) {
                    return resolverRecolector((DeclaracionRecolectorModel) declaracion);
                }
                break;
            case "ARMADOR":
                if (declaracion instanceof DeclaracionArmadorModel) {
                    return resolverArmador((DeclaracionArmadorModel) declaracion);
                }
                break;
            case "AREA":
            case "AMERB":
                if (declaracion instanceof DeclaracionAreaModel) {
                    return resolverArea((DeclaracionAreaModel) declaracion);
                }
                break;
            case "COMERCIALIZADOR":
                if (declaracion instanceof DeclaracionComercializadorModel) {
                    return resolverComercializador((DeclaracionComercializadorModel) declaracion);
                }
                break;
            case "PLANTA":
            case "PLANTA_ABASTECIMIENTO":
                if (declaracion instanceof DeclaracionPlantaAbastecimientoModel) {
                    return resolverPlanta((DeclaracionPlantaAbastecimientoModel) declaracion);
                }
                break;
        }

        return null;
    }

    /**
     * Resolución directa por IDs (útil para consultas SQL / reportes donde solo se tienen IDs)
     */
    public PuntoReferencia resolverPorIds(String tipoDeclaracion, Long caletaId, Long amerbId, Long varaderoId, String textoVaradero, Long comunaId) {
        String tipoNorm = tipoDeclaracion != null ? tipoDeclaracion.trim().toUpperCase() : "";

        if ("RECOLECTOR".equals(tipoNorm)) {
            // 1. Varadero
            if (varaderoId != null) {
                VaraderoModel v = cacheVaradero.get(varaderoId);
                if (v != null && v.getLatitud() != null && v.getLongitud() != null) {
                    return new PuntoReferencia("VARADERO", v.getId(), v.getNombre(), v.getLatitud(), v.getLongitud(), "VARADERO_DECLARADO");
                }
            } else if (textoVaradero != null && comunaId != null) {
                VaraderoModel v = calzarVaradero(textoVaradero, comunaId);
                if (v != null && v.getLatitud() != null && v.getLongitud() != null) {
                    return new PuntoReferencia("VARADERO", v.getId(), v.getNombre(), v.getLatitud(), v.getLongitud(), "VARADERO_DECLARADO");
                }
            }
            // 2. Caleta
            if (caletaId != null) {
                CaletaModel c = cacheCaleta.get(caletaId);
                if (c != null && c.getLatitud() != null && c.getLongitud() != null) {
                    return new PuntoReferencia("CALETA", c.getId(), c.getNombre(), c.getLatitud(), c.getLongitud(), "CALETA");
                }
                if (c != null && c.getVaradero() != null && c.getVaradero().getId() != null) {
                    VaraderoModel vc = cacheVaradero.get(c.getVaradero().getId());
                    if (vc != null && vc.getLatitud() != null && vc.getLongitud() != null) {
                        return new PuntoReferencia("VARADERO", vc.getId(), vc.getNombre(), vc.getLatitud(), vc.getLongitud(), "VARADERO_CALETA");
                    }
                }
            }
        } else if ("ARMADOR".equals(tipoNorm)) {
            if (caletaId != null) {
                CaletaModel c = cacheCaleta.get(caletaId);
                if (c != null && c.getLatitud() != null && c.getLongitud() != null) {
                    return new PuntoReferencia("CALETA", c.getId(), c.getNombre(), c.getLatitud(), c.getLongitud(), "CALETA");
                }
                if (c != null && c.getVaradero() != null && c.getVaradero().getId() != null) {
                    VaraderoModel vc = cacheVaradero.get(c.getVaradero().getId());
                    if (vc != null && vc.getLatitud() != null && vc.getLongitud() != null) {
                        return new PuntoReferencia("VARADERO", vc.getId(), vc.getNombre(), vc.getLatitud(), vc.getLongitud(), "VARADERO_CALETA");
                    }
                }
            }
        } else if ("AREA".equals(tipoNorm) || "AMERB".equals(tipoNorm)) {
            if (amerbId != null) {
                AmerbModel am = cacheAmerb.get(amerbId);
                if (am != null && am.getLatitud() != null && am.getLongitud() != null) {
                    return new PuntoReferencia("AMERB", am.getId(), am.getNombre(), am.getLatitud(), am.getLongitud(), "AMERB");
                }
            }
            if (caletaId != null) {
                CaletaModel c = cacheCaleta.get(caletaId);
                if (c != null && c.getLatitud() != null && c.getLongitud() != null) {
                    return new PuntoReferencia("CALETA", c.getId(), c.getNombre(), c.getLatitud(), c.getLongitud(), "CALETA");
                }
                if (c != null && c.getVaradero() != null && c.getVaradero().getId() != null) {
                    VaraderoModel vc = cacheVaradero.get(c.getVaradero().getId());
                    if (vc != null && vc.getLatitud() != null && vc.getLongitud() != null) {
                        return new PuntoReferencia("VARADERO", vc.getId(), vc.getNombre(), vc.getLatitud(), vc.getLongitud(), "VARADERO_CALETA");
                    }
                }
            }
        }
        return null;
    }
}
