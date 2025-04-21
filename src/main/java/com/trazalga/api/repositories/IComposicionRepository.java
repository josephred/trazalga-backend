package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.ComposicionModel;

@Repository
public interface IComposicionRepository extends JpaRepository<ComposicionModel, Long> {

    
}
