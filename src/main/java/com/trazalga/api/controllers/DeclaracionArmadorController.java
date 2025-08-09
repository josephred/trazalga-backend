package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.RestController;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.services.DeclaracionArmadorService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/declaracionarmador")
public class DeclaracionArmadorController {

    @Autowired
    private DeclaracionArmadorService declaracionArmadorService;

    /**
     * Obtener todas las declaraciones de armador
     */
    @GetMapping
    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmador() {
        return this.declaracionArmadorService.getDeclaracionesArmador();
    }
    
    /**
     * Obtener todas las declaraciones de armador por ID de usuario
     */
    @GetMapping(path = "/usuario/{idUsuario}")
    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmadorIdUsuario(@PathVariable("idUsuario") Long idUsuario){
        return this.declaracionArmadorService.getDeclaracionesArmadorIdUsuario(idUsuario);
    }

    /**
     * Guardar una nueva declaración de armador
     */
    @PostMapping
    public DeclaracionArmadorModel saveDeclaracionArmador(@RequestBody DeclaracionArmadorModel declaracionArmador) {
    // public String saveDeclaracionArmador(@RequestBody DeclaracionArmadorModel declaracionArmador) {
        return this.declaracionArmadorService.saveDeclaracionArmador(declaracionArmador);
        // return "OOKK";
        // return new DeclaracionArmadorModel();
    }
    
    /**
     * Obtener una declaración de armador por ID
     */
    @GetMapping(path = "/{id}")
    public Optional<DeclaracionArmadorModel> getDeclaracionArmadorById(@PathVariable("id") Long id) {
        return this.declaracionArmadorService.getById(id);
    }

    /**
     * Obtener declaraciones de armador donde usuarioDestinatario es NULL
     */
    @Operation(summary = "Obtener declaraciones pendientes para un destinatario",
               description = "Devuelve una lista de declaraciones de armador que han sido asignadas a un usuario destinatario pero que aún no han sido procesadas por él (declaracion_destinatario_id es nulo).")
    @ApiResponse(responseCode = "200", description = "Lista de declaraciones pendientes")
    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    public ResponseEntity<List<DeclaracionArmadorModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId) {
        List<DeclaracionArmadorModel> declaraciones = declaracionArmadorService.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId);
        return ResponseEntity.ok(declaraciones);
    }

    /**
     * Actualizar una declaración de armador por ID
     */
    @PutMapping(path = "{id}")
    public DeclaracionArmadorModel updateDeclaracionArmadorById(@RequestBody DeclaracionArmadorModel request, @PathVariable("id") Long id) {
        return this.declaracionArmadorService.updateById(request, id);
    }

    /**
     * Eliminar una declaración de armador por ID
     */
    @DeleteMapping(path = "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.declaracionArmadorService.deleteDeclaracionArmador(id);
        if(ok){
            return "Declaración " + id + " eliminada correctamente.";
        } else {
            return "ERROR al eliminar la declaración.";
        }
    }

    /**
     * Obtener el último folioOrigen registrado
     */
    @GetMapping(path = "/lastfolioorigen")
    public ResponseEntity<String> getLastFolioOrigen() {
        String lastFolioOrigen = declaracionArmadorService.getLastFolioOrigen();
        return ResponseEntity.ok(lastFolioOrigen);
    }

    /**
     * Obtener el último folioDesembarqueDa registrado
     */
    @GetMapping(path = "/lastfoliodesembarqueda")
    public ResponseEntity<String> getLastFolioDesembarqueDa() {
        String lastFolioDesembarqueDa = declaracionArmadorService.getLastFolioDesembarqueDa();
        return ResponseEntity.ok(lastFolioDesembarqueDa);
    }
}
