package com.trazalga.api.repositories;

import com.trazalga.api.models.VehiculoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IVehiculoRepository extends JpaRepository<VehiculoModel, Long> {

    Optional<VehiculoModel> findByPatente(String patente);

    @Query("SELECT v FROM VehiculoModel v WHERE LOWER(v.patente) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<VehiculoModel> buscarPorPatente(@Param("q") String q);
}
