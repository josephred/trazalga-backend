package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionPlantaDestinoRepository extends JpaRepository<DeclaracionPlantaDestinoModel, Long> {

    List<DeclaracionPlantaDestinoModel> findAllByUsuarioIdOrderByFechaDeclaracionDestinoDesc(Long usuarioId);

    @Query("SELECT d.folioDeclaracionDestino FROM DeclaracionPlantaDestinoModel d ORDER BY d.id DESC")
    List<String> findLastFolioDeclaracionDestino();

    String findTopByOrderByIdDescFolioDeclaracionAbastecimientoPlanta();

    List<DeclaracionPlantaDestinoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(Long usuarioDestinatarioId);

    List<DeclaracionPlantaDestinoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatario(Long usuarioDestinatarioId, Long declaracionDestinatario);

    @Query("SELECT d.producto.id, COUNT(d) FROM DeclaracionPlantaDestinoModel d WHERE d.producto IS NOT NULL GROUP BY d.producto.id")
    List<Object[]> countUsosPorProducto();

    @EntityGraph(attributePaths = {"usuario", "especie", "producto", "usuarioDestinatario"})
    @Query("SELECT d FROM DeclaracionPlantaDestinoModel d "
            + "WHERE (UPPER(REPLACE(REPLACE(COALESCE(d.placaPatente, ''), ' ', ''), '-', '')) = :patente "
            + "   OR UPPER(REPLACE(REPLACE(COALESCE(d.placaPatenteCarro, ''), ' ', ''), '-', '')) = :patente) "
            + "AND (d.estado IS NULL OR (UPPER(d.estado) <> 'RECHAZADA' AND UPPER(d.estado) <> 'ANULADA')) "
            + "ORDER BY d.fechaDeclaracionDestino DESC, d.hora DESC, d.id DESC")
    List<DeclaracionPlantaDestinoModel> findCandidatosPorPatente(@Param("patente") String patente);
}