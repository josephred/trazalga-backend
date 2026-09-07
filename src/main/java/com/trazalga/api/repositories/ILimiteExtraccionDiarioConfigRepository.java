package com.trazalga.api.repositories;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;

@Repository
public interface ILimiteExtraccionDiarioConfigRepository extends JpaRepository<LimiteExtraccionDiarioConfigModel, Long> {

    List<LimiteExtraccionDiarioConfigModel> findByActivoTrue();

    @Query("SELECT r FROM LimiteExtraccionDiarioConfigModel r " +
           "WHERE r.activo = true " +
           "AND (:perfil IS NULL OR r.perfilAplicable = :perfil) " +
           "AND (r.vigenciaInicio IS NULL OR r.vigenciaInicio <= :fecha) " +
           "AND (r.vigenciaFin IS NULL OR r.vigenciaFin >= :fecha)")
    List<LimiteExtraccionDiarioConfigModel> findReglasVigentes(
            @Param("perfil") String perfil,
            @Param("fecha") Date fecha);

}
