package com.trazalga.api.repositories;

import com.trazalga.api.models.ConfiguracionGeneralModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ConfiguracionGeneralRepository extends JpaRepository<ConfiguracionGeneralModel, Long> {
    Optional<ConfiguracionGeneralModel> findByClave(String clave);
}
