package com.trazalga.api.repositories;

import com.trazalga.api.models.EmbarcacionModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IEmbarcacionRepository extends JpaRepository<EmbarcacionModel, Long> {
    Optional<EmbarcacionModel> findByCodigo(String codigo);

    // El nombre ya no es único a nivel nacional (ver EmbarcacionModel); "First" evita
    // que la consulta falle si hay más de una embarcación con el mismo nombre.
    Optional<EmbarcacionModel> findFirstByNombre(String nombre);

    List<EmbarcacionModel> findByCodigoRegion(Integer codigoRegion);
}

