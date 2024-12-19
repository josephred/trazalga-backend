package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;

@Service
public class UsuarioService {

    @Autowired
    IUsuarioRepository usuarioRepository;

    public ArrayList<UsuarioModel> getUsuarios(){
        return (ArrayList<UsuarioModel>) usuarioRepository.findAll();
    }

    public UsuarioModel saveUsuario(UsuarioModel usuarioModel){
        return usuarioRepository.save(usuarioModel);
    }

    public Optional<UsuarioModel> getById(Long id){
        return usuarioRepository.findById(id);
    }

    public UsuarioModel getByRut(String rut){
        return usuarioRepository.findByRut(rut);
    }

    public List<UsuarioModel> getUsuariosByPerfiles(List<Long> perfiles) {
        return usuarioRepository.findUsuariosByPerfiles(perfiles);
    }
    
    public List<UsuarioModel> getUsuariosByPerfil() {
        List<Long> longList = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        
        // List<Integer> integerList = longList.stream()
        //                                     .map(Long::intValue)
        //                                     .collect(Collectors.toList());
        return usuarioRepository.findUsuariosByPerfiles(longList);
    }


    public List<UsuarioModel> getAllUsuarios() {
        return usuarioRepository.findAll();
    }
    
    public Optional<UsuarioModel> getUsuarioById(Long id) {
        return usuarioRepository.findById(id);
    }
    
    // public List<UsuarioModel> getUsuariosByPerfiles(List<Long> perfiles) {
    //     return usuarioRepository.findUsuariosByPerfiles(perfiles);
    // }
   
    public UsuarioModel updateById(UsuarioModel request, Long id){
        UsuarioModel usuarioModel = usuarioRepository.findById(id).get();
        usuarioModel.setRut(request.getRut());
        usuarioModel.setNombres(request.getNombres());
        usuarioModel.setApellidop(request.getApellidop());       
        usuarioModel.setApellidom(request.getApellidom());       
        usuarioModel.setCorreo(request.getCorreo());       
        // usuarioModel.setFechaCreacion(request.getFechaCreacion());       
        usuarioModel.setPerfil(request.getPerfil());       
        usuarioRepository.save(usuarioModel);
        return usuarioModel;
    }

    public Boolean deleteUsuario(Long id){
        try{
            usuarioRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
}
