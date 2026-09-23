package com.trazalga.api.repositories;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionMarcaModel;

@Repository
public interface IDeclaracionMarcaRepository extends JpaRepository<DeclaracionMarcaModel, Long> {

    List<DeclaracionMarcaModel> findByDeclaracionTipoAndDeclaracionId(String declaracionTipo, Long declaracionId);

    List<DeclaracionMarcaModel> findByMarca(String marca);

    List<DeclaracionMarcaModel> findByDeclaracionTipoAndDeclaracionIdAndMarca(String declaracionTipo, Long declaracionId, String marca);

    List<DeclaracionMarcaModel> findByResueltaFalse();

    @Query("SELECT m FROM DeclaracionMarcaModel m WHERE " +
           "(:marca IS NULL OR :marca = '' OR m.marca = :marca) AND " +
           "(:resuelta IS NULL OR m.resuelta = :resuelta) AND " +
           "(:declaracionTipo IS NULL OR :declaracionTipo = '' OR m.declaracionTipo = :declaracionTipo) AND " +
           "(:startDate IS NULL OR m.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR m.createdAt <= :endDate) " +
           "ORDER BY m.createdAt DESC")
    List<DeclaracionMarcaModel> findConFiltros(
            @Param("marca") String marca,
            @Param("resuelta") Boolean resuelta,
            @Param("declaracionTipo") String declaracionTipo,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate
    );
}
