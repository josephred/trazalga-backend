package com.trazalga.api.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.PlantaModel;
import com.trazalga.api.repositories.IPlantaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/planta")
@Tag(name = "Plantas", description = "Plantas almacenadas en la base de datos")
public class PlantaController {

    private final IPlantaRepository plantaRepository;

    public PlantaController(IPlantaRepository plantaRepository) {
        this.plantaRepository = plantaRepository;
    }

    @GetMapping
    @Operation(summary = "Obtiene todas las plantas disponibles")
    public List<PlantaModel> getPlantas() {
        return plantaRepository.findAll();
    }
}
