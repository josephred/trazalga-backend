package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.services.DeclaracionComercializadorService;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;




@RestController
@RequestMapping("/declaracioncomercializador")
public class DeclaracionComercializadorController {

    @Autowired
    private DeclaracionComercializadorService declaracionComercializadorService;

    @GetMapping
    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializador() {
        return this.declaracionComercializadorService.getDeclaracionesComercializador();
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
}
