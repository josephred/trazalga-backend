package com.trazalga.api.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.services.DeclaracionRecolectorService;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;




@RestController
@RequestMapping("/declaracionrecolector")
public class DeclaracionRecolectorController {

    @Autowired
    private DeclaracionRecolectorService declaracionRecolectorService;

    @GetMapping
    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolector() {
        return this.declaracionRecolectorService.getDeclaracionesRecolector();
    }
    
    @PostMapping
    public DeclaracionRecolectorModel saveDeclaracionRecoleccion(@RequestBody DeclaracionRecolectorModel declaracionRecolector) {
        return this.declaracionRecolectorService.saveDeclaracionRecolector(declaracionRecolector);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<DeclaracionRecolectorModel> getDeclaracionRecolectorById(@PathVariable("id") Long id) {
        return this.declaracionRecolectorService.getById(id);
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
}
