package com.trazalga.api.repositories;

import com.trazalga.api.models.ConfiguracionAlertaModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionAlertaRepository extends JpaRepository<ConfiguracionAlertaModel, Long> {
    Optional<ConfiguracionAlertaModel> findByTipoAlerta(String tipoAlerta);
}
