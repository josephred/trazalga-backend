package com.trazalga.api.repositories;

import com.trazalga.api.models.BuzoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IBuzoRepository extends JpaRepository<BuzoModel, Long> {
    Optional<BuzoModel> findByCodigo(String codigo);
}
