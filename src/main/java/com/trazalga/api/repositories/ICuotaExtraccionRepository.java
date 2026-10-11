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

    List<CuotaExtraccionModel> findByEstadoAndActivoTrue(String estado);

    List<CuotaExtraccionModel> findByEsPlantillaTrueAndActivoTrue();

    List<CuotaExtraccionModel> findByAmbitoAndActivoTrue(String ambito);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT c FROM CuotaExtraccionModel c " +
        "LEFT JOIN FETCH c.especie " +
        "LEFT JOIN FETCH c.extraccionTipo " +
        "LEFT JOIN FETCH c.humedadEstado " +
        "LEFT JOIN FETCH c.region " +
        "LEFT JOIN FETCH c.provincia " +
        "LEFT JOIN FETCH c.comuna " +
        "LEFT JOIN FETCH c.usuario " +
        "LEFT JOIN FETCH c.amerb " +
        "WHERE c.activo = true AND (c.estado IS NULL OR UPPER(c.estado) != 'CERRADA')"
    )
    List<CuotaExtraccionModel> findActivasConRelaciones();
}
