package com.trazalga.api.repositories;

import com.trazalga.api.models.EmbarcacionModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IEmbarcacionRepository extends JpaRepository<EmbarcacionModel, Long> {
    Optional<EmbarcacionModel> findByCodigo(String codigo);
}
