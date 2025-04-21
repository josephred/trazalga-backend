package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.services.CaletaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/caleta")
public class CaletaController {

    @Autowired
    private CaletaService caletaService;

    @GetMapping
    public ArrayList<CaletaModel> getCaletas(){
        return this.caletaService.getCaletas();
    }
    
    @PostMapping
    public CaletaModel saveCaleta(@RequestBody CaletaModel caleta) {       
        return this.caletaService.saveCaleta(caleta);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<CaletaModel> getCaletaById(@PathVariable("id") Long id) {
        return this.caletaService.getById(id);
    }

    @PutMapping(path="{id}")
    public CaletaModel updatCaletaById(@RequestBody CaletaModel request,@PathVariable("id") Long id) {
        return this.caletaService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.caletaService.deleteCaleta(id);
        if(ok){
            return "Caleta id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
