package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.VedaEspecieModel;

import java.util.Date;
import java.util.List;

@Repository
public interface VedaEspecieRepository extends JpaRepository<VedaEspecieModel, Long> {

    // Find active vedas for a specific species and region (null region matches all)
    @Query("SELECT v FROM VedaEspecieModel v WHERE v.especie.id = :especieId AND (v.region IS NULL OR v.region.id = :regionId) AND :fecha BETWEEN v.fechaInicio AND v.fechaFin")
    List<VedaEspecieModel> findVedasActivasPorEspecieYRegionYFecha(@Param("especieId") Long especieId, @Param("regionId") Long regionId, @Param("fecha") Date fecha);

    // Find active vedas globally or by species within a date range (for reports)
    @Query("SELECT v FROM VedaEspecieModel v WHERE v.fechaInicio <= :endDate AND v.fechaFin >= :startDate")
    List<VedaEspecieModel> findVedasEnRango(@Param("startDate") Date startDate, @Param("endDate") Date endDate);

}
