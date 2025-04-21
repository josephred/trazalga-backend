package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionEnvioModel;

@Repository
public interface IDeclaracionEnvioRepository extends JpaRepository<DeclaracionEnvioModel, Long> {

    
}
