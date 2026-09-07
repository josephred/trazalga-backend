package com.trazalga.api.repositories;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.DeclaracionAreaModel;

@Repository
public interface IDeclaracionAreaRepository extends JpaRepository<DeclaracionAreaModel, Long> {

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAllByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    @Query("SELECT d.folioOrigen FROM DeclaracionAreaModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    @Query("SELECT d.folioDesembarqueAmerb FROM DeclaracionAreaModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueAmerb();

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    @Query("SELECT d FROM DeclaracionAreaModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    @Query("SELECT d FROM DeclaracionAreaModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

    @Override
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    Page<DeclaracionAreaModel> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    Slice<DeclaracionAreaModel> findSliceBy(Pageable pageable);

    @Query("SELECT d.id FROM DeclaracionAreaModel d ORDER BY d.fechaDeclaracion DESC")
    List<Long> findIdsPaginados(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findByIdInOrderByFechaDeclaracionDesc(List<Long> ids);
}
