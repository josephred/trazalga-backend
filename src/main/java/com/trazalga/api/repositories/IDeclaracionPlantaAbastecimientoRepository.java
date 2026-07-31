package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionPlantaAbastecimientoRepository extends JpaRepository<DeclaracionPlantaAbastecimientoModel, Long> {

    // Obtener todas las declaraciones de un usuario (planta) ordenadas por fecha de ingreso descendente
    List<DeclaracionPlantaAbastecimientoModel> findAllByUsuarioIdOrderByFechaIngresoPlantaDesc(Long usuarioId);

    // Obtener el último folioDeclaracionAPla registrado para generar el correlativo
    @Query("SELECT d.folioDeclaracionAPla FROM DeclaracionPlantaAbastecimientoModel d ORDER BY d.id DESC")
    List<String> findLastFolioDeclaracionAPla();

    // Seleccionables por el destinatario: no consumidas y no rechazadas
    // (estado NULL = declaraciones previas a la columna, equivalen a ENVIADA)
    @Query("SELECT d FROM DeclaracionPlantaAbastecimientoModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    List<DeclaracionPlantaAbastecimientoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    // Igual que la anterior, pero incluye además las que ya consumió el documento :consumidaPorId
    // (al editar ese documento el formulario debe re-mostrarlas marcadas y recalcular el resumen).
    @Query("SELECT d FROM DeclaracionPlantaAbastecimientoModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    List<DeclaracionPlantaAbastecimientoModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);
}