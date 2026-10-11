package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.TareaProgramadaEjecucionModel;

@Repository
public interface ITareaProgramadaEjecucionRepository extends JpaRepository<TareaProgramadaEjecucionModel, Long> {

    List<TareaProgramadaEjecucionModel> findTop50ByOrderByInicioDesc();

    @Query("SELECT t FROM TareaProgramadaEjecucionModel t " +
           "WHERE t.id IN (SELECT MAX(t2.id) FROM TareaProgramadaEjecucionModel t2 GROUP BY t2.nombre) " +
           "ORDER BY t.inicio DESC")
    List<TareaProgramadaEjecucionModel> findUltimaEjecucionPorTarea();
}
