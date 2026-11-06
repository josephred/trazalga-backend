package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionAutorizacionModel;

@Repository
public interface IDeclaracionAutorizacionRepository extends JpaRepository<DeclaracionAutorizacionModel, Long> {

    List<DeclaracionAutorizacionModel> findByDeclaracionTipoAndDeclaracionIdOrderByAutorizadoEnDesc(String declaracionTipo, Long declaracionId);

    List<DeclaracionAutorizacionModel> findByDeclaracionTipoAndDeclaracionIdAndTipoAndUsadaEnIsNull(String declaracionTipo, Long declaracionId, String tipo);
}
