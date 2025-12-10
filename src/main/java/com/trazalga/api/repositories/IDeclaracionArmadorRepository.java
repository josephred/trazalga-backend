package com.trazalga.api.repositories;

import java.util.ArrayList;
import java.util.Date; // Importante para el nuevo método
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionArmadorModel;

@Repository
public interface IDeclaracionArmadorRepository extends JpaRepository<DeclaracionArmadorModel, Long> {

    // Método para obtener todas las declaraciones del armador donde la declaración de destino es nula
    List<DeclaracionArmadorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(Long usuarioDestinatarioId);

    // Método para obtener todas las declaraciones del armador por ID de usuario
    ArrayList<DeclaracionArmadorModel> findAllByUsuarioId(Long usuarioId);

    // Método para obtener todas las declaraciones del armador por ID de usuario ordenadas por fecha de declaración descendente
    ArrayList<DeclaracionArmadorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    // Método para obtener el último folioOrigen
    @Query("SELECT d.folioOrigen FROM DeclaracionArmadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Método para obtener el último folioDesembarqueDA
    @Query("SELECT d.folioDesembarqueDa FROM DeclaracionArmadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueDa();

    // Método para la bandeja de entrada unificada
    List<DeclaracionArmadorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // --- NUEVO MÉTODO PARA EL SERVICIO DE CUOTAS ---
    // Filtra directamente en la BD por usuario, especie y fecha exacta.
    List<DeclaracionArmadorModel> findByUsuarioIdAndEspecieIdAndFechaDeclaracion(Long usuarioId, Long especieId, Date fechaDeclaracion);
}