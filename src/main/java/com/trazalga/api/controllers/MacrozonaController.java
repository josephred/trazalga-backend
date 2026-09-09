package com.trazalga.api.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.MacrozonaDTO;
import com.trazalga.api.models.MacrozonaModel;
import com.trazalga.api.services.MacrozonaService;
import com.trazalga.api.services.RegionService;

@RestController
@RequestMapping("/api/macrozonas")
public class MacrozonaController {

    @Autowired
    private MacrozonaService macrozonaService;

    @Autowired
    private RegionService regionService;

    @GetMapping
    public List<MacrozonaDTO> getAll() {
        return macrozonaService.getAll();
    }

    @GetMapping("/activas")
    public List<MacrozonaModel> getActivas() {
        return macrozonaService.getActivas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MacrozonaDTO> getById(@PathVariable("id") Long id) {
        return macrozonaService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maestros")
    public Map<String, Object> getMaestros() {
        Map<String, Object> out = new HashMap<>();
        out.put("regiones", regionService.getRegions());
        return out;
    }

    @PostMapping
    public ResponseEntity<MacrozonaDTO> save(@RequestBody MacrozonaDTO dto,
                                             @RequestAttribute(value = "usuarioId", required = false) Long usuarioId) {
        MacrozonaDTO saved = macrozonaService.save(dto, usuarioId);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MacrozonaDTO> update(@PathVariable("id") Long id,
                                               @RequestBody MacrozonaDTO dto,
                                               @RequestAttribute(value = "usuarioId", required = false) Long usuarioId) {
        dto.setId(id);
        MacrozonaDTO saved = macrozonaService.save(dto, usuarioId);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("id") Long id,
                                                      @RequestAttribute(value = "usuarioId", required = false) Long usuarioId) {
        boolean ok = macrozonaService.delete(id, usuarioId);
        Map<String, Object> res = new HashMap<>();
        res.put("ok", ok);
        return ResponseEntity.ok(res);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", false);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

}
