package com.trazalga.api.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;

@Service
public class DeclaracionMarcaService {

    @Autowired
    private IDeclaracionMarcaRepository repository;

    public List<DeclaracionMarcaModel> getAll() {
        return repository.findAll();
    }

    public List<DeclaracionMarcaModel> getByDeclaracion(String tipo, Long declaracionId) {
        return repository.findByDeclaracionTipoAndDeclaracionId(tipo, declaracionId);
    }

    public List<DeclaracionMarcaModel> getByMarca(String marca) {
        return repository.findByMarca(marca);
    }

    public List<DeclaracionMarcaModel> getPendientes() {
        return repository.findByResueltaFalse();
    }

    public DeclaracionMarcaModel marcar(String declaracionTipo, Long declaracionId, String marca, String detalle, Long reglaId) {
        DeclaracionMarcaModel model = DeclaracionMarcaModel.builder()
                .declaracionTipo(declaracionTipo)
                .declaracionId(declaracionId)
                .marca(marca)
                .detalle(detalle)
                .reglaId(reglaId)
                .resuelta(false)
                .build();
        return repository.save(model);
    }

    public Optional<DeclaracionMarcaModel> resolverMarca(Long id) {
        return repository.findById(id).map(m -> {
            m.setResuelta(true);
            return repository.save(m);
        });
    }
}
