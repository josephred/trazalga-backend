package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.services.BuzoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/buzo")
public class BuzoController {

    @Autowired
    private BuzoService buzoService;

    @GetMapping
    public ArrayList<BuzoModel> getBuzos(){
        return this.buzoService.getBuzos();
    }
    
    @PostMapping
    public BuzoModel saveBuzo(@RequestBody BuzoModel buzo) {       
        return this.buzoService.saveBuzo(buzo);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<BuzoModel> getBuzoById(@PathVariable("id") Long id) {
        return this.buzoService.getById(id);
    }

    @PutMapping(path="{id}")
    public BuzoModel updatBuzoById(@RequestBody BuzoModel request,@PathVariable("id") Long id) {
        return this.buzoService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.buzoService.deleteBuzo(id);
        if(ok){
            return "Buzo id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
