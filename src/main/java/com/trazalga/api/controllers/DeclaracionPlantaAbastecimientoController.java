package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.services.DeclaracionPlantaAbastecimientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declaracion-planta-abastecimiento")
@CrossOrigin(origins = "*")
public class DeclaracionPlantaAbastecimientoController {

    @Autowired
    private DeclaracionPlantaAbastecimientoService service;

    @PostMapping()
    public ResponseEntity<DeclaracionPlantaAbastecimientoModel> createDeclaracion(@RequestBody DeclaracionPlantaAbastecimientoModel declaracion) {
        DeclaracionPlantaAbastecimientoModel nuevaDeclaracion = service.save(declaracion);
        return new ResponseEntity<>(nuevaDeclaracion, HttpStatus.CREATED);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<DeclaracionPlantaAbastecimientoModel>> getDeclaracionesPorUsuario(@PathVariable("usuarioId") Long usuarioId) {
        List<DeclaracionPlantaAbastecimientoModel> declaraciones = service.getAllByUsuarioId(usuarioId);
        return new ResponseEntity<>(declaraciones, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeclaracionPlantaAbastecimientoModel> getDeclaracionPorId(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(declaracion -> new ResponseEntity<>(declaracion, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}