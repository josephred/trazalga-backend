package com.trazalga.api.repositories;

import com.trazalga.api.models.CoordenadaCambioLogModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ICoordenadaCambioLogRepository extends JpaRepository<CoordenadaCambioLogModel, Long> {
    List<CoordenadaCambioLogModel> findByEntidadTipoAndEntidadIdOrderByActualizadoEnDesc(String entidadTipo, Long entidadId);
}
