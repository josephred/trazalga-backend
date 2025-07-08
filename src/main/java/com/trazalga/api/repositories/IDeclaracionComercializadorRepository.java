package com.trazalga.api.repositories;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;

@Repository
public interface IDeclaracionComercializadorRepository extends JpaRepository<DeclaracionComercializadorModel, Long> {
    // Método para obtener todas las declaraciones del recolector en orden descendente por el campo especificado
    // @Query("SELECT d FROM DeclaracionRecolectorModel d ORDER BY d.campoEspecifico DESC")
    // public ArrayList<DeclaracionRecolectorModel> findAllOrderByCampoEspecificoDesc();
    ArrayList<DeclaracionComercializadorModel> findAllByUsuarioId(Long usuarioId);
    ArrayList<DeclaracionComercializadorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);
    // Método para obtener el último folioOrigen
    @Query("SELECT d.folioOrigen FROM DeclaracionComercializadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Método para obtener el último folioDesembarqueRO
    @Query("SELECT d.folioDesembarqueAc FROM DeclaracionComercializadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueAc();

    List<DeclaracionComercializadorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);
}
