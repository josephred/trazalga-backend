package com.trazalga.api.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.AmerbEspecieHabilitadaModel;
import com.trazalga.api.repositories.IAmerbEspecieHabilitadaRepository;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IEspecieRepository;

@Service
public class AmerbEspecieHabilitadaService {

    @Autowired
    private IAmerbEspecieHabilitadaRepository repository;

    @Autowired
    private IAmerbRepository amerbRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    public List<AmerbEspecieHabilitadaModel> getAll() {
        return repository.findAll();
    }

    public List<AmerbEspecieHabilitadaModel> getByAmerb(Long amerbId) {
        return repository.findByAmerbIdAndActivoTrue(amerbId);
    }

    public boolean isEspecieHabilitada(Long amerbId, Long especieId) {
        // Si no hay especies configuradas para esa AMERB, no restringe (regla NULL = aplica a todos)
        List<AmerbEspecieHabilitadaModel> habilitadas = repository.findByAmerbIdAndActivoTrue(amerbId);
        if (habilitadas.isEmpty()) {
            return true;
        }
        return habilitadas.stream().anyMatch(h -> h.getEspecie().getId().equals(especieId));
    }

    public AmerbEspecieHabilitadaModel save(AmerbEspecieHabilitadaModel model) {
        if (model.getAmerb() != null && model.getAmerb().getId() != null) {
            model.setAmerb(amerbRepository.findById(model.getAmerb().getId())
                    .orElseThrow(() -> new IllegalArgumentException("AMERB no encontrada con ID: " + model.getAmerb().getId())));
        }
        if (model.getEspecie() != null && model.getEspecie().getId() != null) {
            model.setEspecie(especieRepository.findById(model.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada con ID: " + model.getEspecie().getId())));
        }
        return repository.save(model);
    }

    public boolean delete(Long id) {
        try {
            repository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
