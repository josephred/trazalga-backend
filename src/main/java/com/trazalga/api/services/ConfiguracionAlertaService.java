package com.trazalga.api.services;

import com.trazalga.api.models.ConfiguracionAlertaModel;
import com.trazalga.api.repositories.ConfiguracionAlertaRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ConfiguracionAlertaService {

    @Autowired
    private ConfiguracionAlertaRepository repository;

    @PostConstruct
    public void seedDefaults() {
        if (repository.count() == 0) {
            repository.save(ConfiguracionAlertaModel.builder()
                    .tipoAlerta("EXTRACCION_VEDA")
                    .titulo("Alerta de Extracción en Veda")
                    .umbral(null)
                    .activo(true)
                    .build());

            repository.save(ConfiguracionAlertaModel.builder()
                    .tipoAlerta("LIMITE_CUOTA")
                    .titulo("Alerta por Aproximación a Límite de Cuota")
                    .umbral(80.0) // 80% default
                    .activo(true)
                    .build());
        }
    }

    public List<ConfiguracionAlertaModel> getAll() {
        return repository.findAll();
    }

    public Optional<ConfiguracionAlertaModel> getByTipo(String tipo) {
        return repository.findByTipoAlerta(tipo);
    }

    public ConfiguracionAlertaModel updateConfig(Long id, ConfiguracionAlertaModel request) {
        return repository.findById(id).map(config -> {
            config.setUmbral(request.getUmbral());
            config.setActivo(request.getActivo());
            return repository.save(config);
        }).orElseThrow(() -> new RuntimeException("Configuracion no encontrada"));
    }
}
