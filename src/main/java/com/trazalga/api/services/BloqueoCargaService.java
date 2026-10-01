package com.trazalga.api.services;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio centralizado para la gestión del bloqueo de carga en bodega virtual.
 * Implementa las directivas del Punto 4 (T4.1 - T4.3) del refinamiento de septiembre 2026.
 */
@Slf4j
@Service
public class BloqueoCargaService {

    @Autowired
    private IDeclaracionMarcaRepository marcaRepository;

    @Autowired
    private ConfiguracionGeneralService configService;

    public boolean isBloqueoActivo() {
        return configService.getBoolean("bloqueo_carga_activo", true);
    }

    public Set<String> getMarcasBloqueantes() {
        String raw = configService.getValor("marcas_bloqueantes_carga", "LED_EXCEDIDO");
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Verifica si una carga específica está actualmente bloqueada en bodega.
     */
    public boolean estaBloqueada(String tipo, Long id) {
        if (!isBloqueoActivo() || tipo == null || id == null) {
            return false;
        }
        Set<String> marcas = getMarcasBloqueantes();
        if (marcas.isEmpty()) {
            return false;
        }
        return marcaRepository.isCargaBloqueada(tipo.toUpperCase().trim(), id, marcas);
    }

    /**
     * Evalúa un CSV de tokens de declaraciones seleccionadas para despacho
     * y retorna el mapa de aquellas que se encuentran bloqueadas con su motivo.
     */
    public Map<String, MotivoBloqueoDTO> bloqueosPara(String csvTokens) {
        if (!isBloqueoActivo() || csvTokens == null || csvTokens.isBlank()) {
            return Collections.emptyMap();
        }
        Set<String> marcasBloqueantes = getMarcasBloqueantes();
        if (marcasBloqueantes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<Long>> parsed = SeleccionTokens.parse(csvTokens);
        Map<String, MotivoBloqueoDTO> bloqueadas = new HashMap<>();

        for (Map.Entry<String, List<Long>> entry : parsed.entrySet()) {
            String tipo = entry.getKey();
            List<Long> ids = entry.getValue();
            if (ids == null || ids.isEmpty()) continue;

            // Si el token es LEGACY (sin prefijo), evaluar contra los orígenes posibles
            List<String> tiposAEvaluar = SeleccionTokens.LEGACY.equals(tipo)
                    ? List.of("ARMADOR", "RECOLECTOR", "AREA", "COMERCIALIZADOR")
                    : List.of(tipo.toUpperCase());

            for (String t : tiposAEvaluar) {
                List<DeclaracionMarcaModel> marcas = marcaRepository.findMarcasBloqueantesPorTipoEIds(t, ids, marcasBloqueantes);
                for (DeclaracionMarcaModel m : marcas) {
                    String key = SeleccionTokens.LEGACY.equals(tipo) ? String.valueOf(m.getDeclaracionId()) : (t + ":" + m.getDeclaracionId());
                    bloqueadas.put(key, toDTO(m));
                }
            }
        }

        return bloqueadas;
    }

    /**
     * Consulta las marcas bloqueantes para un conjunto de IDs de un tipo específico.
     */
    public Map<Long, MotivoBloqueoDTO> bloqueosPorTipoEIds(String tipo, Collection<Long> ids) {
        if (!isBloqueoActivo() || tipo == null || ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> marcasBloqueantes = getMarcasBloqueantes();
        if (marcasBloqueantes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DeclaracionMarcaModel> marcas = marcaRepository.findMarcasBloqueantesPorTipoEIds(
                tipo.toUpperCase().trim(), ids, marcasBloqueantes);

        Map<Long, MotivoBloqueoDTO> map = new HashMap<>();
        for (DeclaracionMarcaModel m : marcas) {
            map.put(m.getDeclaracionId(), toDTO(m));
        }
        return map;
    }

    private MotivoBloqueoDTO toDTO(DeclaracionMarcaModel m) {
        return MotivoBloqueoDTO.builder()
                .marca(m.getMarca())
                .detalle(m.getDetalle())
                .fechaDeteccion(m.getCreatedAt())
                .marcaId(m.getId())
                .resolucionTipo(m.getResolucionTipo())
                .observacionResolucion(m.getObservacionResolucion())
                .estadoGestion(m.getEstadoGestion())
                .build();
    }

    private String formatMotivo(MotivoBloqueoDTO b) {
        if (b == null) return null;
        String res = "Retenida por " + b.getMarca() + " (hallazgo #" + b.getMarcaId() + ")";
        if (b.getDetalle() != null && !b.getDetalle().isBlank()) {
            res += ": " + b.getDetalle();
        }
        if (b.getResolucionTipo() != null && !"LIBERADA".equalsIgnoreCase(b.getResolucionTipo())) {
            res += " [Resolución: " + b.getResolucionTipo() + "]";
        }
        return res;
    }

    // --- Métodos de enriquecimiento para listados de bodega virtual ---

    public void enriquecerArmadores(List<DeclaracionArmadorModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(DeclaracionArmadorModel::getId).filter(Objects::nonNull).toList();
        Map<Long, MotivoBloqueoDTO> bloqueos = bloqueosPorTipoEIds("ARMADOR", ids);
        for (DeclaracionArmadorModel item : list) {
            if (bloqueos.containsKey(item.getId())) {
                item.setBloqueada(true);
                item.setMotivoBloqueo(formatMotivo(bloqueos.get(item.getId())));
            } else {
                item.setBloqueada(false);
                item.setMotivoBloqueo(null);
            }
        }
    }

    public void enriquecerRecolectores(List<DeclaracionRecolectorModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(DeclaracionRecolectorModel::getId).filter(Objects::nonNull).toList();
        Map<Long, MotivoBloqueoDTO> bloqueos = bloqueosPorTipoEIds("RECOLECTOR", ids);
        for (DeclaracionRecolectorModel item : list) {
            if (bloqueos.containsKey(item.getId())) {
                item.setBloqueada(true);
                item.setMotivoBloqueo(formatMotivo(bloqueos.get(item.getId())));
            } else {
                item.setBloqueada(false);
                item.setMotivoBloqueo(null);
            }
        }
    }

    public void enriquecerAreas(List<DeclaracionAreaModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(DeclaracionAreaModel::getId).filter(Objects::nonNull).toList();
        Map<Long, MotivoBloqueoDTO> bloqueos = bloqueosPorTipoEIds("AREA", ids);
        for (DeclaracionAreaModel item : list) {
            if (bloqueos.containsKey(item.getId())) {
                item.setBloqueada(true);
                item.setMotivoBloqueo(formatMotivo(bloqueos.get(item.getId())));
            } else {
                item.setBloqueada(false);
                item.setMotivoBloqueo(null);
            }
        }
    }

    public void enriquecerComercializadores(List<DeclaracionComercializadorModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(DeclaracionComercializadorModel::getId).filter(Objects::nonNull).toList();
        Map<Long, MotivoBloqueoDTO> bloqueos = bloqueosPorTipoEIds("COMERCIALIZADOR", ids);
        for (DeclaracionComercializadorModel item : list) {
            if (bloqueos.containsKey(item.getId())) {
                item.setBloqueada(true);
                item.setMotivoBloqueo(formatMotivo(bloqueos.get(item.getId())));
            } else {
                item.setBloqueada(false);
                item.setMotivoBloqueo(null);
            }
        }
    }

    public void enriquecerPlantas(List<DeclaracionPlantaAbastecimientoModel> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(DeclaracionPlantaAbastecimientoModel::getId).filter(Objects::nonNull).toList();
        Map<Long, MotivoBloqueoDTO> bloqueos = bloqueosPorTipoEIds("PLANTA_ABASTECIMIENTO", ids);
        for (DeclaracionPlantaAbastecimientoModel item : list) {
            if (bloqueos.containsKey(item.getId())) {
                item.setBloqueada(true);
                item.setMotivoBloqueo(formatMotivo(bloqueos.get(item.getId())));
            } else {
                item.setBloqueada(false);
                item.setMotivoBloqueo(null);
            }
        }
    }
}
