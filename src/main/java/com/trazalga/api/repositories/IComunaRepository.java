package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ComunaModel;

@Repository
public interface IComunaRepository extends JpaRepository<ComunaModel, Long> {

    
}
