package com.trazalga.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.MacrozonaModel;

@Repository
public interface IMacrozonaRepository extends JpaRepository<MacrozonaModel, Long> {

    List<MacrozonaModel> findByActivoTrue();

    Optional<MacrozonaModel> findByCodigo(String codigo);

    Optional<MacrozonaModel> findByEsNacionalTrue();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

}
