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

import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.services.PerfilService;

@RestController
@RequestMapping("/perfil")
public class PerfilController {

    @Autowired
    private PerfilService perfilService;

    @GetMapping
    public ArrayList<PerfilModel> getPerfiles() {
        return this.perfilService.getPerfiles();
    }
    
    @PostMapping
    public PerfilModel savePerfil(@RequestBody PerfilModel perfil) {
        return this.perfilService.savePerfil(perfil);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<PerfilModel> getPerfilById(@PathVariable("id") Long id) {
        return this.perfilService.getById(id);
    }
    
    @PutMapping(path = "{id}")
    public PerfilModel updatePerfilById(@RequestBody PerfilModel request, @PathVariable("id") Long id) {
        return this.perfilService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.perfilService.deletePerfil(id);
        if(ok){
            return " Perfil " + id + " eliminado ";
        } else {
            return " ERROR al eliminar el perfil ";
        }
    }
}
