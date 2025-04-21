package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DocumentoTributarioModel;
import com.trazalga.api.services.DocumentoTributarioService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/documentoTributario")
public class DocumentoTributarioController {

    @Autowired
    private DocumentoTributarioService documentoTributarioService;

    @GetMapping
    public ArrayList<DocumentoTributarioModel> getDocumentoTributarios(){
        return this.documentoTributarioService.getDocumentoTributarios();
    }
    
    @PostMapping
    public DocumentoTributarioModel saveDocumentoTributario(@RequestBody DocumentoTributarioModel documentoTributario) {       
        return this.documentoTributarioService.saveDocumentoTributario(documentoTributario);
    }
    
    @GetMapping(path = "/{id}")
    public Optional<DocumentoTributarioModel> getDocumentoTributarioById(@PathVariable("id") Long id) {
        return this.documentoTributarioService.getById(id);
    }

    @PutMapping(path="{id}")
    public DocumentoTributarioModel updatDocumentoTributarioById(@RequestBody DocumentoTributarioModel request,@PathVariable("id") Long id) {
        return this.documentoTributarioService.updateById(request, id);
    }
    
    @DeleteMapping(path= "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.documentoTributarioService.deleteDocumentoTributario(id);
        if(ok){
            return "Usuario id "+id+" eliminado";
        } else {
            return "ERROR al eliminar";
        }
    }
}
