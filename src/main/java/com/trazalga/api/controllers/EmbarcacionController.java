package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.services.EmbarcacionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/embarcacion")
public class EmbarcacionController {

    @Autowired
    private EmbarcacionService embarcacionService;

    @GetMapping
    public ArrayList<EmbarcacionModel> getEmbarcaciones(){
        return this.embarcacionService.getEmbarcaciones();
    }
    
    @PostMapping
    public EmbarcacionModel saveEmbarcacion(@RequestBody EmbarcacionModel embarcacion) {       
        return this.embarcacionService.saveEmbarcacion(embarcacion);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<EmbarcacionModel> getEmbarcacionById(@PathVariable("id") Long id) {
        return this.embarcacionService.getById(id);
    }

    @PutMapping(path="{id}")
    public EmbarcacionModel updatEmbarcacionById(@RequestBody EmbarcacionModel request,@PathVariable("id") Long id) {
        return this.embarcacionService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.embarcacionService.deleteEmbarcacion(id);
        if(ok){
            return "Embarcacion id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
