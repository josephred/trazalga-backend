package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionPlantaDestinoRepository extends JpaRepository<DeclaracionPlantaDestinoModel, Long> {

    // Obtener todas las declaraciones de un usuario (planta) ordenadas por fecha de
    // declaración descendente
    List<DeclaracionPlantaDestinoModel> findAllByUsuarioIdOrderByFechaDeclaracionDestinoDesc(Long usuarioId);

    // Obtener el último folioDeclaracionAbastecimientoPlanta registrado para
    // generar el correlativo
    // @Query("SELECT d.folioDeclaracionAbastecimientoPlanta FROM
    // DeclaracionPlantaDestinoModel d ORDER BY d.id DESC")
    // String findLastFolioDeclaracionAbastecimientoPlanta();

    String findTopByOrderByIdDescFolioDeclaracionAbastecimientoPlanta();

    // Método para obtener declaraciones pendientes para un destinatario
    List<DeclaracionPlantaDestinoModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(
            Long usuarioDestinatarioId);
}