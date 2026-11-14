package com.trazalga.api.services.indicadores;

import com.trazalga.api.dto.TrazabilidadEdgeDTO;
import com.trazalga.api.dto.TrazabilidadNodoDTO;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.CadenaCustodiaService;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resuelve hacia atrás la captura biológica de origen asociada a los lotes
 * de una declaración de producción en planta (QR.1).
 *
 * Recorre cada lote con CadenaCustodiaService.resolver, deduplica los orígenes
 * (recolector, armador, área) antes de sumar `captura` y marca `completa = false`
 * (devolviendo la suma parcial) cuando algún lote carece de declaraciones_seleccionadas,
 * llega a cero orígenes o presenta tokens LEGACY no atribuibles de forma unívoca.
 */
@Service
@Slf4j
public class CapturaOrigenProduccion {

    public static final String MOTIVO_SIN_LOTES = "SIN_LOTES";
    public static final String MOTIVO_LOTE_SIN_SELECCIONADAS = "LOTE_SIN_DECLARACIONES_SELECCIONADAS";
    public static final String MOTIVO_LOTE_SIN_ORIGENES = "LOTE_SIN_ORIGENES";
    public static final String MOTIVO_LEGACY_NO_ATRIBUIBLE = "LEGACY_NO_ATRIBUIBLE";
    public static final String MOTIVO_CADENA_INCOMPLETA = "CADENA_INTERMEDIA_SIN_ORIGEN";
    public static final String MOTIVO_CAPTURA_NULA = "CAPTURA_ORIGEN_NULA";
    public static final String MOTIVO_ORIGEN_NO_ENCONTRADO = "ORIGEN_NO_ENCONTRADO";

    @Autowired(required = false) private CadenaCustodiaService cadenaCustodiaService;
    @Autowired(required = false) private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired(required = false) private IDeclaracionArmadorRepository armadorRepository;
    @Autowired(required = false) private IDeclaracionAreaRepository areaRepository;
    @Autowired(required = false) private IDeclaracionComercializadorRepository comercializadorRepository;

    public CapturaOrigenProduccion() {
    }

    /** Constructor explícito para pruebas unitarias. */
    public CapturaOrigenProduccion(
            CadenaCustodiaService cadenaCustodiaService,
            IDeclaracionRecolectorRepository recolectorRepository,
            IDeclaracionArmadorRepository armadorRepository,
            IDeclaracionAreaRepository areaRepository,
            IDeclaracionComercializadorRepository comercializadorRepository) {
        this.cadenaCustodiaService = cadenaCustodiaService;
        this.recolectorRepository = recolectorRepository;
        this.armadorRepository = armadorRepository;
        this.areaRepository = areaRepository;
        this.comercializadorRepository = comercializadorRepository;
    }

    public record ResultadoCapturaOrigen(
            BigDecimal capturaOrigenKg,
            boolean completa,
            int totalOrigenes,
            List<String> clavesOrigen,
            String motivoIncompleta
    ) {
    }

    @Transactional(readOnly = true)
    public ResultadoCapturaOrigen resolverParaLotes(
            DeclaracionPlantaProduccionModel declaracion,
            List<DeclaracionPlantaAbastecimientoModel> lotes) {
        return resolverParaLotes(lotes);
    }

