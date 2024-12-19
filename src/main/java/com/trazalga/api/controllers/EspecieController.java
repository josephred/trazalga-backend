package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.services.EspecieService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/especie")
public class EspecieController {

    @Autowired
    private EspecieService especieService;

    @GetMapping
    public ArrayList<EspecieModel> getEspecies(){
        return this.especieService.getEspecies();
    }
    
    @PostMapping
    public EspecieModel saveEspecie(@RequestBody EspecieModel especie) {       
        return this.especieService.saveEspecie(especie);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<EspecieModel> getEspecieById(@PathVariable("id") Long id) {
        return this.especieService.getById(id);
    }

    @PutMapping(path="{id}")
    public EspecieModel updatEspecieById(@RequestBody EspecieModel request,@PathVariable("id") Long id) {
        return this.especieService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.especieService.deleteEspecie(id);
        if(ok){
            return "Especie id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
