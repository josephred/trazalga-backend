package com.trazalga.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ProvinciaModel;

@Repository
public interface IProvinciaRepository extends JpaRepository<ProvinciaModel, Long> {

    List<ProvinciaModel> findByRegionId(Long regionId);

    Optional<ProvinciaModel> findByNombre(String nombre);

    Optional<ProvinciaModel> findByNombreAndRegionId(String nombre, Long regionId);

}
