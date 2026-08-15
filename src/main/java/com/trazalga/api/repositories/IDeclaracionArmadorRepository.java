package com.trazalga.api.repositories;

import java.util.ArrayList;
import java.util.Date; // Importante para el nuevo método
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionArmadorModel;

@Repository
public interface IDeclaracionArmadorRepository extends JpaRepository<DeclaracionArmadorModel, Long> {

    // Método para obtener todas las declaraciones del armador donde la declaración de destino es nula
    // Seleccionables por el destinatario: no consumidas y no rechazadas
    // (estado NULL = declaraciones previas a la columna, equivalen a ENVIADA)
    @Query("SELECT d FROM DeclaracionArmadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    // Igual que el anterior, pero al EDITAR un documento consumidor ya guardado también
    // se deben incluir las declaraciones que ese mismo documento ya consumió (para que
    // el formulario pueda re-mostrarlas marcadas y recalcular el resumen consolidado).
    @Query("SELECT d FROM DeclaracionArmadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

    // Método para obtener todas las declaraciones del armador por ID de usuario
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    ArrayList<DeclaracionArmadorModel> findAllByUsuarioId(Long usuarioId);

    // Método para obtener todas las declaraciones del armador por ID de usuario ordenadas por fecha de declaración descendente
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    ArrayList<DeclaracionArmadorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    // Método para obtener el último folioOrigen
    @Query("SELECT d.folioOrigen FROM DeclaracionArmadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Método para obtener el último folioDesembarqueDA
    @Query("SELECT d.folioDesembarqueDa FROM DeclaracionArmadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueDa();

    // Método para la bandeja de entrada unificada
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // --- NUEVO MÉTODO PARA EL SERVICIO DE CUOTAS ---
    // Filtra directamente en la BD por usuario, especie y fecha exacta.
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findByUsuarioIdAndEspecieIdAndFechaDeclaracion(Long usuarioId, Long especieId, Date fechaDeclaracion);

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
    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    List<DeclaracionArmadorModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "embarcacion", "buzo", "usuarioDestinatario", "caleta", "comuna", "especie", "composicion", "humedadEstado"})
    Page<DeclaracionArmadorModel> findAllBy(Pageable pageable);
}
