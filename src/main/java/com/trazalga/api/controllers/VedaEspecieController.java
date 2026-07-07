package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.services.VedaEspecieService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/vedas")
public class VedaEspecieController {

    @Autowired
    private VedaEspecieService vedaService;

    @GetMapping
    public List<VedaEspecieModel> getAll() {
        return vedaService.getAll();
    }

    @GetMapping(path = "/{id}")
    public Optional<VedaEspecieModel> getById(@PathVariable("id") Long id) {
        return vedaService.getById(id);
    }

    /** Datos maestros (especies y regiones) para los selects del mantenedor. */
    @GetMapping("/maestros")
    public java.util.Map<String, Object> getMaestros() {
        return vedaService.getMaestros();
    }

    /** Los errores de configuración de la veda llegan como 422 con mensaje legible. */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> handleValidacion(IllegalArgumentException ex) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", ex.getMessage());
        return org.springframework.http.ResponseEntity.unprocessableEntity().body(body);
    }

    @PostMapping
    public VedaEspecieModel create(@RequestBody VedaEspecieModel veda) {
        return vedaService.save(veda);
    }

    @PutMapping(path = "/{id}")
    public VedaEspecieModel update(@RequestBody VedaEspecieModel request, @PathVariable("id") Long id) {
        request.setId(id);
        return vedaService.save(request);
    }

    @DeleteMapping(path = "/{id}")
    public String delete(@PathVariable("id") Long id) {
        boolean ok = vedaService.delete(id);
        if (ok) return "Veda id " + id + " eliminada";
        return "ERROR al eliminar veda";
    }
}
