package com.trazalga.api.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.DeclaracionAreaModel;

@Repository
public interface IDeclaracionAreaRepository extends JpaRepository<DeclaracionAreaModel, Long> {

    // Obtener todas las declaraciones de un usuario
    List<DeclaracionAreaModel> findAllByUsuarioId(Long usuarioId);

    // Obtener declaraciones de un usuario ordenadas por fecha de declaración descendente
    List<DeclaracionAreaModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    // Obtener el último folioOrigen registrado
    @Query("SELECT d.folioOrigen FROM DeclaracionAreaModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Obtener el último folioDesembarqueAmerb registrado
    @Query("SELECT d.folioDesembarqueAmerb FROM DeclaracionAreaModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueAmerb();

    List<DeclaracionAreaModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // NUEVO MÉTODO AÑADIDO
    // Busca declaraciones por usuario destinatario donde aún no se ha creado una declaración de destino.
    // Seleccionables por el destinatario: no consumidas y no rechazadas
    // (estado NULL = declaraciones previas a la columna, equivalen a ENVIADA)
    @Query("SELECT d FROM DeclaracionAreaModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    List<DeclaracionAreaModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);
}