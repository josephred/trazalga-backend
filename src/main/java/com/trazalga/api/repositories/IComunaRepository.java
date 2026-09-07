package com.trazalga.api.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ComunaModel;

@Repository
public interface IComunaRepository extends JpaRepository<ComunaModel, Long> {

    List<ComunaModel> findByRegionId(Long regionId);

    List<ComunaModel> findByProvinciaId(Long provinciaId);

}
