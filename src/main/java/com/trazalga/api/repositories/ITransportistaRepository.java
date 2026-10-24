package com.trazalga.api.repositories;

import com.trazalga.api.models.TransportistaModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ITransportistaRepository extends JpaRepository<TransportistaModel, Long> {

    Optional<TransportistaModel> findByRut(String rut);

    List<TransportistaModel> findByNombreNormalizadoAndComercializadorRef(String nombreNormalizado, Long comercializadorRef);

    List<TransportistaModel> findByEstado(String estado);

    List<TransportistaModel> findByNombreNormalizado(String nombreNormalizado);

    @Query("SELECT t FROM TransportistaModel t WHERE t.estado <> 'FUSIONADO'")
    List<TransportistaModel> findAllActivos();

    @Query("SELECT t FROM TransportistaModel t WHERE LOWER(t.nombreCanonico) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(t.nombreNormalizado) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR (t.rut IS NOT NULL AND LOWER(t.rut) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<TransportistaModel> buscarPorTermino(@Param("q") String q);
}
