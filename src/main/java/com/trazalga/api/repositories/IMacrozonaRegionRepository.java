package com.trazalga.api.repositories;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.MacrozonaModel;
import com.trazalga.api.models.MacrozonaRegionModel;

@Repository
public interface IMacrozonaRegionRepository extends JpaRepository<MacrozonaRegionModel, Long> {

    List<MacrozonaRegionModel> findByMacrozonaId(Long macrozonaId);

    List<MacrozonaRegionModel> findByRegionId(Long regionId);

    @Query("SELECT mr.macrozona FROM MacrozonaRegionModel mr " +
           "WHERE mr.region.id = :regionId " +
           "AND (mr.vigenciaInicio IS NULL OR :fecha >= mr.vigenciaInicio) " +
           "AND (mr.vigenciaFin IS NULL OR :fecha <= mr.vigenciaFin) " +
           "AND mr.macrozona.activo = true")
    List<MacrozonaModel> findMacrozonasActivasByRegionId(@Param("regionId") Long regionId, @Param("fecha") Date fecha);

    @Query("SELECT mr.region.id FROM MacrozonaRegionModel mr " +
           "WHERE mr.macrozona.id = :macrozonaId " +
           "AND (mr.vigenciaInicio IS NULL OR :fecha >= mr.vigenciaInicio) " +
           "AND (mr.vigenciaFin IS NULL OR :fecha <= mr.vigenciaFin)")
    List<Long> findRegionIdsByMacrozonaIdAndFecha(@Param("macrozonaId") Long macrozonaId, @Param("fecha") Date fecha);

    @Query("SELECT COUNT(mr) > 0 FROM MacrozonaRegionModel mr " +
           "WHERE mr.macrozona.id = :macrozonaId " +
           "AND mr.region.id = :regionId " +
           "AND (mr.vigenciaInicio IS NULL OR :fecha >= mr.vigenciaInicio) " +
           "AND (mr.vigenciaFin IS NULL OR :fecha <= mr.vigenciaFin)")
    boolean existsByMacrozonaIdAndRegionIdAndFecha(@Param("macrozonaId") Long macrozonaId,
                                                   @Param("regionId") Long regionId,
                                                   @Param("fecha") Date fecha);

    @Modifying
    @Query("DELETE FROM MacrozonaRegionModel mr WHERE mr.macrozona.id = :macrozonaId")
    void deleteByMacrozonaId(@Param("macrozonaId") Long macrozonaId);

}
