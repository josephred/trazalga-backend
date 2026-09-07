package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ConfiguracionAuditoriaModel;

@Repository
public interface IConfiguracionAuditoriaRepository extends JpaRepository<ConfiguracionAuditoriaModel, Long> {

    List<ConfiguracionAuditoriaModel> findByEntidadOrderByCreatedAtDesc(String entidad);

    List<ConfiguracionAuditoriaModel> findByEntidadAndEntidadIdOrderByCreatedAtDesc(String entidad, String entidadId);

}
