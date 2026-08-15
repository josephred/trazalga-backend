package com.trazalga.api.repositories;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionRecolectorModel;

@Repository
public interface IDeclaracionRecolectorRepository extends JpaRepository<DeclaracionRecolectorModel, Long> {
    
    // Método personalizado para encontrar registros donde declaracion_destinatario_id es null para un usuario destinatario específico
    // Seleccionables por el destinatario: no consumidas y no rechazadas
    // (estado NULL = declaraciones previas a la columna, equivalen a ENVIADA)
    @Query("SELECT d FROM DeclaracionRecolectorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    // Igual que el anterior, pero al EDITAR un documento consumidor ya guardado también
    // se deben incluir las declaraciones que ese mismo documento ya consumió (para que
    // el formulario pueda re-mostrarlas marcadas y recalcular el resumen consolidado).
    @Query("SELECT d FROM DeclaracionRecolectorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

    // Método para obtener todas las declaraciones del recolector por id de usuario
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionRecolectorModel> findAllByUsuarioId(Long usuarioId);

    // Método para obtener todas las declaraciones del recolector por id de usuario ordenadas por fecha de declaración descendente
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionRecolectorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    // Método para obtener el último folioOrigen
    @Query("SELECT d.folioOrigen FROM DeclaracionRecolectorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Método para obtener el último folioDesembarqueRO
    @Query("SELECT d.folioDesembarqueRo FROM DeclaracionRecolectorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueRo();

    // Método para la bandeja de entrada unificada
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // --- NUEVO MÉTODO PARA EL SERVICIO DE CUOTAS (OPTIMIZADO) ---
    // Este método permite filtrar en la base de datos por Usuario, Especie y Fecha exacta.
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findByUsuarioIdAndEspecieIdAndFechaDeclaracion(Long usuarioId, Long especieId, Date fechaDeclaracion);

    // --- NUEVO MÉTODO PARA DASHBOARD DE CUOTAS GLOBAL ---
    //
    // CORREGIDO EN rama perf/stress-tests:
    //   Antes:  ... AND DATE(d.fechaDeclaracion) BETWEEN DATE(:startDate) AND DATE(:endDate)
    //   Ahora:  ... AND d.fechaDeclaracion BETWEEN :startDate AND :endDate
    //
    // Aplicar una función (DATE()) sobre una columna indexada impide que MySQL
    // use el índice: cada llamada degeneraba en un full table scan. Con 400
    // filas no se nota; con 500.000 son segundos por invocación.
    //
    // La función era además redundante: fechaDeclaracion ya está declarada
    // como @Temporal(TemporalType.DATE), o sea que la columna es DATE y no
    // tiene componente horario que truncar. El resultado es idéntico.
    @Query("SELECT COALESCE(SUM(d.desembarque), 0) FROM DeclaracionRecolectorModel d WHERE d.especie.id = :especieId AND d.fechaDeclaracion BETWEEN :startDate AND :endDate")
    BigDecimal sumDesembarqueByEspecieIdAndDateRange(@Param("especieId") Long especieId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    // ---------------------------------------------------------------
    // AÑADIDO PARA PRUEBAS DE ESTRÉS
    //
    // findAll() se sobrescribe con @EntityGraph para que las relaciones
    // LAZY se traigan en UN solo JOIN en vez de N+1 consultas.
    //
    // findAllBy(Pageable) es la alternativa paginada: con 500.000 filas,
    // findAll() sin paginar agota el heap de la JVM sin importar cuán
    // optimizada esté la consulta. Migrar los controllers a este método.
    // ---------------------------------------------------------------
    @Override
    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionRecolectorModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "caleta", "especie", "comuna", "extraccionTipo", "composicion", "humedadEstado", "usuarioDestinatario"})
    Page<DeclaracionRecolectorModel> findAllBy(Pageable pageable);
}
