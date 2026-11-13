package com.trazalga.api.repositories;

import com.trazalga.api.models.AdjuntoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IAdjuntoRepository extends JpaRepository<AdjuntoModel, Long> {

    Optional<AdjuntoModel> findByUuid(String uuid);

    Optional<AdjuntoModel> findFirstBySha256(String sha256);

    long countByRutaRelativa(String rutaRelativa);
}
