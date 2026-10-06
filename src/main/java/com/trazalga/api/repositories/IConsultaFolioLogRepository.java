package com.trazalga.api.repositories;

import com.trazalga.api.models.ConsultaFolioLogModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para registro y auditoría de consultas de folios (T2.2).
 */
@Repository
public interface IConsultaFolioLogRepository extends JpaRepository<ConsultaFolioLogModel, Long> {
}
