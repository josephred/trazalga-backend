package com.trazalga.api.repositories;

import java.math.BigDecimal;
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

import com.trazalga.api.models.DeclaracionRecolectorModel;

@Repository
public interface IDeclaracionRecolectorRepository extends JpaRepository<DeclaracionRecolectorModel, Long> {
    
    @Query("SELECT d FROM DeclaracionRecolectorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    @Query("SELECT d FROM DeclaracionRecolectorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionRecolectorModel> findAllByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionRecolectorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    @Query("SELECT d.folioOrigen FROM DeclaracionRecolectorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    @Query("SELECT d.folioDesembarqueRo FROM DeclaracionRecolectorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueRo();

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByUsuarioIdAndEspecieIdAndFechaDeclaracion(Long usuarioId, Long especieId, Date fechaDeclaracion);

    @Query("SELECT COALESCE(SUM(d.desembarque), 0) FROM DeclaracionRecolectorModel d WHERE d.especie.id = :especieId AND d.fechaDeclaracion BETWEEN :startDate AND :endDate")
    BigDecimal sumDesembarqueByEspecieIdAndDateRange(@Param("especieId") Long especieId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    @Override
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    Page<DeclaracionRecolectorModel> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    Slice<DeclaracionRecolectorModel> findSliceBy(Pageable pageable);

    @Query("SELECT d.id FROM DeclaracionRecolectorModel d ORDER BY d.fechaDeclaracion DESC")
    List<Long> findIdsPaginados(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByIdInOrderByFechaDeclaracionDesc(List<Long> ids);

    @Query("SELECT COALESCE(SUM(d.desembarque), 0) FROM DeclaracionRecolectorModel d " +
           "WHERE d.usuario.id = :usuarioId AND (:especieId IS NULL OR d.especie.id = :especieId) " +
           "AND d.fechaDeclaracion = :fecha")
    BigDecimal sumDesembarqueByUsuarioAndFecha(
            @Param("usuarioId") Long usuarioId,
            @Param("especieId") Long especieId,
            @Param("fecha") Date fecha);

    @Query("SELECT COALESCE(SUM(d.captura), 0) FROM DeclaracionRecolectorModel d " +
           "WHERE d.usuario.id = :usuarioId AND (:especieId IS NULL OR d.especie.id = :especieId) " +
           "AND d.fechaDeclaracion = :fecha")
    BigDecimal sumCapturaByUsuarioAndFecha(
            @Param("usuarioId") Long usuarioId,
            @Param("especieId") Long especieId,
            @Param("fecha") Date fecha);
}
