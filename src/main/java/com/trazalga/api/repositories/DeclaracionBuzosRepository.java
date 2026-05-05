package com.trazalga.api.repositories;

import com.trazalga.api.models.DeclaracionBuzosModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeclaracionBuzosRepository extends JpaRepository<DeclaracionBuzosModel, Long> {
    List<DeclaracionBuzosModel> findByDeclaracionArmadorId(Long declaracionArmadorId);
    List<DeclaracionBuzosModel> findByDeclaracionRecolectorId(Long declaracionRecolectorId);
    List<DeclaracionBuzosModel> findByDeclaracionAreaId(Long declaracionAreaId);
}
