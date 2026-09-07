package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.DeclaracionRecolectorService;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/declaracionrecolector")
public class DeclaracionRecolectorController {

    @Autowired
    private DeclaracionRecolectorService declaracionRecolectorService;

    @Autowired
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @GetMapping
    public ResponseEntity<?> getDeclaracionesRecolector(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean conTotal) {
        if (page == null) {
            return ResponseEntity.ok(this.declaracionRecolectorService.getDeclaracionesRecolector());
        }
        int tamano = Math.min(size, 200);
        Pageable conExtra = PageRequest.of(page, tamano + 1, Sort.by(Sort.Direction.DESC, "fechaDeclaracion"));

        List<Long> ids = declaracionRecolectorRepository.findIdsPaginados(conExtra);
        boolean hayMas = ids.size() > tamano;
        if (hayMas) {
            ids = ids.subList(0, tamano);
        }

        List<DeclaracionRecolectorModel> datos = ids.isEmpty()
                ? List.of()
                : declaracionRecolectorRepository.findByIdInOrderByFechaDeclaracionDesc(ids);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("content", datos);
        respuesta.put("page", page);
        respuesta.put("size", tamano);
        respuesta.put("hasNext", hayMas);
        return ResponseEntity.ok(respuesta);
    }
    
    @GetMapping(path = "/usuario/{idUsuario}")
    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolectorIdUsuario(@PathVariable("idUsuario") Long idUsuario){
        return this.declaracionRecolectorService.getDeclaracionesRecolectorIdUsuario(idUsuario);
    }

    @PostMapping
    public DeclaracionRecolectorModel saveDeclaracionRecoleccion(@RequestBody DeclaracionRecolectorModel declaracionRecolector) {
        return this.declaracionRecolectorService.saveDeclaracionRecolector(declaracionRecolector);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<DeclaracionRecolectorModel> getDeclaracionRecolectorById(@PathVariable("id") Long id) {
        return this.declaracionRecolectorService.getById(id);
    }

    // consumidasPorId (opcional): al editar un documento consumidor ya guardado, incluye
    // también las declaraciones que ese documento ya consumió (ver DeclaracionRecolectorService).
    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    public ResponseEntity<List<DeclaracionRecolectorModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @PathVariable Long usuarioDestinatarioId,
            @RequestParam(required = false) Long consumidasPorId) {
        List<DeclaracionRecolectorModel> declaraciones = declaracionRecolectorService.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
        return ResponseEntity.ok(declaraciones);
    }

    
    @PutMapping(path = "{id}")
    public DeclaracionRecolectorModel updateDeclaracionRecolectorById(@RequestBody DeclaracionRecolectorModel request, @PathVariable("id") Long id) {
        return this.declaracionRecolectorService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.declaracionRecolectorService.deleteDeclaracionRecolector(id);
        if(ok){
            return " Declaracion " + id + " eliminada ";
        } else {
            return " ERROR al eliminar ";
        }
    }

    @GetMapping(path = "/lastfolioorigen")
    public ResponseEntity<String> getLastFolioOrigen() {
        String lastFolioOrigen = declaracionRecolectorService.getLastFolioOrigen();
        return ResponseEntity.ok(lastFolioOrigen);
    }

    @GetMapping(path = "/lastfoliodesembarquero")
    public ResponseEntity<String> getLastFolioDesembarqueRo() {
        String lastFolioDesembarqueRo = declaracionRecolectorService.getLastFolioDesembarqueRo();
        return ResponseEntity.ok(lastFolioDesembarqueRo);
    }
}
