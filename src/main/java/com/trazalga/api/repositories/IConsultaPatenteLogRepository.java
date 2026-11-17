package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.ConsultaPatenteLogModel;
import java.util.Date;
import java.util.List;

@Repository
public interface IConsultaPatenteLogRepository extends JpaRepository<ConsultaPatenteLogModel, Long> {
    List<ConsultaPatenteLogModel> findByPatenteOrderByFechaDesc(String patente);

    @Modifying
    @Query("DELETE FROM ConsultaPatenteLogModel l WHERE l.fecha < :cutoff")
    int deleteOlderThan(@Param("cutoff") Date cutoff);
}
