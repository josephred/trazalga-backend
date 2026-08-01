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
import java.util.Map;

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

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una declaración de destino existente",
               description = "Modifica los datos de una declaración ya registrada. Libera y re-marca las declaraciones de producción asociadas.")
    @ApiResponse(responseCode = "200", description = "Declaración actualizada exitosamente")
    @ApiResponse(responseCode = "404", description = "Declaración no encontrada", content = @Content)
    public ResponseEntity<DeclaracionPlantaDestinoModel> updateDeclaracion(
            @PathVariable Long id,
            @RequestBody DeclaracionPlantaDestinoModel declaracion) {
        try {
            DeclaracionPlantaDestinoModel updated = service.updateById(declaracion, id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una declaración de destino",
               description = "Elimina una declaración de destino. Libera las declaraciones de producción que consumía.")
    @ApiResponse(responseCode = "200", description = "Declaración eliminada exitosamente")
    @ApiResponse(responseCode = "404", description = "Declaración no encontrada o ya consumida", content = @Content)
    public ResponseEntity<Boolean> deleteDeclaracion(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.deleteById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(false);
        }
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
               description = "Devuelve declaraciones de destino asignadas pero no consumidas. Si se pasa consumidasPorId, incluye las ya consumidas por esa declaración específica.")
    @ApiResponse(responseCode = "200", description = "Lista de declaraciones pendientes")
    public ResponseEntity<List<DeclaracionPlantaDestinoModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId,
            @Parameter(description = "ID opcional de la declaración que consume estas, para incluirlas en edición")
            @RequestParam(required = false) Long consumidasPorId) {
        List<DeclaracionPlantaDestinoModel> declaraciones = service.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
        return ResponseEntity.ok(declaraciones);
    }

    @GetMapping("/detalle-consolidado/{id}")
    @Operation(summary = "Obtener el detalle consolidado de una declaración de destino",
               description = "Devuelve las líneas consolidadas (especie+producto+kg) de las producciones que componen esta declaración.")
    @ApiResponse(responseCode = "200", description = "Detalle consolidado obtenido")
    public ResponseEntity<List<Map<String, Object>>> getDetalleConsolidado(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetalleConsolidado(id));
    }
}