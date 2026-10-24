package com.trazalga.api.repositories;

import com.trazalga.api.models.TransportistaFusionLogModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ITransportistaFusionLogRepository extends JpaRepository<TransportistaFusionLogModel, Long> {

    List<TransportistaFusionLogModel> findByDestinoId(Long destinoId);

    List<TransportistaFusionLogModel> findByOrigenId(Long origenId);
}
