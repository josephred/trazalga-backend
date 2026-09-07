package com.trazalga.api.controllers;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.services.LimiteExtraccionDiarioConfigService;

@RestController
@RequestMapping({"/limiteextracciondiarioconfig", "/api/limites-extraccion-diario"})
public class LimiteExtraccionDiarioConfigController {

    @Autowired
    private LimiteExtraccionDiarioConfigService service;

    @GetMapping
    public List<LimiteExtraccionDiarioConfigModel> getAll(@RequestParam(required = false, defaultValue = "false") boolean soloActivos) {
        return soloActivos ? service.getActivos() : service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LimiteExtraccionDiarioConfigModel> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vigentes")
    public List<LimiteExtraccionDiarioConfigModel> getVigentes(
            @RequestParam(required = false) String perfil,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date fecha) {
        return service.findReglasVigentes(perfil, fecha);
    }

    @PostMapping
    public ResponseEntity<LimiteExtraccionDiarioConfigModel> create(@RequestBody LimiteExtraccionDiarioConfigModel model) {
        return ResponseEntity.ok(service.save(model));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LimiteExtraccionDiarioConfigModel> update(@PathVariable Long id, @RequestBody LimiteExtraccionDiarioConfigModel model) {
        return ResponseEntity.ok(service.update(id, model));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
