package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionEnvioModel;
import com.trazalga.api.services.DeclaracionEnvioService;

@RestController
@RequestMapping("/declaracionenvio")
public class DeclaracionEnvioController {

    @Autowired
    private DeclaracionEnvioService declaracionEnvioService;

    @GetMapping
    public ArrayList<DeclaracionEnvioModel> getDeclaracionEnvios() {
        return this.declaracionEnvioService.getDeclaracionEnvios();
    }
    
    @PostMapping
    public DeclaracionEnvioModel saveDeclaracionEnvio(@RequestBody DeclaracionEnvioModel declaracionEnvio) {
        return this.declaracionEnvioService.saveDeclaracionEnvio(declaracionEnvio);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<DeclaracionEnvioModel> getDeclaracionEnvioById(@PathVariable("id") Long id) {
        return this.declaracionEnvioService.getById(id);
    }
    
    @PutMapping(path = "{id}")
    public DeclaracionEnvioModel updateDeclaracionEnvioById(@RequestBody DeclaracionEnvioModel request, @PathVariable("id") Long id) {
        return this.declaracionEnvioService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.declaracionEnvioService.deleteDeclaracionEnvio(id);
        if(ok){
            return " DeclaracionEnvio " + id + " eliminado ";
        } else {
            return " ERROR al eliminar el declaracionEnvio ";
        }
    }
}
