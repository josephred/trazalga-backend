package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.services.HumedadEstadoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/humedadestado")
public class HumedadEstadoController {

    @Autowired
    private HumedadEstadoService humedadEstadoService;

    @GetMapping
    public ArrayList<HumedadEstadoModel> getHumedadEstados(){
        return this.humedadEstadoService.getHumedadEstados();
    }
    
    @PostMapping
    public HumedadEstadoModel saveHumedadEstado(@RequestBody HumedadEstadoModel humedadEstado) {       
        return this.humedadEstadoService.saveHumedadEstado(humedadEstado);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<HumedadEstadoModel> getHumedadEstadoById(@PathVariable("id") Long id) {
        return this.humedadEstadoService.getById(id);
    }

    @PutMapping(path="{id}")
    public HumedadEstadoModel updatHumedadEstadoById(@RequestBody HumedadEstadoModel request,@PathVariable("id") Long id) {
        return this.humedadEstadoService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.humedadEstadoService.deleteHumedadEstado(id);
        if(ok){
            return "HumedadEstado id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
