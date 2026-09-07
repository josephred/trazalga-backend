package com.trazalga.api.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.services.DeclaracionMarcaService;

@RestController
@RequestMapping({"/declaracionmarca", "/api/declaracion-marcas"})
public class DeclaracionMarcaController {

    @Autowired
    private DeclaracionMarcaService service;

    @GetMapping
    public List<DeclaracionMarcaModel> getAll(
            @RequestParam(required = false) String marca,
            @RequestParam(required = false, defaultValue = "false") boolean soloPendientes) {
        if (marca != null && !marca.isBlank()) {
            return service.getByMarca(marca);
        }
        if (soloPendientes) {
            return service.getPendientes();
        }
        return service.getAll();
    }

    @GetMapping("/{tipo}/{id}")
    public List<DeclaracionMarcaModel> getByDeclaracion(
            @PathVariable String tipo,
            @PathVariable Long id) {
        return service.getByDeclaracion(tipo.toUpperCase(), id);
    }

    @PutMapping("/{id}/resolver")
    public ResponseEntity<DeclaracionMarcaModel> resolver(@PathVariable Long id) {
        return service.resolverMarca(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
