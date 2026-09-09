package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.HumedadEstadoModel;

@Repository
public interface IHumedadEstadoRepository extends JpaRepository<HumedadEstadoModel, Long> {

    java.util.Optional<HumedadEstadoModel> findByNombreIgnoreCase(String nombre);

    java.util.List<HumedadEstadoModel> findByNombreContainingIgnoreCase(String nombre);
}
