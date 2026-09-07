package com.trazalga.api.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.ILimiteExtraccionDiarioConfigRepository;
import com.trazalga.api.repositories.IRegionRepository;

@Service
public class LimiteExtraccionDiarioConfigService {

    @Autowired
    private ILimiteExtraccionDiarioConfigRepository repository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired
    private IRegionRepository regionRepository;

    public List<LimiteExtraccionDiarioConfigModel> getAll() {
        return repository.findAll();
    }

    public List<LimiteExtraccionDiarioConfigModel> getActivos() {
        return repository.findByActivoTrue();
    }

    public Optional<LimiteExtraccionDiarioConfigModel> getById(Long id) {
        return repository.findById(id);
    }

    public List<LimiteExtraccionDiarioConfigModel> findReglasVigentes(String perfil, Date fecha) {
        return repository.findReglasVigentes(perfil, fecha != null ? fecha : new Date());
    }

    public LimiteExtraccionDiarioConfigModel save(LimiteExtraccionDiarioConfigModel model) {
        resolverReferencias(model);
        if (model.getLimiteKg() == null || model.getLimiteKg().doubleValue() <= 0) {
            throw new IllegalArgumentException("El límite en kg debe ser mayor a 0.");
        }
        if (model.getPerfilAplicable() == null || model.getPerfilAplicable().isBlank()) {
            model.setPerfilAplicable("ARMADOR");
        }
        if (model.getUnidadAgregacion() == null || model.getUnidadAgregacion().isBlank()) {
            model.setUnidadAgregacion("EMBARCACION");
        }
        if (model.getMetrica() == null || model.getMetrica().isBlank()) {
            model.setMetrica("DESEMBARQUE");
        }
        if (model.getModoAccion() == null || model.getModoAccion().isBlank()) {
            model.setModoAccion("SOLO_ALERTA");
        }
        return repository.save(model);
    }

    public LimiteExtraccionDiarioConfigModel update(Long id, LimiteExtraccionDiarioConfigModel request) {
        LimiteExtraccionDiarioConfigModel existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla LED no encontrada con ID: " + id));

        resolverReferencias(request);
        if (request.getNombreRegla() != null) existing.setNombreRegla(request.getNombreRegla());
        existing.setEspecie(request.getEspecie());
        existing.setExtraccionTipo(request.getExtraccionTipo());
        existing.setRegion(request.getRegion());
        if (request.getPerfilAplicable() != null) existing.setPerfilAplicable(request.getPerfilAplicable());
        if (request.getUnidadAgregacion() != null) existing.setUnidadAgregacion(request.getUnidadAgregacion());
        if (request.getMetrica() != null) existing.setMetrica(request.getMetrica());
        if (request.getLimiteKg() != null) {
            if (request.getLimiteKg().doubleValue() <= 0) {
                throw new IllegalArgumentException("El límite en kg debe ser mayor a 0.");
            }
            existing.setLimiteKg(request.getLimiteKg());
        }
        if (request.getMargenToleranciaPct() != null) existing.setMargenToleranciaPct(request.getMargenToleranciaPct());
        if (request.getModoAccion() != null) existing.setModoAccion(request.getModoAccion());
        existing.setVigenciaInicio(request.getVigenciaInicio());
        existing.setVigenciaFin(request.getVigenciaFin());
        if (request.getActivo() != null) existing.setActivo(request.getActivo());

        return repository.save(existing);
    }

    public boolean delete(Long id) {
        try {
            repository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resolverReferencias(LimiteExtraccionDiarioConfigModel model) {
        if (model.getEspecie() != null && model.getEspecie().getId() != null) {
            model.setEspecie(especieRepository.findById(model.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada con ID: " + model.getEspecie().getId())));
        }
        if (model.getExtraccionTipo() != null && model.getExtraccionTipo().getId() != null) {
            model.setExtraccionTipo(extraccionTipoRepository.findById(model.getExtraccionTipo().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Método de extracción no encontrado con ID: " + model.getExtraccionTipo().getId())));
        }
        if (model.getRegion() != null && model.getRegion().getId() != null) {
            model.setRegion(regionRepository.findById(model.getRegion().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Región no encontrada con ID: " + model.getRegion().getId())));
        }
    }
}
