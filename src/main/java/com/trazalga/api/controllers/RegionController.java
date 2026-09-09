package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.RegionModel;
import com.trazalga.api.services.RegionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping({"/region", "/api/regiones"})
public class RegionController {

    @Autowired
    private RegionService regionService;

    @GetMapping
    public ArrayList<RegionModel> getRegions(){
        return this.regionService.getRegions();
    }
    
    @PostMapping
    public RegionModel saveRegion(@RequestBody RegionModel region) {       
        return this.regionService.saveRegion(region);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<RegionModel> getRegionById(@PathVariable("id") Long id) {
        return this.regionService.getById(id);
    }

    @PutMapping(path="{id}")
    public RegionModel updatRegionById(@RequestBody RegionModel request,@PathVariable("id") Long id) {
        return this.regionService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.regionService.deleteRegion(id);
        if(ok){
            return "Usuario id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
