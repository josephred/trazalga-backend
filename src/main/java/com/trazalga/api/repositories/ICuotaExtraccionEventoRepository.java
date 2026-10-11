package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.CuotaExtraccionEventoModel;

@Repository
public interface ICuotaExtraccionEventoRepository extends JpaRepository<CuotaExtraccionEventoModel, Long> {

    @Query("SELECT e FROM CuotaExtraccionEventoModel e WHERE e.cuota.id = :cuotaId ORDER BY e.createdAt DESC")
    List<CuotaExtraccionEventoModel> findByCuotaIdOrderByCreatedAtDesc(@Param("cuotaId") Long cuotaId);
}
