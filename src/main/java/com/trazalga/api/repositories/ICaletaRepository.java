package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.CaletaModel;

@Repository
public interface ICaletaRepository extends JpaRepository<CaletaModel, Long> {
    java.util.List<CaletaModel> findByComunaId(Long comunaId);
    java.util.List<CaletaModel> findByRegionId(Long regionId);
}
