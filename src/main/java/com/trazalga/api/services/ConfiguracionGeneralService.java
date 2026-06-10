package com.trazalga.api.services;

import com.trazalga.api.models.ConfiguracionGeneralModel;
import com.trazalga.api.repositories.ConfiguracionGeneralRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ConfiguracionGeneralService {

    @Autowired
    private ConfiguracionGeneralRepository repository;

    @PostConstruct
    public void initDefaults() {
        createIfNotExist("intervalo_rastreo_minutos", "5", "Intervalo en minutos para el registro de ubicación del usuario en la app móvil");
        createIfNotExist("rastreo_activo", "true", "Habilita o deshabilita globalmente el rastreo de ubicación en la aplicación móvil");
    }

    private void createIfNotExist(String clave, String valorDefecto, String descripcion) {
        if (repository.findByClave(clave).isEmpty()) {
            repository.save(ConfiguracionGeneralModel.builder()
                    .clave(clave)
                    .valor(valorDefecto)
                    .descripcion(descripcion)
                    .build());
        }
    }

    public List<ConfiguracionGeneralModel> getAll() {
        return repository.findAll();
    }

    public Optional<ConfiguracionGeneralModel> getByClave(String clave) {
        return repository.findByClave(clave);
    }

    public ConfiguracionGeneralModel updateConfig(String clave, String nuevoValor) {
        ConfiguracionGeneralModel config = repository.findByClave(clave)
                .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada: " + clave));
        config.setValor(nuevoValor);
        return repository.save(config);
    }
}
