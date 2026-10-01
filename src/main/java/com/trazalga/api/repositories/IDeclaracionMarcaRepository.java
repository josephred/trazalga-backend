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

    @Query("SELECT COUNT(m) > 0 FROM DeclaracionMarcaModel m WHERE " +
           "m.declaracionTipo = :tipo AND m.declaracionId = :id AND " +
           "m.marca IN :marcasBloqueantes AND " +
           "(m.resuelta = false OR (m.resuelta = true AND (m.resolucionTipo IS NULL OR m.resolucionTipo NOT IN ('LIBERADA', 'DESCARTADA'))))")
    boolean isCargaBloqueada(
            @Param("tipo") String tipo,
            @Param("id") Long id,
            @Param("marcasBloqueantes") java.util.Collection<String> marcasBloqueantes
    );

    @Query("SELECT m FROM DeclaracionMarcaModel m WHERE " +
           "m.declaracionTipo = :tipo AND m.declaracionId IN :ids AND " +
           "m.marca IN :marcasBloqueantes AND " +
           "(m.resuelta = false OR (m.resuelta = true AND (m.resolucionTipo IS NULL OR m.resolucionTipo NOT IN ('LIBERADA', 'DESCARTADA'))))")
    List<DeclaracionMarcaModel> findMarcasBloqueantesPorTipoEIds(
            @Param("tipo") String tipo,
            @Param("ids") java.util.Collection<Long> ids,
            @Param("marcasBloqueantes") java.util.Collection<String> marcasBloqueantes
    );

    @Query("SELECT m FROM DeclaracionMarcaModel m WHERE " +
           "CONCAT(m.declaracionTipo, ':', m.declaracionId) IN :tokens AND " +
           "m.marca IN :marcasBloqueantes AND " +
           "(m.resuelta = false OR (m.resuelta = true AND (m.resolucionTipo IS NULL OR m.resolucionTipo NOT IN ('LIBERADA', 'DESCARTADA'))))")
    List<DeclaracionMarcaModel> findMarcasBloqueantesPorTokens(
            @Param("tokens") java.util.Collection<String> tokens,
            @Param("marcasBloqueantes") java.util.Collection<String> marcasBloqueantes
    );
}
