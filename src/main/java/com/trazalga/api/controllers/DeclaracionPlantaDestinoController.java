package com.trazalga.api.controllers;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.services.DeclaracionPlantaDestinoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/declaracion-planta-destino")
@Tag(name = "Planta - Destino", description = "Operaciones para las declaraciones de destino de la planta")
public class DeclaracionPlantaDestinoController {

    @Autowired
    private DeclaracionPlantaDestinoService service;

    @PostMapping
    @Operation(summary = "Crear una nueva declaración de destino",
               description = "Registra una nueva declaración sobre el destino de un producto procesado por la planta.")
    @ApiResponse(responseCode = "201", description = "Declaración creada exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos", content = @Content)
    public ResponseEntity<DeclaracionPlantaDestinoModel> createDeclaracion(@RequestBody DeclaracionPlantaDestinoModel declaracion) {
        DeclaracionPlantaDestinoModel nuevaDeclaracion = service.save(declaracion);
        return new ResponseEntity<>(nuevaDeclaracion, HttpStatus.CREATED);
    }

    @GetMapping("/usuario/{usuarioId}")
    @Operation(summary = "Obtener declaraciones de destino por ID de usuario",
               description = "Devuelve una lista de todas las declaraciones de destino realizadas por un usuario (planta).")
    @ApiResponse(responseCode = "200", description = "Lista de declaraciones obtenida")
    public ResponseEntity<List<DeclaracionPlantaDestinoModel>> getDeclaracionesPorUsuario(
            @Parameter(description = "ID del usuario (planta) para buscar sus declaraciones") @PathVariable("usuarioId") Long usuarioId) {
        List<DeclaracionPlantaDestinoModel> declaraciones = service.getAllByUsuarioId(usuarioId);
        return ResponseEntity.ok(declaraciones);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una declaración de destino por su ID",
               description = "Busca y devuelve una declaración específica usando su ID único.")
    @ApiResponse(responseCode = "200", description = "Declaración encontrada")
    @ApiResponse(responseCode = "404", description = "Declaración no encontrada", content = @Content)
    public ResponseEntity<DeclaracionPlantaDestinoModel> getDeclaracionPorId(@Parameter(description = "ID de la declaración a buscar") @PathVariable("id") Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    @Operation(summary = "Obtener declaraciones de destino pendientes para un destinatario",
               description = "Devuelve una lista de declaraciones de destino de planta que han sido asignadas a un usuario destinatario pero que aún no han sido procesadas por él (declaracion_destinatario_id es nulo).")
    @ApiResponse(responseCode = "200", description = "Lista de declaraciones pendientes")
    public ResponseEntity<List<DeclaracionPlantaDestinoModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId) {
        List<DeclaracionPlantaDestinoModel> declaraciones = service.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId);
        return ResponseEntity.ok(declaraciones);
    }
}