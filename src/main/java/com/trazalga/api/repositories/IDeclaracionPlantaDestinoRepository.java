package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionPlantaDestinoRepository extends JpaRepository<DeclaracionPlantaDestinoModel, Long> {

    List<DeclaracionPlantaDestinoModel> findAllByUsuarioIdOrderByFechaDeclaracionDestinoDesc(Long usuarioId);

    @Query("SELECT d.folioDeclaracionDestino FROM DeclaracionPlantaDestinoModel d ORDER BY d.id DESC")
    List<String> findLastFolioDeclaracionDestino();

    String findTopByOrderByIdDescFolioDeclaracionAbastecimientoPlanta();

    List<DeclaracionPlantaDestinoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(Long usuarioDestinatarioId);

    List<DeclaracionPlantaDestinoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioId(Long usuarioDestinatarioId, Long declaracionDestinatarioId);
}