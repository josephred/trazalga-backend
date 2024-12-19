package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.ComposicionModel;
import com.trazalga.api.services.ComposicionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/composicion")
public class ComposicionController {

    @Autowired
    private ComposicionService composicionService;

    @GetMapping
    public ArrayList<ComposicionModel> getComposicions(){
        return this.composicionService.getComposicions();
    }
    
    @PostMapping
    public ComposicionModel saveComposicion(@RequestBody ComposicionModel composicion) {       
        return this.composicionService.saveComposicion(composicion);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<ComposicionModel> getComposicionById(@PathVariable("id") Long id) {
        return this.composicionService.getById(id);
    }

    @PutMapping(path="{id}")
    public ComposicionModel updatComposicionById(@RequestBody ComposicionModel request,@PathVariable("id") Long id) {
        return this.composicionService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.composicionService.deleteComposicion(id);
        if(ok){
            return "Composicion id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
