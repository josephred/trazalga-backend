package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.services.DeclaracionAreaService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/declaracionarea")
public class DeclaracionAreaController {

    @Autowired
    private DeclaracionAreaService declaracionAreaService;

    @Autowired
    private IDeclaracionAreaRepository declaracionAreaRepository;

    @GetMapping
    public ResponseEntity<?> getAllDeclaraciones(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean conTotal) {
        if (page == null) {
            return ResponseEntity.ok(this.declaracionAreaService.getAllDeclaraciones());
        }
        int tamano = Math.min(size, 200);
        Pageable conExtra = PageRequest.of(page, tamano + 1, Sort.by(Sort.Direction.DESC, "fechaDeclaracion"));

        List<Long> ids = declaracionAreaRepository.findIdsPaginados(conExtra);
        boolean hayMas = ids.size() > tamano;
        if (hayMas) {
            ids = ids.subList(0, tamano);
        }

        List<DeclaracionAreaModel> datos = ids.isEmpty()
                ? List.of()
                : declaracionAreaRepository.findByIdInOrderByFechaDeclaracionDesc(ids);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("content", datos);
        respuesta.put("page", page);
        respuesta.put("size", tamano);
        respuesta.put("hasNext", hayMas);
        return ResponseEntity.ok(respuesta);
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
    
    // NUEVO ENDPOINT AÑADIDO
    // consumidasPorId (opcional): al editar un documento consumidor ya guardado, incluye
    // también las declaraciones que ese documento ya consumió (ver DeclaracionAreaService).
    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    public ResponseEntity<List<DeclaracionAreaModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @PathVariable Long usuarioDestinatarioId,
            @RequestParam(required = false) Long consumidasPorId) {
        List<DeclaracionAreaModel> declaraciones = declaracionAreaService.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
        return ResponseEntity.ok(declaraciones);
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