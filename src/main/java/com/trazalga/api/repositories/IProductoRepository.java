package com.trazalga.api.repositories;

import com.trazalga.api.models.ProductoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IProductoRepository extends JpaRepository<ProductoModel, Long> {

    List<ProductoModel> findAllByOrderByNombreAsc();

    List<ProductoModel> findByActivoTrueOrActivoIsNullOrderByNombreAsc();

    List<ProductoModel> findByEspecieIdOrderByNombreAsc(Long especieId);

    @Query("SELECT p FROM ProductoModel p WHERE p.especie.id = :especieId AND (p.activo = true OR p.activo IS NULL) ORDER BY p.nombre ASC")
    List<ProductoModel> findActivosByEspecieId(@Param("especieId") Long especieId);

    Optional<ProductoModel> findByCodigoIgnoreCase(String codigo);

    Optional<ProductoModel> findByNombreIgnoreCase(String nombre);
}
