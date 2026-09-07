package com.trazalga.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.AmerbEspecieHabilitadaModel;

@Repository
public interface IAmerbEspecieHabilitadaRepository extends JpaRepository<AmerbEspecieHabilitadaModel, Long> {

    List<AmerbEspecieHabilitadaModel> findByAmerbIdAndActivoTrue(Long amerbId);

    Optional<AmerbEspecieHabilitadaModel> findByAmerbIdAndEspecieIdAndActivoTrue(Long amerbId, Long especieId);

}
