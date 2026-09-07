package com.trazalga.api.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.AmerbEspecieHabilitadaModel;
import com.trazalga.api.services.AmerbEspecieHabilitadaService;

@RestController
@RequestMapping({"/amerbespeciehabilitada", "/api/amerb-especies-habilitadas"})
public class AmerbEspecieHabilitadaController {

    @Autowired
    private AmerbEspecieHabilitadaService service;

    @GetMapping
    public List<AmerbEspecieHabilitadaModel> getAll(@RequestParam(required = false) Long amerbId) {
        if (amerbId != null) {
            return service.getByAmerb(amerbId);
        }
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<AmerbEspecieHabilitadaModel> create(@RequestBody AmerbEspecieHabilitadaModel model) {
        return ResponseEntity.ok(service.save(model));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
