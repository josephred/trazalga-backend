package com.trazalga.api.repositories;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.FactorConversionModel;

@Repository
public interface IFactorConversionRepository extends JpaRepository<FactorConversionModel, Long> {

    List<FactorConversionModel> findByEspecieIdAndHumedadEstadoId(Long especieId, Long humedadEstadoId);

    List<FactorConversionModel> findByActivoTrue();

    @Query("SELECT fc FROM FactorConversionModel fc " +
           "WHERE fc.especie.id = :especieId " +
           "AND fc.humedadEstado.id = :humedadEstadoId " +
           "AND fc.activo = true " +
           "AND fc.vigenciaInicio <= :fecha " +
           "AND (fc.vigenciaFin IS NULL OR fc.vigenciaFin >= :fecha) " +
           "ORDER BY fc.vigenciaInicio DESC")
    List<FactorConversionModel> findFactoresVigentes(
            @Param("especieId") Long especieId,
            @Param("humedadEstadoId") Long humedadEstadoId,
            @Param("fecha") Date fecha);

    default Optional<FactorConversionModel> findFactorVigente(Long especieId, Long humedadEstadoId, Date fecha) {
        List<FactorConversionModel> list = findFactoresVigentes(especieId, humedadEstadoId, fecha);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
