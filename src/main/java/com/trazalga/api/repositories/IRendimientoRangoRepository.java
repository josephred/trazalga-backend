package com.trazalga.api.repositories;

import com.trazalga.api.models.RendimientoRangoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface IRendimientoRangoRepository extends JpaRepository<RendimientoRangoModel, Long> {

    @Query("""
        SELECT r FROM RendimientoRangoModel r
        WHERE r.especie.id = :especieId
          AND r.humedadEntrada.id = :humedadEntradaId
          AND r.producto.id = :productoId
          AND r.activo = true
          AND r.vigenciaInicio <= :fecha
          AND (r.vigenciaFin IS NULL OR r.vigenciaFin >= :fecha)
        ORDER BY r.vigenciaInicio DESC, r.id DESC
    """)
    List<RendimientoRangoModel> findVigentes(
            @Param("especieId") Long especieId,
            @Param("humedadEntradaId") Long humedadEntradaId,
            @Param("productoId") Long productoId,
            @Param("fecha") Date fecha
    );

    @Query("""
        SELECT r FROM RendimientoRangoModel r
        WHERE r.especie.id = :especieId
          AND r.humedadEntrada.id = :humedadEntradaId
          AND r.producto.id = :productoId
          AND r.base = :base
          AND r.activo = true
          AND r.vigenciaInicio <= :fecha
          AND (r.vigenciaFin IS NULL OR r.vigenciaFin >= :fecha)
        ORDER BY r.vigenciaInicio DESC, r.id DESC
    """)
    List<RendimientoRangoModel> findVigentesPorBase(
            @Param("especieId") Long especieId,
            @Param("humedadEntradaId") Long humedadEntradaId,
            @Param("productoId") Long productoId,
            @Param("base") String base,
            @Param("fecha") Date fecha
    );

    default Optional<RendimientoRangoModel> findVigentePorBase(
            Long especieId, Long humedadEntradaId, Long productoId, String base, Date fecha) {
        List<RendimientoRangoModel> lista = findVigentesPorBase(especieId, humedadEntradaId, productoId, base, fecha);
        return (lista != null && !lista.isEmpty()) ? Optional.of(lista.get(0)) : Optional.empty();
    }

    default Optional<RendimientoRangoModel> findVigente(Long especieId, Long humedadEntradaId, Long productoId, Date fecha) {
        return findVigentePorBase(especieId, humedadEntradaId, productoId, "MASA_RECIBIDA", fecha);
    }
}

