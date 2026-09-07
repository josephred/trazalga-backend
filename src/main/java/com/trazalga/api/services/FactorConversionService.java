package com.trazalga.api.services;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;

@Service
public class FactorConversionService {

    @Autowired
    private IFactorConversionRepository repository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;

    public List<FactorConversionModel> getAll() {
        return repository.findAll();
    }

    public List<FactorConversionModel> getActivos() {
        return repository.findByActivoTrue();
    }

    public Optional<FactorConversionModel> getById(Long id) {
        return repository.findById(id);
    }

    public Optional<FactorConversionModel> findFactorVigente(Long especieId, Long humedadEstadoId, Date fecha) {
        return repository.findFactorVigente(especieId, humedadEstadoId, fecha != null ? fecha : new Date());
    }

    public FactorConversionModel save(FactorConversionModel model) {
        resolverReferencias(model);
        if (model.getFactor() == null || model.getFactor().doubleValue() <= 0) {
            throw new IllegalArgumentException("El factor de conversión debe ser un valor mayor a 0.");
        }
        if (model.getVigenciaInicio() == null) {
            model.setVigenciaInicio(new Date());
        }
        return repository.save(model);
    }

    public FactorConversionModel update(Long id, FactorConversionModel request) {
        FactorConversionModel existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factor de conversión no encontrado con ID: " + id));

        resolverReferencias(request);
        if (request.getEspecie() != null) existing.setEspecie(request.getEspecie());
        if (request.getHumedadEstado() != null) existing.setHumedadEstado(request.getHumedadEstado());
        if (request.getFactor() != null) {
            if (request.getFactor().doubleValue() <= 0) {
                throw new IllegalArgumentException("El factor debe ser mayor a 0.");
            }
            existing.setFactor(request.getFactor());
        }
        if (request.getVigenciaInicio() != null) existing.setVigenciaInicio(request.getVigenciaInicio());
        existing.setVigenciaFin(request.getVigenciaFin());
        if (request.getResolucion() != null) existing.setResolucion(request.getResolucion());
        if (request.getDescripcion() != null) existing.setDescripcion(request.getDescripcion());
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

    private void resolverReferencias(FactorConversionModel model) {
        if (model.getEspecie() != null && model.getEspecie().getId() != null) {
            model.setEspecie(especieRepository.findById(model.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada con ID: " + model.getEspecie().getId())));
        }
        if (model.getHumedadEstado() != null && model.getHumedadEstado().getId() != null) {
            model.setHumedadEstado(humedadEstadoRepository.findById(model.getHumedadEstado().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Estado de humedad no encontrado con ID: " + model.getHumedadEstado().getId())));
        }
    }
}
