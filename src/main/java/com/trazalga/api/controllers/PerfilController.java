package com.trazalga.api.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.services.PerfilService;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping
    public ResponseEntity<List<PerfilModel>> getPerfiles() {
        return ResponseEntity.ok(perfilService.getPerfiles());
    }

    @PostMapping
    public ResponseEntity<PerfilModel> savePerfil(@RequestBody PerfilModel perfil) {
        return ResponseEntity.ok(perfilService.savePerfil(perfil));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PerfilModel> getPerfilById(@PathVariable Long id) {
        return perfilService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PerfilModel> updatePerfilById(@RequestBody PerfilModel request, @PathVariable Long id) {
        return ResponseEntity.ok(perfilService.updateById(request, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteById(@PathVariable Long id) {
        return perfilService.deletePerfil(id)
                ? ResponseEntity.ok("Perfil " + id + " eliminado.")
                : ResponseEntity.badRequest().body("ERROR al eliminar el perfil.");
    }
}
