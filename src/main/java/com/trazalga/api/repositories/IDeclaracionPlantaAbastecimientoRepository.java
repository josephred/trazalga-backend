package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionPlantaAbastecimientoRepository extends JpaRepository<DeclaracionPlantaAbastecimientoModel, Long> {

    // Obtener todas las declaraciones de un usuario (planta) ordenadas por fecha de ingreso descendente
    List<DeclaracionPlantaAbastecimientoModel> findAllByUsuarioIdOrderByFechaIngresoPlantaDesc(Long usuarioId);

    // Obtener el último folioDeclaracionAPla registrado para generar el correlativo
    @Query("SELECT d.folioDeclaracionAPla FROM DeclaracionPlantaAbastecimientoModel d ORDER BY d.id DESC")
    List<String> findLastFolioDeclaracionAPla();

    // Método para obtener declaraciones pendientes para un destinatario
    List<DeclaracionPlantaAbastecimientoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(Long usuarioDestinatarioId);
}