package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.EspecieModel;

@Repository
public interface IEspecieRepository extends JpaRepository<EspecieModel, Long> {

    // Visibles en la app: NULL equivale a activo (filas previas al flag)
    @org.springframework.data.jpa.repository.Query("SELECT x FROM EspecieModel x WHERE x.activo IS NULL OR x.activo = true")
    java.util.List<EspecieModel> findVisibles();

    java.util.Optional<EspecieModel> findByNombreIgnoreCase(String nombre);

    java.util.List<EspecieModel> findByNombreContainingIgnoreCase(String nombre);
}
