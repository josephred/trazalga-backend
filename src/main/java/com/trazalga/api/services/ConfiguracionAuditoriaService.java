package com.trazalga.api.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.ConfiguracionAuditoriaModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IConfiguracionAuditoriaRepository;

@Service
public class ConfiguracionAuditoriaService {

    @Autowired
    private IConfiguracionAuditoriaRepository repository;

    public List<ConfiguracionAuditoriaModel> getAll() {
        return repository.findAll();
    }

    public List<ConfiguracionAuditoriaModel> getByEntidad(String entidad) {
        return repository.findByEntidadOrderByCreatedAtDesc(entidad);
    }

    public List<ConfiguracionAuditoriaModel> getByEntidadAndId(String entidad, String entidadId) {
        return repository.findByEntidadAndEntidadIdOrderByCreatedAtDesc(entidad, entidadId);
    }

    public ConfiguracionAuditoriaModel registrar(
            String entidad,
            String entidadId,
            String campo,
            String valorAnterior,
            String valorNuevo,
            UsuarioModel usuario) {
        ConfiguracionAuditoriaModel model = ConfiguracionAuditoriaModel.builder()
                .entidad(entidad)
                .entidadId(entidadId)
                .campo(campo)
                .valorAnterior(valorAnterior)
                .valorNuevo(valorNuevo)
                .usuario(usuario)
                .build();
        return repository.save(model);
    }
}
