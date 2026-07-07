package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.CuotaExtraccionModel;

@Repository
public interface ICuotaExtraccionRepository extends JpaRepository<CuotaExtraccionModel, Long> {

    List<CuotaExtraccionModel> findByPerfilAndEspecieIdAndActivoTrue(String perfil, Long especieId);

    List<CuotaExtraccionModel> findByPerfilAndActivoTrue(String perfil);

    List<CuotaExtraccionModel> findByActivoTrue();

}
