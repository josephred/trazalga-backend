package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.trazalga.api.models.TipoDocumentoTributarioModel;
import com.trazalga.api.services.TipoDocumentoTributarioService;

@RestController
@RequestMapping("/tipoDocumentoTributario")
public class TipoDocumentoTributarioController {

    @Autowired
    private TipoDocumentoTributarioService tipoDocumentoTributarioService;

    @GetMapping
    public ArrayList<TipoDocumentoTributarioModel> getTiposDocumentoTributario() {
        return this.tipoDocumentoTributarioService.getTiposDocumentoTributario();
    }

    @PostMapping
    public TipoDocumentoTributarioModel saveTipoDocumentoTributario(@RequestBody TipoDocumentoTributarioModel tipo) {
        return this.tipoDocumentoTributarioService.saveTipoDocumentoTributario(tipo);
    }

    @GetMapping(path = "/{id}")
    public Optional<TipoDocumentoTributarioModel> getTipoDocumentoTributarioById(@PathVariable("id") Long id) {
        return this.tipoDocumentoTributarioService.getById(id);
    }

    @PutMapping(path = "/{id}")
    public TipoDocumentoTributarioModel updateTipoDocumentoTributarioById(@RequestBody TipoDocumentoTributarioModel request, @PathVariable("id") Long id) {
        return this.tipoDocumentoTributarioService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<String> deleteById(@PathVariable("id") Long id) {
        boolean ok = this.tipoDocumentoTributarioService.deleteById(id);
        if (ok) {
            return ResponseEntity.ok("Tipo documento tributario " + id + " eliminado");
        } else {
            return ResponseEntity.badRequest().body("ERROR al eliminar");
        }
    }
}
