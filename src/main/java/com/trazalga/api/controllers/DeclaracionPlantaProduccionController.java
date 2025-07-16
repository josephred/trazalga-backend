package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.services.DeclaracionPlantaProduccionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declaracion-planta-produccion")
@CrossOrigin(origins = "*")
public class DeclaracionPlantaProduccionController {

    @Autowired
    private DeclaracionPlantaProduccionService service;

    @PostMapping
    public ResponseEntity<DeclaracionPlantaProduccionModel> createDeclaracion(@RequestBody DeclaracionPlantaProduccionModel declaracion) {
        DeclaracionPlantaProduccionModel nuevaDeclaracion = service.save(declaracion);
        return new ResponseEntity<>(nuevaDeclaracion, HttpStatus.CREATED);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<DeclaracionPlantaProduccionModel>> getDeclaracionesPorUsuario(@PathVariable("usuarioId") Long usuarioId) {
        List<DeclaracionPlantaProduccionModel> declaraciones = service.getAllByUsuarioId(usuarioId);
        return new ResponseEntity<>(declaraciones, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeclaracionPlantaProduccionModel> getDeclaracionPorId(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(declaracion -> new ResponseEntity<>(declaracion, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}