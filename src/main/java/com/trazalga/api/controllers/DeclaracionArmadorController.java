package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.RestController;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.services.DeclaracionArmadorService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

    /**
     * Obtener todas las declaraciones de armador
     */
    @GetMapping
    public ResponseEntity<?> getDeclaracionesArmador(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean conTotal) {
        if (page == null) {
            return ResponseEntity.ok(this.declaracionArmadorService.getDeclaracionesArmador());
        }
        int tamano = Math.min(size, 200);
        Pageable conExtra = PageRequest.of(page, tamano + 1, Sort.by(Sort.Direction.DESC, "fechaDeclaracion"));

        List<Long> ids = declaracionArmadorRepository.findIdsPaginados(conExtra);
        boolean hayMas = ids.size() > tamano;
        if (hayMas) {
            ids = ids.subList(0, tamano);
        }

        List<DeclaracionArmadorModel> datos = ids.isEmpty()
                ? List.of()
                : declaracionArmadorRepository.findByIdInOrderByFechaDeclaracionDesc(ids);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("content", datos);
        respuesta.put("page", page);
        respuesta.put("size", tamano);
        respuesta.put("hasNext", hayMas);
        return ResponseEntity.ok(respuesta);
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
            @Parameter(description = "ID del usuario destinatario") @PathVariable Long usuarioDestinatarioId,
            @Parameter(description = "Al editar un documento consumidor ya guardado, incluye también las declaraciones que ese documento ya consumió")
            @RequestParam(required = false) Long consumidasPorId) {
        List<DeclaracionArmadorModel> declaraciones = declaracionArmadorService.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
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
