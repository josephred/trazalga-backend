package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.services.DeclaracionPlantaDestinoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declaracion-planta-destino")
@CrossOrigin(origins = "*")
public class DeclaracionPlantaDestinoController {

    @Autowired
    private DeclaracionPlantaDestinoService service;

    @PostMapping
    public ResponseEntity<DeclaracionPlantaDestinoModel> createDeclaracion(@RequestBody DeclaracionPlantaDestinoModel declaracion) {
        DeclaracionPlantaDestinoModel nuevaDeclaracion = service.save(declaracion);
        return new ResponseEntity<>(nuevaDeclaracion, HttpStatus.CREATED);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<DeclaracionPlantaDestinoModel>> getDeclaracionesPorUsuario(@PathVariable("usuarioId") Long usuarioId) {
        List<DeclaracionPlantaDestinoModel> declaraciones = service.getAllByUsuarioId(usuarioId);
        return new ResponseEntity<>(declaraciones, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeclaracionPlantaDestinoModel> getDeclaracionPorId(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(declaracion -> new ResponseEntity<>(declaracion, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}