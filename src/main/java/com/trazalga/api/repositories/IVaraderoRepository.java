package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.VaraderoModel;

@Repository
public interface IVaraderoRepository extends JpaRepository<VaraderoModel, Long> {

}
