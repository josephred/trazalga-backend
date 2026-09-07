package com.trazalga.api.controllers;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.services.FactorConversionService;

@RestController
@RequestMapping({"/factorconversion", "/api/factores-conversion"})
public class FactorConversionController {

    @Autowired
    private FactorConversionService service;

    @GetMapping
    public List<FactorConversionModel> getAll(@RequestParam(required = false, defaultValue = "false") boolean soloActivos) {
        return soloActivos ? service.getActivos() : service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<FactorConversionModel> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vigente")
    public ResponseEntity<FactorConversionModel> getVigente(
            @RequestParam Long especieId,
            @RequestParam Long humedadEstadoId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fecha) {
        return service.findFactorVigente(especieId, humedadEstadoId, fecha)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FactorConversionModel> create(@RequestBody FactorConversionModel model) {
        return ResponseEntity.ok(service.save(model));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FactorConversionModel> update(@PathVariable Long id, @RequestBody FactorConversionModel model) {
        return ResponseEntity.ok(service.update(id, model));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
