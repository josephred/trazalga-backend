package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.services.DeclaracionBuzosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/declaracionbuzos")
public class DeclaracionBuzosController {

    @Autowired
    private DeclaracionBuzosService declaracionBuzosService;

    @GetMapping
    public ArrayList<DeclaracionBuzosModel> obtenerDeclaracionBuzos() {
        return declaracionBuzosService.obtenerDeclaracionBuzos();
    }

    @PostMapping
    public DeclaracionBuzosModel guardarDeclaracionBuzos(@RequestBody DeclaracionBuzosModel declaracionBuzos) {
        return this.declaracionBuzosService.guardarDeclaracionBuzos(declaracionBuzos);
    }

    @GetMapping(path = "/{id}")
    public Optional<DeclaracionBuzosModel> obtenerDeclaracionBuzosPorId(@PathVariable("id") Long id) {
        return this.declaracionBuzosService.obtenerPorId(id);
    }

    @GetMapping(path = "/armador/{id}")
    public List<DeclaracionBuzosModel> obtenerPorDeclaracionArmadorId(@PathVariable("id") Long id) {
        return this.declaracionBuzosService.obtenerPorDeclaracionArmadorId(id);
    }

    @GetMapping(path = "/recolector/{id}")
    public List<DeclaracionBuzosModel> obtenerPorDeclaracionRecolectorId(@PathVariable("id") Long id) {
        return this.declaracionBuzosService.obtenerPorDeclaracionRecolectorId(id);
    }

    @GetMapping(path = "/area/{id}")
    public List<DeclaracionBuzosModel> obtenerPorDeclaracionAreaId(@PathVariable("id") Long id) {
        return this.declaracionBuzosService.obtenerPorDeclaracionAreaId(id);
    }

    @DeleteMapping(path = "/{id}")
    public String eliminarPorId(@PathVariable("id") Long id) {
        boolean ok = this.declaracionBuzosService.eliminarDeclaracionBuzos(id);
        if (ok) {
            return "Se eliminó el registro con id " + id;
        } else {
            return "No se pudo eliminar el registro con id " + id;
        }
    }
}
