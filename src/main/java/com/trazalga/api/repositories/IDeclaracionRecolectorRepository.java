package com.trazalga.api.repositories;


import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionRecolectorModel;

@Repository
public interface IDeclaracionRecolectorRepository extends JpaRepository<DeclaracionRecolectorModel, Long> {
    
    // Método para obtener todas las declaraciones del recolector en orden descendente por el campo especificado
    // @Query("SELECT d FROM DeclaracionRecolectorModel d ORDER BY d.campoEspecifico DESC")
    // public ArrayList<DeclaracionRecolectorModel> findAllOrderByCampoEspecificoDesc();
    // ArrayList<DeclaracionRecolectorModel> findAllByUsuarioId(Long usuarioId);

    // Método personalizado para encontrar registros donde declaracion_destinatario_id es null para un usuario destinatario específico
    List<DeclaracionRecolectorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(Long usuarioDestinatarioId);

    // Método para obtener todas las declaraciones del recolector por id de usuario
    ArrayList<DeclaracionRecolectorModel> findAllByUsuarioId(Long usuarioId);

    // Método para obtener todas las declaraciones del recolector por id de usuario ordenadas por fecha de declaración descendente
    ArrayList<DeclaracionRecolectorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    // Método para obtener el último folioOrigen
    @Query("SELECT d.folioOrigen FROM DeclaracionRecolectorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Método para obtener el último folioDesembarqueRO
    @Query("SELECT d.folioDesembarqueRo FROM DeclaracionRecolectorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueRo();
}
