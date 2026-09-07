package com.trazalga.api.repositories;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionArmadorModel;

@Repository
public interface IDeclaracionArmadorRepository extends JpaRepository<DeclaracionArmadorModel, Long> {

    @Query("SELECT d FROM DeclaracionArmadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    @Query("SELECT d FROM DeclaracionArmadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    ArrayList<DeclaracionArmadorModel> findAllByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    ArrayList<DeclaracionArmadorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    @Query("SELECT d.folioOrigen FROM DeclaracionArmadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    @Query("SELECT d.folioDesembarqueDa FROM DeclaracionArmadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueDa();

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByUsuarioIdAndEspecieIdAndFechaDeclaracion(Long usuarioId, Long especieId, Date fechaDeclaracion);

    @Override
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    Page<DeclaracionArmadorModel> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    Slice<DeclaracionArmadorModel> findSliceBy(Pageable pageable);

    @Query("SELECT d.id FROM DeclaracionArmadorModel d ORDER BY d.fechaDeclaracion DESC")
    List<Long> findIdsPaginados(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByIdInOrderByFechaDeclaracionDesc(List<Long> ids);
}
