package com.trazalga.api.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.PlantaModel;

@Repository
public interface IPlantaRepository extends JpaRepository<PlantaModel, Long> {
    @Override
    @EntityGraph(attributePaths = {"comuna", "comuna.region"})
    List<PlantaModel> findAll();
}
