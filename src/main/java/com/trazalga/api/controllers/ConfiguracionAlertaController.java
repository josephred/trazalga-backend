package com.trazalga.api.controllers;

import com.trazalga.api.models.ConfiguracionAlertaModel;
import com.trazalga.api.services.ConfiguracionAlertaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracion-alertas")
public class ConfiguracionAlertaController {

    @Autowired
    private ConfiguracionAlertaService service;

    @GetMapping
    public ResponseEntity<List<ConfiguracionAlertaModel>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguracionAlertaModel> update(@PathVariable Long id, @RequestBody ConfiguracionAlertaModel request) {
        return ResponseEntity.ok(service.updateConfig(id, request));
    }
}
