package com.trazalga.api.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.services.ProvinciaService;

@RestController
@RequestMapping({"/provincia", "/api/provincias"})
public class ProvinciaController {

    @Autowired
    private ProvinciaService provinciaService;

    @GetMapping
    public List<ProvinciaModel> getProvincias(@RequestParam(name = "regionId", required = false) Long regionId) {
        if (regionId != null) {
            return provinciaService.getByRegion(regionId);
        }
        return provinciaService.getProvincias();
    }

    @PostMapping
    public ProvinciaModel saveProvincia(@RequestBody ProvinciaModel provincia) {
        return provinciaService.saveProvincia(provincia);
    }

    @GetMapping(path = "/{id}")
    public Optional<ProvinciaModel> getProvinciaById(@PathVariable("id") Long id) {
        return provinciaService.getById(id);
    }

    @GetMapping(path = "/region/{regionId}")
    public List<ProvinciaModel> getProvinciasByRegion(@PathVariable("regionId") Long regionId) {
        return provinciaService.getByRegion(regionId);
    }

    @PutMapping(path = "/{id}")
    public ProvinciaModel updateProvinciaById(@RequestBody ProvinciaModel request, @PathVariable("id") Long id) {
        return provinciaService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteById(@PathVariable("id") Long id) {
        boolean ok = provinciaService.deleteProvincia(id);
        if (ok) {
            return ResponseEntity.ok("Provincia id " + id + " eliminada");
        } else {
            return ResponseEntity.status(500).body("ERROR al eliminar provincia");
        }
    }
}
