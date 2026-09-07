package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.services.ComunaService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping({"/comuna", "/api/comunas"})
public class ComunaController {

    @Autowired
    private ComunaService comunaService;

    @GetMapping
    public List<ComunaModel> getComunas(
            @RequestParam(name = "regionId", required = false) Long regionId,
            @RequestParam(name = "provinciaId", required = false) Long provinciaId) {
        if (provinciaId != null) {
            return this.comunaService.getByProvincia(provinciaId);
        }
        if (regionId != null) {
            return this.comunaService.getByRegion(regionId);
        }
        return this.comunaService.getComunas();
    }
    
    @PostMapping
    public ComunaModel saveComuna(@RequestBody ComunaModel comuna) {       
        return this.comunaService.saveComuna(comuna);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<ComunaModel> getComunaById(@PathVariable("id") Long id) {
        return this.comunaService.getById(id);
    }

    @PutMapping(path="{id}")
    public ComunaModel updatComunaById(@RequestBody ComunaModel request,@PathVariable("id") Long id) {
        return this.comunaService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.comunaService.deleteComuna(id);
        if(ok){
            return "Comuna id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
