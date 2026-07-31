package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.services.DeclaracionPlantaAbastecimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/declaracion-planta-abastecimiento", "/declaracionplantaabastecimiento"})
@Tag(name = "Planta - Abastecimiento", description = "Operaciones para las declaraciones de abastecimiento de la planta")
public class DeclaracionPlantaAbastecimientoController {

    @Autowired
    private DeclaracionPlantaAbastecimientoService service;

    @PostMapping
    @Operation(summary = "Crear una nueva declaración de abastecimiento")
    @ApiResponse(responseCode = "201", description = "Declaración creada exitosamente")
    public ResponseEntity<DeclaracionPlantaAbastecimientoModel> createDeclaracion(@RequestBody DeclaracionPlantaAbastecimientoModel declaracion) {
        DeclaracionPlantaAbastecimientoModel nuevaDeclaracion = service.save(declaracion);
        return new ResponseEntity<>(nuevaDeclaracion, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una declaración de abastecimiento existente")
    public ResponseEntity<DeclaracionPlantaAbastecimientoModel> updateDeclaracion(@RequestBody DeclaracionPlantaAbastecimientoModel request, @PathVariable("id") Long id) {
        DeclaracionPlantaAbastecimientoModel actualizada = service.updateById(request, id);
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una declaración de abastecimiento")
    public ResponseEntity<String> deleteDeclaracion(@PathVariable("id") Long id) {
        boolean ok = service.deleteById(id);
        if (ok) {
            return ResponseEntity.ok("Declaracion " + id + " eliminada");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ERROR al eliminar");
        }
    }

    @GetMapping("/usuario/{usuarioId}")
    @Operation(summary = "Obtener declaraciones de abastecimiento por ID de usuario")
    public ResponseEntity<List<DeclaracionPlantaAbastecimientoModel>> getDeclaracionesPorUsuario(@PathVariable("usuarioId") Long usuarioId) {
        List<DeclaracionPlantaAbastecimientoModel> declaraciones = service.getAllByUsuarioId(usuarioId);
        return ResponseEntity.ok(declaraciones);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una declaración de abastecimiento por su ID")
    public ResponseEntity<DeclaracionPlantaAbastecimientoModel> getDeclaracionPorId(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    @Operation(summary = "Obtener declaraciones de abastecimiento pendientes para un destinatario")
    public ResponseEntity<List<DeclaracionPlantaAbastecimientoModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId,
            @RequestParam(value = "consumidasPorId", required = false) Long consumidasPorId) {
        List<DeclaracionPlantaAbastecimientoModel> declaraciones = service.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
        return ResponseEntity.ok(declaraciones);
    }

    @GetMapping("/detalle-consolidado/{id}")
    @Operation(summary = "Obtener el detalle consolidado (líneas de documento) de una declaración de abastecimiento")
    public ResponseEntity<List<Map<String, Object>>> getDetalleConsolidado(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.getDetalleConsolidado(id));
    }
}