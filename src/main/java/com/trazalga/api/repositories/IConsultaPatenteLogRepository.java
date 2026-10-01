package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.ConsultaPatenteLogModel;
import java.util.List;

@Repository
public interface IConsultaPatenteLogRepository extends JpaRepository<ConsultaPatenteLogModel, Long> {
    List<ConsultaPatenteLogModel> findByPatenteOrderByFechaDesc(String patente);
}
