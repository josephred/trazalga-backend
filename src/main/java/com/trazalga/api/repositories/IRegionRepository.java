package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.RegionModel;

@Repository
public interface IRegionRepository extends JpaRepository<RegionModel, Long> {

    
}
