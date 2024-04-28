package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DeclaracionComercializadorModel;

@Repository
public interface IDeclaracionComercializadorRepository extends JpaRepository<DeclaracionComercializadorModel, Long> {
    // Método para obtener todas las declaraciones del recolector en orden descendente por el campo especificado
    // @Query("SELECT d FROM DeclaracionRecolectorModel d ORDER BY d.campoEspecifico DESC")
    // public ArrayList<DeclaracionRecolectorModel> findAllOrderByCampoEspecificoDesc();

}
