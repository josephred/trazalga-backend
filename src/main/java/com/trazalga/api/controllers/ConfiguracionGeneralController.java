package com.trazalga.api.controllers;

import com.trazalga.api.models.ConfiguracionGeneralModel;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/configuracion-general")
public class ConfiguracionGeneralController {

    @Autowired
    private ConfiguracionGeneralService service;

    @GetMapping
    public ResponseEntity<List<ConfiguracionGeneralModel>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{clave}")
    public ResponseEntity<ConfiguracionGeneralModel> getByClave(@PathVariable String clave) {
        return service.getByClave(clave)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{clave}")
    public ResponseEntity<ConfiguracionGeneralModel> update(@PathVariable String clave, @RequestBody ConfiguracionGeneralModel request) {
        return ResponseEntity.ok(service.updateConfig(clave, request.getValor()));
    }
}
