package com.trazalga.api.repositories;

import com.trazalga.api.models.GestionMensajeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IGestionMensajeRepository extends JpaRepository<GestionMensajeModel, Long> {

    /** Historial de la conversación completa de una declaración, ordenado cronológicamente. */
    List<GestionMensajeModel> findByDeclaracionTipoAndDeclaracionIdOrderByFechaEnvioAsc(String declaracionTipo, Long declaracionId);

    /** Cantidad de mensajes no leídos para un usuario. */
    long countByReceptorIdAndLeidoFalse(Long receptorId);

    /** Bandeja de gestiones pendientes: mensajes no leídos dirigidos a un usuario. */
    List<GestionMensajeModel> findByReceptorIdAndLeidoFalseOrderByFechaEnvioDesc(Long receptorId);

    /** Marcar como leídos: obtener los mensajes no leídos de una conversación para un receptor. */
    List<GestionMensajeModel> findByDeclaracionTipoAndDeclaracionIdAndReceptorIdAndLeidoFalse(
            String declaracionTipo, Long declaracionId, Long receptorId);

    /** Verificar si una declaración tiene mensajes de gestión. */
    boolean existsByDeclaracionTipoAndDeclaracionId(String declaracionTipo, Long declaracionId);
}
