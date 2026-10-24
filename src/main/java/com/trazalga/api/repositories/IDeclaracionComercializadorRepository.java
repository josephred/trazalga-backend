package com.trazalga.api.repositories;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionComercializadorModel;

@Repository
public interface IDeclaracionComercializadorRepository extends JpaRepository<DeclaracionComercializadorModel, Long> {

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionComercializadorModel> findAllByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionComercializadorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    @Query("SELECT d.folioOrigen FROM DeclaracionComercializadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    @Query("SELECT d.folioDesembarqueAc FROM DeclaracionComercializadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueAc();

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    @Query("SELECT d FROM DeclaracionComercializadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    @Query("SELECT d FROM DeclaracionComercializadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

    @Override
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    Page<DeclaracionComercializadorModel> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    Slice<DeclaracionComercializadorModel> findSliceBy(Pageable pageable);

    @Query("SELECT d.id FROM DeclaracionComercializadorModel d ORDER BY d.fechaDeclaracion DESC")
    List<Long> findIdsPaginados(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findByIdInOrderByFechaDeclaracionDesc(List<Long> ids);

    /**
     * Declaraciones consumidas por puntero por las declaraciones destino indicadas
     * (p. ej. tipo = 'PLANTA_ABASTECIMIENTO'). Reemplaza el findAll() + filtro en memoria de TV.1.
     * El tipo se compara en mayúsculas: pasar el valor ya normalizado.
     */
    @EntityGraph(attributePaths = {"usuario", "humedadEstado"})
    @Query("SELECT d FROM DeclaracionComercializadorModel d "
            + "WHERE d.declaracionDestinatario IN :destinoIds AND UPPER(d.consumidaPorTipo) = :tipo")
    List<DeclaracionComercializadorModel> findConsumidasPorDestinos(
            @Param("tipo") String tipo, @Param("destinoIds") List<Long> destinoIds);

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario", "usuarioDestinatario.comuna"})
    @Query("SELECT d FROM DeclaracionComercializadorModel d "
            + "WHERE (UPPER(REPLACE(REPLACE(COALESCE(d.placaPatente, ''), ' ', ''), '-', '')) = :patente "
            + "   OR UPPER(REPLACE(REPLACE(COALESCE(d.placaPatenteCarro, ''), ' ', ''), '-', '')) = :patente "
            + "   OR UPPER(REPLACE(REPLACE(COALESCE(d.patente, ''), ' ', ''), '-', '')) = :patente) "
            + "AND (d.estado IS NULL OR (UPPER(d.estado) <> 'RECHAZADA' AND UPPER(d.estado) <> 'ANULADA')) "
            + "ORDER BY d.fechaDeclaracion DESC, d.hora DESC, d.id DESC")
    List<DeclaracionComercializadorModel> findCandidatosPorPatente(@Param("patente") String patente);
}
