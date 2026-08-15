package com.trazalga.api.repositories;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;

@Repository
public interface IDeclaracionComercializadorRepository extends JpaRepository<DeclaracionComercializadorModel, Long> {
    // Método para obtener todas las declaraciones del recolector en orden descendente por el campo especificado
    // @Query("SELECT d FROM DeclaracionRecolectorModel d ORDER BY d.campoEspecifico DESC")
    // public ArrayList<DeclaracionRecolectorModel> findAllOrderByCampoEspecificoDesc();
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionComercializadorModel> findAllByUsuarioId(Long usuarioId);
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    ArrayList<DeclaracionComercializadorModel> findAllByUsuarioIdOrderByFechaDeclaracionDesc(Long usuarioId);
    // Método para obtener el último folioOrigen
    @Query("SELECT d.folioOrigen FROM DeclaracionComercializadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioOrigen();

    // Método para obtener el último folioDesembarqueRO
    @Query("SELECT d.folioDesembarqueAc FROM DeclaracionComercializadorModel d ORDER BY d.id DESC")
    List<String> findLastFolioDesembarqueAc();

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findByUsuarioDestinatarioId(Long usuarioDestinatarioId);

    // Método personalizado para encontrar registros donde declaracion_destinatario_id es null para un usuario destinatario específico
    // Seleccionables por el destinatario: no consumidas y no rechazadas
    // (estado NULL = declaraciones previas a la columna, equivalen a ENVIADA)
    @Query("SELECT d FROM DeclaracionComercializadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND d.declaracionDestinatario IS NULL AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId);

    // Igual que la anterior, pero incluye además las que ya consumió el documento :consumidaPorId
    // (al editar ese documento el formulario debe re-mostrarlas marcadas y recalcular el resumen).
    @Query("SELECT d FROM DeclaracionComercializadorModel d WHERE d.usuarioDestinatario.id = :usuarioDestinatarioId "
            + "AND (d.declaracionDestinatario IS NULL OR d.declaracionDestinatario = :consumidaPorId) "
            + "AND (d.estado IS NULL OR d.estado <> 'RECHAZADA')")
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findAsignadasParaEditar(@Param("usuarioDestinatarioId") Long usuarioDestinatarioId, @Param("consumidaPorId") Long consumidaPorId);

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
    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    List<DeclaracionComercializadorModel> findAll();

    @EntityGraph(attributePaths = {"usuario", "especie", "composicion", "humedadEstado", "usuarioDestinatario"})
    Page<DeclaracionComercializadorModel> findAllBy(Pageable pageable);
}
