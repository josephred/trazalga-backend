package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ExtraccionTipoModel;

@Repository
public interface IExtraccionTipoRepository extends JpaRepository<ExtraccionTipoModel, Long> {

    java.util.Optional<ExtraccionTipoModel> findByNombreIgnoreCase(String nombre);

    java.util.List<ExtraccionTipoModel> findByNombreContainingIgnoreCase(String nombre);
}
