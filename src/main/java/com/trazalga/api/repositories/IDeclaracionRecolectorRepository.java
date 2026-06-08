package com.trazalga.api.repositories;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionRecolectorModel;

@Repository
public interface IDeclaracionRecolectorRepository extends JpaRepository<DeclaracionRecolectorModel, Long> {
    
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

    // Método para la bandeja de entrada unificada
    List<DeclaracionRecolectorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // --- NUEVO MÉTODO PARA EL SERVICIO DE CUOTAS (OPTIMIZADO) ---
    // Este método permite filtrar en la base de datos por Usuario, Especie y Fecha exacta.
    List<DeclaracionRecolectorModel> findByUsuarioIdAndEspecieIdAndFechaDeclaracion(Long usuarioId, Long especieId, Date fechaDeclaracion);

    // --- NUEVO MÉTODO PARA DASHBOARD DE CUOTAS GLOBAL ---
    @Query("SELECT COALESCE(SUM(d.desembarque), 0) FROM DeclaracionRecolectorModel d WHERE d.especie.id = :especieId AND DATE(d.fechaDeclaracion) BETWEEN DATE(:startDate) AND DATE(:endDate)")
    BigDecimal sumDesembarqueByEspecieIdAndDateRange(@Param("especieId") Long especieId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

}