package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.services.DeclaracionPlantaProduccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/declaracion-planta-produccion", "/declaracionplantaproduccion"})
@Tag(name = "Planta - Producción", description = "Operaciones para las declaraciones de producción de la planta")
public class DeclaracionPlantaProduccionController {

    @Autowired
    private DeclaracionPlantaProduccionService service;

    @PostMapping
    @Operation(summary = "Crear una nueva declaración de producción")
    @ApiResponse(responseCode = "201", description = "Declaración creada exitosamente")
    public ResponseEntity<DeclaracionPlantaProduccionModel> createDeclaracion(@RequestBody DeclaracionPlantaProduccionModel declaracion) {
        DeclaracionPlantaProduccionModel nuevaDeclaracion = service.save(declaracion);
        return new ResponseEntity<>(nuevaDeclaracion, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una declaración de producción existente")
    public ResponseEntity<DeclaracionPlantaProduccionModel> updateDeclaracion(@RequestBody DeclaracionPlantaProduccionModel request, @PathVariable("id") Long id) {
        DeclaracionPlantaProduccionModel actualizada = service.updateById(request, id);
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una declaración de producción")
    public ResponseEntity<String> deleteDeclaracion(@PathVariable("id") Long id) {
        boolean ok = service.deleteById(id);
        if (ok) {
            return ResponseEntity.ok("Declaracion " + id + " eliminada");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("ERROR al eliminar");
        }
    }

    @GetMapping("/usuario/{usuarioId}")
    @Operation(summary = "Obtener declaraciones de producción por ID de usuario")
    public ResponseEntity<List<DeclaracionPlantaProduccionModel>> getDeclaracionesPorUsuario(@PathVariable("usuarioId") Long usuarioId) {
        List<DeclaracionPlantaProduccionModel> declaraciones = service.getAllByUsuarioId(usuarioId);
        return ResponseEntity.ok(declaraciones);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una declaración de producción por su ID")
    public ResponseEntity<DeclaracionPlantaProduccionModel> getDeclaracionPorId(@PathVariable("id") Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    @Operation(summary = "Obtener declaraciones de producción pendientes para un destinatario",
               description = "Devuelve una lista de declaraciones de producción de planta que han sido asignadas a un usuario destinatario pero que aún no han sido procesadas por él (declaracion_destinatario_id es nulo).")
    @ApiResponse(responseCode = "200", description = "Lista de declaraciones pendientes")
    public ResponseEntity<List<DeclaracionPlantaProduccionModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId,
            @RequestParam(value = "consumidasPorId", required = false) Long consumidasPorId) {
        List<DeclaracionPlantaProduccionModel> declaraciones = service.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
        return ResponseEntity.ok(declaraciones);
    }
}