package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.PlantaModel;

@Repository
public interface IPlantaRepository extends JpaRepository<PlantaModel, Long> {
}
