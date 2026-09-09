package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.MacrozonaDTO;
import com.trazalga.api.dto.MacrozonaRegionDTO;
import com.trazalga.api.models.MacrozonaModel;
import com.trazalga.api.models.MacrozonaRegionModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IMacrozonaRegionRepository;
import com.trazalga.api.repositories.IMacrozonaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

@Service
public class MacrozonaService {

    @Autowired
    private IMacrozonaRepository macrozonaRepository;

    @Autowired
    private IMacrozonaRegionRepository macrozonaRegionRepository;

    @Autowired
    private IRegionRepository regionRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private ConfiguracionAuditoriaService auditoriaService;

    // Cache local en memoria para resolución ultrarrápida en motor de cuotas
    private final Map<String, Set<Long>> cacheMacrozonaRegiones = new ConcurrentHashMap<>();
    private final Map<Long, List<MacrozonaModel>> cacheRegionMacrozonas = new ConcurrentHashMap<>();

    public void clearCache() {
        cacheMacrozonaRegiones.clear();
        cacheRegionMacrozonas.clear();
    }

    @Transactional(readOnly = true)
    public List<MacrozonaDTO> getAll() {
        List<MacrozonaModel> list = macrozonaRepository.findAll();
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MacrozonaModel> getActivas() {
        return macrozonaRepository.findByActivoTrue();
    }

    @Transactional(readOnly = true)
    public Optional<MacrozonaDTO> getById(Long id) {
        return macrozonaRepository.findById(id).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Optional<MacrozonaModel> getModelById(Long id) {
        return macrozonaRepository.findById(id);
    }

    @Transactional
    public MacrozonaDTO save(MacrozonaDTO dto, Long usuarioId) {
        if (dto.getNombre() == null || dto.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la macrozona es obligatorio.");
        }

        String nombreTrim = dto.getNombre().trim();
        if (dto.getId() == null) {
            if (macrozonaRepository.existsByNombreIgnoreCase(nombreTrim)) {
                throw new IllegalArgumentException("Ya existe una macrozona con el nombre «" + nombreTrim + "».");
            }
        } else {
            if (macrozonaRepository.existsByNombreIgnoreCaseAndIdNot(nombreTrim, dto.getId())) {
                throw new IllegalArgumentException("Ya existe otra macrozona con el nombre «" + nombreTrim + "».");
            }
        }

        MacrozonaModel model;
        boolean esNuevo = (dto.getId() == null);
        if (esNuevo) {
            model = new MacrozonaModel();
        } else {
            model = macrozonaRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Macrozona no encontrada id=" + dto.getId()));
        }

        model.setNombre(nombreTrim);
        model.setCodigo(dto.getCodigo() != null ? dto.getCodigo().trim() : null);
        model.setDescripcion(dto.getDescripcion() != null ? dto.getDescripcion().trim() : null);
        model.setEsNacional(Boolean.TRUE.equals(dto.getEsNacional()));
        model.setActivo(dto.getActivo() == null || Boolean.TRUE.equals(dto.getActivo()));

        MacrozonaModel saved = macrozonaRepository.save(model);

        // Si es nacional, por definición cubre todas las regiones
        List<Long> targetRegionIds = new ArrayList<>();
        if (Boolean.TRUE.equals(saved.getEsNacional())) {
            targetRegionIds = regionRepository.findAll().stream()
                    .map(RegionModel::getId)
                    .collect(Collectors.toList());
        } else if (dto.getRegionIds() != null) {
            targetRegionIds = new ArrayList<>(dto.getRegionIds());
        }

        // Sincronizar asociaciones con regiones (preservando vigencia para inmutabilidad histórica)
        sincronizarRegiones(saved, targetRegionIds);

        clearCache();

        if (auditoriaService != null && usuarioId != null) {
            UsuarioModel usuario = usuarioRepository.findById(usuarioId).orElse(null);
            auditoriaService.registrar("MACROZONA", String.valueOf(saved.getId()),
                    esNuevo ? "CREAR" : "ACTUALIZAR",
                    null, saved.getNombre(), usuario);
        }

        return toDTO(saved);
    }

    @Transactional
    public boolean delete(Long id, Long usuarioId) {
        Optional<MacrozonaModel> opt = macrozonaRepository.findById(id);
        if (opt.isEmpty()) return false;

        MacrozonaModel model = opt.get();
        model.setActivo(false);
        macrozonaRepository.save(model);
        clearCache();

        if (auditoriaService != null && usuarioId != null) {
            UsuarioModel usuario = usuarioRepository.findById(usuarioId).orElse(null);
            auditoriaService.registrar("MACROZONA", String.valueOf(id),
                    "DESACTIVAR", "activo=true", "activo=false", usuario);
        }
        return true;
    }

    private void sincronizarRegiones(MacrozonaModel macrozona, List<Long> nuevosRegionIds) {
        List<MacrozonaRegionModel> actuales = macrozonaRegionRepository.findByMacrozonaId(macrozona.getId());
        Set<Long> nuevosSet = new HashSet<>(nuevosRegionIds != null ? nuevosRegionIds : List.of());
        Date ahora = new Date();

        // 1. Cerrar vigencia de regiones removidas
        for (MacrozonaRegionModel mr : actuales) {
            Long rId = mr.getRegion().getId();
            if (!nuevosSet.contains(rId)) {
                if (mr.getVigenciaFin() == null) {
                    mr.setVigenciaFin(ahora);
                    macrozonaRegionRepository.save(mr);
                }
            }
        }

        // 2. Agregar regiones nuevas o reactivar cerradas
        for (Long rId : nuevosSet) {
            boolean yaEstaActiva = actuales.stream().anyMatch(mr ->
                    mr.getRegion().getId().equals(rId) &&
                    (mr.getVigenciaFin() == null || mr.getVigenciaFin().after(ahora)));

            if (!yaEstaActiva) {
                RegionModel reg = regionRepository.findById(rId).orElse(null);
                if (reg != null) {
                    MacrozonaRegionModel nuevo = MacrozonaRegionModel.builder()
                            .macrozona(macrozona)
                            .region(reg)
                            .vigenciaInicio(ahora)
                            .vigenciaFin(null)
                            .build();
                    macrozonaRegionRepository.save(nuevo);
                }
            }
        }
    }

    // =========================================================================
    // RESOLUCIÓN TERRITORIAL PARA EL MOTOR DE CUOTAS
    // =========================================================================

    public Set<Long> getRegionIdsForMacrozona(Long macrozonaId, Date fecha) {
        if (macrozonaId == null) return Set.of();
        Date fechaEval = (fecha != null) ? fecha : new Date();
        List<Long> ids = macrozonaRegionRepository.findRegionIdsByMacrozonaIdAndFecha(macrozonaId, fechaEval);
        return new HashSet<>(ids);
    }

    public List<MacrozonaModel> getMacrozonasForRegion(Long regionId, Date fecha) {
        if (regionId == null) return List.of();
        Date fechaEval = (fecha != null) ? fecha : new Date();
        return macrozonaRegionRepository.findMacrozonasActivasByRegionId(regionId, fechaEval);
    }

    public boolean isRegionInMacrozona(Long macrozonaId, Long regionId, Date fecha) {
        if (macrozonaId == null || regionId == null) return false;
        Date fechaEval = (fecha != null) ? fecha : new Date();
        return macrozonaRegionRepository.existsByMacrozonaIdAndRegionIdAndFecha(macrozonaId, regionId, fechaEval);
    }

    public MacrozonaDTO toDTO(MacrozonaModel model) {
        if (model == null) return null;
        List<MacrozonaRegionModel> listReg = macrozonaRegionRepository.findByMacrozonaId(model.getId());

        List<MacrozonaRegionDTO> regDTOs = listReg.stream().map(mr -> MacrozonaRegionDTO.builder()
                .id(mr.getId())
                .macrozonaId(model.getId())
                .regionId(mr.getRegion() != null ? mr.getRegion().getId() : null)
                .regionNombre(mr.getRegion() != null ? mr.getRegion().getNombre() : null)
                .regionCodigo(mr.getRegion() != null ? mr.getRegion().getCodigo() : null)
                .vigenciaInicio(mr.getVigenciaInicio())
                .vigenciaFin(mr.getVigenciaFin())
                .build()).collect(Collectors.toList());

        // Regiones actualmente vigentes
        Date ahora = new Date();
        List<Long> activeRegionIds = listReg.stream()
                .filter(mr -> mr.getVigenciaFin() == null || mr.getVigenciaFin().after(ahora))
                .map(mr -> mr.getRegion() != null ? mr.getRegion().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return MacrozonaDTO.builder()
                .id(model.getId())
                .nombre(model.getNombre())
                .codigo(model.getCodigo())
                .descripcion(model.getDescripcion())
                .esNacional(model.getEsNacional())
                .activo(model.getActivo())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .regiones(regDTOs)
                .regionIds(activeRegionIds)
                .build();
    }

}
