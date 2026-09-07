package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionMarcaModel;

@Repository
public interface IDeclaracionMarcaRepository extends JpaRepository<DeclaracionMarcaModel, Long> {

    List<DeclaracionMarcaModel> findByDeclaracionTipoAndDeclaracionId(String declaracionTipo, Long declaracionId);

    List<DeclaracionMarcaModel> findByMarca(String marca);

    List<DeclaracionMarcaModel> findByDeclaracionTipoAndDeclaracionIdAndMarca(String declaracionTipo, Long declaracionId, String marca);

    List<DeclaracionMarcaModel> findByResueltaFalse();

}
