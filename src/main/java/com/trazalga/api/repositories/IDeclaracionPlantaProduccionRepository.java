package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IDeclaracionPlantaProduccionRepository extends JpaRepository<DeclaracionPlantaProduccionModel, Long> {

    // Obtener todas las declaraciones de un usuario (planta) ordenadas por fecha de producción descendente
    List<DeclaracionPlantaProduccionModel> findAllByUsuarioIdOrderByFechaProduccionDesc(Long usuarioId);

    // Obtener el último folioDeclaracionPpla registrado para generar el correlativo
    @Query("SELECT d.folioDeclaracionPpla FROM DeclaracionPlantaProduccionModel d ORDER BY d.id DESC")
    List<String> findLastFolioDeclaracionPpla();

    // Método para obtener declaraciones pendientes para un destinatario
    List<DeclaracionPlantaProduccionModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(Long usuarioDestinatarioId);

    // Método para obtener declaraciones consumidas por una declaración específica
    List<DeclaracionPlantaProduccionModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatario(Long usuarioDestinatarioId, Long declaracionDestinatario);
}