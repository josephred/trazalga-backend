package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionFechaAjusteLogModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionFechaAjusteLogRepository extends JpaRepository<DeclaracionFechaAjusteLogModel, Long> {
    List<DeclaracionFechaAjusteLogModel> findByDeclaracionTipoAndDeclaracionId(String declaracionTipo, Long declaracionId);
}