    @Transactional(readOnly = true)
    public ResultadoCapturaOrigen resolverParaLotes(List<DeclaracionPlantaAbastecimientoModel> lotes) {
        if (lotes == null || lotes.isEmpty()) {
            return new ResultadoCapturaOrigen(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    false,
                    0,
                    List.of(),
                    MOTIVO_SIN_LOTES
            );
        }

        boolean completa = true;
        Set<String> motivos = new LinkedHashSet<>();
        Set<String> clavesUnicas = new LinkedHashSet<>();
        Set<Long> recolectorIds = new LinkedHashSet<>();
        Set<Long> armadorIds = new LinkedHashSet<>();
        Set<Long> areaIds = new LinkedHashSet<>();

        for (DeclaracionPlantaAbastecimientoModel lote : lotes) {
            if (lote == null) {
                continue;
            }
            if (lote.getDeclaracionesSeleccionadas() == null || lote.getDeclaracionesSeleccionadas().isBlank()) {
                completa = false;
                motivos.add(MOTIVO_LOTE_SIN_SELECCIONADAS);
            } else if (tieneLegacyNoAtribuible(lote.getDeclaracionesSeleccionadas())) {
                completa = false;
                motivos.add(MOTIVO_LEGACY_NO_ATRIBUIBLE);
            }

            if (lote.getId() != null && cadenaCustodiaService != null) {
                CadenaCustodiaService.Cadena cadena = cadenaCustodiaService.resolver("PLANTA_ABASTECIMIENTO", lote.getId());
                if (cadena == null || cadena.getOrigenes() == null || cadena.getOrigenes().isEmpty()) {
                    completa = false;
                    motivos.add(MOTIVO_LOTE_SIN_ORIGENES);
                } else {
                    for (CadenaCustodiaService.OrigenRef ref : cadena.getOrigenes()) {
                        registrarOrigenRef(ref.getTipo(), ref.getId(), clavesUnicas, recolectorIds, armadorIds, areaIds);
                    }
                }

                if (cadena != null && cadena.getNodos() != null) {
                    Set<String> nodosConPadre = new HashSet<>();
                    if (cadena.getEnlaces() != null) {
                        for (TrazabilidadEdgeDTO edge : cadena.getEnlaces()) {
                            if (edge.getTarget() != null) {
                                nodosConPadre.add(edge.getTarget());
                            }
                        }
                    }
                    for (TrazabilidadNodoDTO nodo : cadena.getNodos()) {
                        String tipoNodo = normalizarTipo(nodo.getTipoNodo());
                        if ("COMERCIALIZADOR".equals(tipoNodo) || "PLANTA_ABASTECIMIENTO".equals(tipoNodo)) {
                            if (tieneLegacyNoAtribuible(nodo.getDeclaracionesSeleccionadas())) {
                                completa = false;
                                motivos.add(MOTIVO_LEGACY_NO_ATRIBUIBLE);
                            }
                            if ("COMERCIALIZADOR".equals(tipoNodo) && nodo.getIdDeclaracion() != null) {
                                String claveCom = "COMERCIALIZADOR:" + nodo.getIdDeclaracion();
                                if (!nodosConPadre.contains(claveCom)) {
                                    completa = false;
                                    motivos.add(MOTIVO_CADENA_INCOMPLETA);
                                }
                            }
                        }
                    }
                }
            } else {
                // Respaldo cuando el lote aún no está persistido o CadenaCustodiaService no aplica (p. ej. test puro)
                Set<String> comVisitados = new HashSet<>();
                int antes = clavesUnicas.size();
                if (!recorrerSeleccionadasRespaldo(
                        lote.getDeclaracionesSeleccionadas(), comVisitados,
                        clavesUnicas, recolectorIds, armadorIds, areaIds, motivos)) {
                    completa = false;
                }
                if (clavesUnicas.size() == antes) {
                    completa = false;
                    motivos.add(MOTIVO_LOTE_SIN_ORIGENES);
                }
            }
        }

        BigDecimal totalCaptura = BigDecimal.ZERO;
        int esperados = recolectorIds.size() + armadorIds.size() + areaIds.size();
        int encontrados = 0;

        if (!recolectorIds.isEmpty()) {
            if (recolectorRepository == null) {
                completa = false;
                motivos.add(MOTIVO_ORIGEN_NO_ENCONTRADO);
            } else {
                for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                    encontrados++;
                    if (r.getCaptura() != null) {
                        totalCaptura = totalCaptura.add(r.getCaptura());
                    } else {
                        completa = false;
                        motivos.add(MOTIVO_CAPTURA_NULA);
                    }
                }
            }
        }

