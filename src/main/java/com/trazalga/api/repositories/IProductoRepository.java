package com.trazalga.api.repositories;

import com.trazalga.api.models.ProductoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IProductoRepository extends JpaRepository<ProductoModel, Long> {

    List<ProductoModel> findAllByOrderByNombreAsc();

    List<ProductoModel> findByActivoTrueOrActivoIsNullOrderByNombreAsc();

    Optional<ProductoModel> findByCodigoIgnoreCase(String codigo);

    Optional<ProductoModel> findByNombreIgnoreCase(String nombre);
}
