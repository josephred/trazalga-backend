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

@RestController
@RequestMapping("/api/declaracion-planta-abastecimiento")
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
    @Operation(summary = "Obtener declaraciones de abastecimiento pendientes para un destinatario",
               description = "Devuelve una lista de declaraciones de abastecimiento de planta que han sido asignadas a un usuario destinatario pero que aún no han sido procesadas por él (declaracion_destinatario_id es nulo).")
    @ApiResponse(responseCode = "200", description = "Lista de declaraciones pendientes")
    public ResponseEntity<List<DeclaracionPlantaAbastecimientoModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId) {
        List<DeclaracionPlantaAbastecimientoModel> declaraciones = service.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId);
        return ResponseEntity.ok(declaraciones);
    }
}