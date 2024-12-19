package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.EspecieModel;

@Repository
public interface IEspecieRepository extends JpaRepository<EspecieModel, Long> {

    
}
