package com.trazalga.api.repositories;

import com.trazalga.api.models.HistorialUbicacionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;

@Repository
public interface HistorialUbicacionRepository extends JpaRepository<HistorialUbicacionModel, Long> {
    List<HistorialUbicacionModel> findByUsuarioIdAndFechaRegistroBetweenOrderByFechaRegistroAsc(Long usuarioId, Date desde, Date hasta);
}
