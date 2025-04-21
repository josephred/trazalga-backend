package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.DTOConverter;
import com.trazalga.api.dto.UsuarioDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.UsuarioService;


@RestController
@RequestMapping("/usuario")
public class UsuarioController {
    
    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ArrayList<UsuarioModel> getUsuarios() {
        return this.usuarioService.getUsuarios();
    }
    

    @GetMapping("/all-usuarios")
    public List<UsuarioModel> getAllUsuarios() {
        return usuarioService.getAllUsuarios();
    }
    
    @GetMapping("/usuarios/{id}")
    public ResponseEntity<UsuarioModel> getUsuarioById2(@PathVariable Long id) {
        Optional<UsuarioModel> usuarioOptional = usuarioService.getUsuarioById(id);
        return usuarioOptional.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping("/usuarios-by-perfiles")
    public List<UsuarioModel> getUsuariosByPerfiles(@RequestBody List<Long> perfiles) {
        return usuarioService.getUsuariosByPerfiles(perfiles);
    }


    @GetMapping(path = "/{id}")
    public Optional<UsuarioModel> getUsuarioById(@PathVariable("id") Long id) {
        return this.usuarioService.getById(id);
    }

    // @GetMapping(path = "/usuariosdestino")
    // public List<UsuarioModel> getUsuariosByPerfiles() {
    //     return this.usuarioService.getUsuariosByPerfiles();
    // }

    // @GetMapping(path = "/usuariosdestino")
    // public List<UsuarioModel> getUsuariosByPerfil() {
    //     return this.usuarioService.getUsuariosByPerfil();

    // }

    // @PostMapping(path = "/usuariosdestino")
    // public List<UsuarioModel> getUsuariosByPerfil(@RequestParam List<Long> perfiles) {
    //     return this.usuarioService.getUsuariosByPerfiles(perfiles);
    // }
    
    @PostMapping("/usuariosdestino")
    public List<UsuarioModel> getUsuariosByPerfil(@RequestBody Map<String, List<Long>> perfiles) {
        return this.usuarioService.getUsuariosByPerfiles(perfiles.get("perfiles"));
    }

    @GetMapping("/full")
    public List<UsuarioDTO> getUsuariosFull() {
        // List<UsuarioModel> usuarios = this.usuarioService.obtenerUsuarios(); // Suponiendo que obtienes los usuarios de algún servicio
        List<UsuarioModel> usuarios = this.usuarioService.getUsuarios(); // Suponiendo que obtienes los usuarios de algún servicio
        List<UsuarioDTO> usuariosDTO = new ArrayList<>();
        for (UsuarioModel usuario : usuarios) {
            UsuarioDTO usuarioDTO = DTOConverter.convertirAUsuarioDTO(usuario);
            usuariosDTO.add(usuarioDTO);
        }
        return usuariosDTO;
    }

    @PostMapping
    public UsuarioModel saveUsuario(@RequestBody UsuarioModel usuario) {
        return this.usuarioService.saveUsuario(usuario);
    }
    
    
    @PutMapping(path = "{id}")
    public UsuarioModel updateUsuarioById(@RequestBody UsuarioModel request, @PathVariable("id") Long id) {
        return this.usuarioService.updateById(request, id);
    }

    @DeleteMapping(path = "/{id}")
    public String deleteById(@PathVariable("id") Long id ){
        boolean ok = this.usuarioService.deleteUsuario(id);
        if(ok){
            return " Usuario " + id + " eliminado ";
        } else {
            return " ERROR al eliminar el usuario ";
        }
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioModel> login(@RequestBody UsuarioModel u){
        UsuarioModel usuario = this.usuarioService.getByRut(u.getRut());
        if( usuario != null && usuario.getClave().equals(u.getClave()) ){
             return ResponseEntity.ok(usuario);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    public String salida(){
        return "login";
    }
}
