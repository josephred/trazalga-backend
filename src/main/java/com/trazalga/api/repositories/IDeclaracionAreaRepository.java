package com.trazalga.api.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.DeclaracionAreaModel;

@Repository
public interface IDeclaracionAreaRepository extends JpaRepository<DeclaracionAreaModel, Long> {

    // Obtener todas las declaraciones de un usuario
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAllByUsuarioId(Long usuarioId);

    // Obtener declaraciones de un usuario ordenadas por fecha de declaración descendente
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);

    // Obtener el último folioOrigen registrado
    @Query("SELECT d.folioOrigen FROM DeclaracionAreaModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Obtener el último folioDesembarqueAmerb registrado
    @Query("SELECT d.folioDesembarqueAmerb FROM DeclaracionAreaModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueAmerb();

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // NUEVO MÉTODO AÑADIDO
    // Busca declaraciones por usuario destinatario donde aún no se ha creado una declaración de destino.
    // Seleccionables por el destinatario: no consumidas y no rechazadas
    // (estado NULL = declaraciones previas a la columna, equivalen a ENVIADA)
    @Query("SELECT d FROM DeclaracionAreaModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    // Igual que el anterior, pero al EDITAR un documento consumidor ya guardado también
    // se deben incluir las declaraciones que ese mismo documento ya consumió (para que
    // el formulario pueda re-mostrarlas marcadas y recalcular el resumen consolidado).
    @Query("SELECT d FROM DeclaracionAreaModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

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
    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    List<DeclaracionAreaModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "amerb", "caleta", "especie", "usuarioDestinatario", "composicion", "humedadEstado", "embarcacion", "buzo"})
    Page<DeclaracionAreaModel> findAllBy(Pageable pageable);
}
