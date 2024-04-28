package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.repositories.IPerfilRepository;

@Service
public class PerfilService {
    
    @Autowired
    IPerfilRepository perfilRepository;

    public ArrayList<PerfilModel> getPerfiles(){
        return (ArrayList<PerfilModel>) perfilRepository.findAll();
    }
    
    public PerfilModel savePerfil(PerfilModel perfilModel){
        return perfilRepository.save(perfilModel);
    }

    public Optional<PerfilModel> getById(Long id){
        return perfilRepository.findById(id);
    }
    
    public PerfilModel updateById(PerfilModel request, Long id){
        PerfilModel perfilModel = perfilRepository.findById(id).get();
        perfilModel.setNombre(request.getNombre());
        perfilModel.setDescripcion(request.getDescripcion());
        perfilRepository.save(perfilModel);
        return perfilModel;
    }

    public Boolean deletePerfil(Long id){
        try{
            perfilRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
}
