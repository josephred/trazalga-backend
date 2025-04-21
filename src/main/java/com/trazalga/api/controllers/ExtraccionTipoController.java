package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.services.ExtraccionTipoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/extracciontipo")
public class ExtraccionTipoController {

    @Autowired
    private ExtraccionTipoService extraccionTipoService;

    @GetMapping
    public ArrayList<ExtraccionTipoModel> getExtraccionTipos(){
        return this.extraccionTipoService.getExtraccionTipos();
    }
    
    @PostMapping
    public ExtraccionTipoModel saveExtraccionTipo(@RequestBody ExtraccionTipoModel extraccionTipo) {       
        return this.extraccionTipoService.saveExtraccionTipo(extraccionTipo);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<ExtraccionTipoModel> getExtraccionTipoById(@PathVariable("id") Long id) {
        return this.extraccionTipoService.getById(id);
    }

    @PutMapping(path="{id}")
    public ExtraccionTipoModel updatExtraccionTipoById(@RequestBody ExtraccionTipoModel request,@PathVariable("id") Long id) {
        return this.extraccionTipoService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.extraccionTipoService.deleteExtraccionTipo(id);
        if(ok){
            return "ExtraccionTipo id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
