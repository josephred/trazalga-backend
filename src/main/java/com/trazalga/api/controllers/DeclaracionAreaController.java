package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.services.DeclaracionAreaService;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/declaracionarea")
public class DeclaracionAreaController {

    @Autowired
    private DeclaracionAreaService declaracionAreaService;

    @GetMapping
    public List<DeclaracionAreaModel> getAllDeclaraciones() {
        return declaracionAreaService.getAllDeclaraciones();
    }

    @GetMapping("/usuario/{usuarioId}")
    public List<DeclaracionAreaModel> getDeclaracionesByUsuario(@PathVariable Long usuarioId) {
        return declaracionAreaService.getDeclaracionesByUsuario(usuarioId);
    }

    @PostMapping
    public DeclaracionAreaModel saveDeclaracion(@RequestBody DeclaracionAreaModel declaracion) {
        return declaracionAreaService.saveDeclaracion(declaracion);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeclaracionAreaModel> getById(@PathVariable Long id) {
        Optional<DeclaracionAreaModel> declaracion = declaracionAreaService.getById(id);
        return declaracion.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public DeclaracionAreaModel updateDeclaracion(@PathVariable Long id, @RequestBody DeclaracionAreaModel request) {
        return declaracionAreaService.updateDeclaracion(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteById(@PathVariable Long id) {
        boolean ok = declaracionAreaService.deleteDeclaracion(id);
        return ok ? ResponseEntity.ok("Declaración eliminada") : ResponseEntity.badRequest().body("Error al eliminar");
    }

    @GetMapping("/lastfolioorigen")
    public ResponseEntity<String> getLastFolioOrigen() {
        String lastFolioOrigen = declaracionAreaService.getLastFolioOrigen();
        return ResponseEntity.ok(lastFolioOrigen);
    }

    @GetMapping("/lastfoliodesembarqueamerb")
    public ResponseEntity<String> getLastFolioDesembarqueAmerb() {
        String lastFolioDesembarqueAmerb = declaracionAreaService.getLastFolioDesembarqueAmerb();
        return ResponseEntity.ok(lastFolioDesembarqueAmerb);
    }
}
