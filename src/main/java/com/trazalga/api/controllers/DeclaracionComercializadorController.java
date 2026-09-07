package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.services.DeclaracionComercializadorService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;

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
@RequestMapping("/declaracioncomercializador")
public class DeclaracionComercializadorController {

    @Autowired
    private DeclaracionComercializadorService declaracionComercializadorService;

    @Autowired
    private IDeclaracionComercializadorRepository declaracionComercializadorRepository;

    @GetMapping
    public ResponseEntity<?> getDeclaracionesComercializador(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean conTotal) {
        if (page == null) {
            return ResponseEntity.ok(this.declaracionComercializadorService.getDeclaracionesComercializador());
        }
        int tamano = Math.min(size, 200);
        Pageable conExtra = PageRequest.of(page, tamano + 1, Sort.by(Sort.Direction.DESC, "fechaDeclaracion"));

        List<Long> ids = declaracionComercializadorRepository.findIdsPaginados(conExtra);
        boolean hayMas = ids.size() > tamano;
        if (hayMas) {
            ids = ids.subList(0, tamano);
        }

        List<DeclaracionComercializadorModel> datos = ids.isEmpty()
                ? List.of()
                : declaracionComercializadorRepository.findByIdInOrderByFechaDeclaracionDesc(ids);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("content", datos);
        respuesta.put("page", page);
        respuesta.put("size", tamano);
        respuesta.put("hasNext", hayMas);
        return ResponseEntity.ok(respuesta);
    }
        
    @GetMapping(path = "/usuario/{usuarioId}")
    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializadorIdUsuario(@PathVariable("usuarioId") Long usuarioId){
        return this.declaracionComercializadorService.getDeclaracionesComercializadorIdUsuario(usuarioId);
    }
        
    @GetMapping(path = "/usuariodesc/{usuarioId}")
    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializadorUsuarioIdDesc(@PathVariable("usuarioId") Long usuarioId){
        return this.declaracionComercializadorService.getDeclaracionesComercializadorUsuarioIdDesc(usuarioId);
    }

    @PostMapping
    public DeclaracionComercializadorModel saveDeclaracionRecoleccion(@RequestBody DeclaracionComercializadorModel declaracionComercializador) {
        return this.declaracionComercializadorService.saveDeclaracionComercializador(declaracionComercializador);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<DeclaracionComercializadorModel> getDeclaracionComercializadorById(@PathVariable("id") Long id) {
        return this.declaracionComercializadorService.getById(id);
    }
    
    // consumidasPorId (opcional): al editar un documento consumidor ya guardado, incluye
    // también las declaraciones que ese documento ya consumió (ver DeclaracionComercializadorService).
    @GetMapping("/usuariosdestinatarios/{usuarioDestinatarioId}")
    public ResponseEntity<List<DeclaracionComercializadorModel>> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            @PathVariable Long usuarioDestinatarioId,
            @RequestParam(required = false) Long consumidasPorId) {
        List<DeclaracionComercializadorModel> declaraciones = declaracionComercializadorService.getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId, consumidasPorId);
        return ResponseEntity.ok(declaraciones);
    }

    @PutMapping(path = "{id}")
    public DeclaracionComercializadorModel updateDeclaracionComercializadorById(@RequestBody DeclaracionComercializadorModel request, @PathVariable("id") Long id) {
        return this.declaracionComercializadorService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.declaracionComercializadorService.deleteDeclaracionComercializador(id);
        if(ok){
            return " Declaracion " + id + " eliminada ";
        } else {
            return " ERROR al eliminar ";
        }
    }
    
    @GetMapping(path = "/lastfolioorigen")
    public ResponseEntity<String> getLastFolioOrigen() {
        String lastFolioOrigen = declaracionComercializadorService.getLastFolioOrigen();
        return ResponseEntity.ok(lastFolioOrigen);
    }

    @GetMapping(path = "/lastfoliodesembarqueac")
    public ResponseEntity<String> getLastFolioDesembarqueRo() {
        String lastFolioDesembarqueAc = declaracionComercializadorService.getLastFolioDesembarqueAc();
        return ResponseEntity.ok(lastFolioDesembarqueAc);
    }

    /**
     * Líneas consolidadas del documento (especie + humedad + composición con totales),
     * derivadas de las declaraciones de origen que esta declaración consume.
     */
    @GetMapping(path = "/detalle-consolidado/{id}")
    public ResponseEntity<List<java.util.Map<String, Object>>> getDetalleConsolidado(@PathVariable("id") Long id) {
        return ResponseEntity.ok(declaracionComercializadorService.getDetalleConsolidado(id));
    }
}
