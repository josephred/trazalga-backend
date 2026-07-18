package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ComposicionModel;

@Repository
public interface IComposicionRepository extends JpaRepository<ComposicionModel, Long> {

    // Visibles en la app: NULL equivale a activo (filas previas al flag)
    @org.springframework.data.jpa.repository.Query("SELECT x FROM ComposicionModel x WHERE x.activo IS NULL OR x.activo = true")
    java.util.List<ComposicionModel> findVisibles();


    
}