        if (!armadorIds.isEmpty()) {
            if (armadorRepository == null) {
                completa = false;
                motivos.add(MOTIVO_ORIGEN_NO_ENCONTRADO);
            } else {
                for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                    encontrados++;
                    if (a.getCaptura() != null) {
                        totalCaptura = totalCaptura.add(BigDecimal.valueOf(a.getCaptura()));
                    } else {
                        completa = false;
                        motivos.add(MOTIVO_CAPTURA_NULA);
                    }
                }
            }
        }

        if (!areaIds.isEmpty()) {
            if (areaRepository == null) {
                completa = false;
                motivos.add(MOTIVO_ORIGEN_NO_ENCONTRADO);
            } else {
                for (DeclaracionAreaModel ar : areaRepository.findAllById(areaIds)) {
                    encontrados++;
                    if (ar.getCaptura() != null) {
                        totalCaptura = totalCaptura.add(BigDecimal.valueOf(ar.getCaptura()));
                    } else {
                        completa = false;
                        motivos.add(MOTIVO_CAPTURA_NULA);
                    }
                }
            }
        }

        if (encontrados < esperados) {
            completa = false;
            motivos.add(MOTIVO_ORIGEN_NO_ENCONTRADO);
        }

        String motivoStr = motivos.isEmpty() ? null : String.join(", ", motivos);
        return new ResultadoCapturaOrigen(
                totalCaptura.setScale(2, RoundingMode.HALF_UP),
                completa,
                clavesUnicas.size(),
                List.copyOf(clavesUnicas),
                motivoStr
        );
    }

    private boolean tieneLegacyNoAtribuible(String csv) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        Map<String, List<Long>> sel = SeleccionTokens.parse(csv);
        List<Long> legacyIds = sel.getOrDefault(SeleccionTokens.LEGACY, Collections.emptyList());
        if (legacyIds.isEmpty()) {
            return false;
        }
        for (Long id : legacyIds) {
            if (contarCoincidenciasLegacy(id) != 1) {
                return true;
            }
        }
        return false;
    }

    private int contarCoincidenciasLegacy(Long id) {
        if (id == null) {
            return 0;
        }
        int matches = 0;
        if (recolectorRepository != null && recolectorRepository.existsById(id)) matches++;
        if (armadorRepository != null && armadorRepository.existsById(id)) matches++;
        if (areaRepository != null && areaRepository.existsById(id)) matches++;
        if (comercializadorRepository != null && comercializadorRepository.existsById(id)) matches++;
        return matches;
    }

    private boolean recorrerSeleccionadasRespaldo(
            String csv,
            Set<String> comVisitados,
            Set<String> clavesUnicas,
            Set<Long> recolectorIds,
            Set<Long> armadorIds,
            Set<Long> areaIds,
            Set<String> motivos) {
        if (csv == null || csv.isBlank()) {
            return false;
        }
        boolean ok = true;
        Map<String, List<Long>> sel = SeleccionTokens.parse(csv);
        for (Long id : SeleccionTokens.idsParaTipo(sel, "RECOLECTOR")) {
            registrarOrigenRef("RECOLECTOR", id, clavesUnicas, recolectorIds, armadorIds, areaIds);
        }
        for (Long id : SeleccionTokens.idsParaTipo(sel, "ARMADOR")) {
            registrarOrigenRef("ARMADOR", id, clavesUnicas, recolectorIds, armadorIds, areaIds);
        }
        for (Long id : SeleccionTokens.idsParaTipo(sel, "AREA")) {
            registrarOrigenRef("AREA", id, clavesUnicas, recolectorIds, armadorIds, areaIds);
        }
        if (tieneLegacyNoAtribuible(csv)) {
            ok = false;
            motivos.add(MOTIVO_LEGACY_NO_ATRIBUIBLE);
        }
        List<Long> comIds = SeleccionTokens.idsParaTipo(sel, "COMERCIALIZADOR");
        if (!comIds.isEmpty()) {
            if (comercializadorRepository == null) {
                ok = false;
                motivos.add(MOTIVO_CADENA_INCOMPLETA);
            } else {
                List<DeclaracionComercializadorModel> coms = comercializadorRepository.findAllById(comIds);
                if (coms.size() < comIds.size()) {
                    ok = false;
                    motivos.add(MOTIVO_CADENA_INCOMPLETA);
                }
                for (DeclaracionComercializadorModel c : coms) {
                    if (comVisitados.add("COMERCIALIZADOR:" + c.getId())) {
                        if (!recorrerSeleccionadasRespaldo(
                                c.getDeclaracionesSeleccionadas(), comVisitados,
                                clavesUnicas, recolectorIds, armadorIds, areaIds, motivos)) {
                            ok = false;
                        }
                    }
                }
            }
        }
        return ok;
    }

    private static void registrarOrigenRef(
            String tipo,
            Long id,
            Set<String> clavesUnicas,
            Set<Long> recolectorIds,
            Set<Long> armadorIds,
            Set<Long> areaIds) {
        if (id == null) return;
        String t = normalizarTipo(tipo);
        switch (t) {
            case "RECOLECTOR":
                clavesUnicas.add("RECOLECTOR:" + id);
                recolectorIds.add(id);
                break;
            case "ARMADOR":
                clavesUnicas.add("ARMADOR:" + id);
                armadorIds.add(id);
                break;
            case "AREA":
                clavesUnicas.add("AREA:" + id);
                areaIds.add(id);
                break;
            default:
                break;
        }
    }

    private static String normalizarTipo(String tipo) {
        if (tipo == null) return "";
        String t = tipo.trim().toUpperCase();
        if (t.startsWith("REC")) return "RECOLECTOR";
        if (t.startsWith("ARM")) return "ARMADOR";
        if (t.startsWith("ARE") || t.equals("AMERB")) return "AREA";
        if (t.startsWith("COM")) return "COMERCIALIZADOR";
        if (t.startsWith("PLANTA_ABAS") || t.equals("A-PLA") || t.equals("A_PLA")) return "PLANTA_ABASTECIMIENTO";
        return t;
    }
}
