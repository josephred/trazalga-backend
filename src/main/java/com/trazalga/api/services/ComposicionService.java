package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.ComposicionModel;
import com.trazalga.api.repositories.IComposicionRepository;

@Service
public class ComposicionService {

    @Autowired
    IComposicionRepository composicionRepository;

    public ArrayList<ComposicionModel> getComposicions(boolean incluirOcultas){
        if (incluirOcultas) {
            return (ArrayList<ComposicionModel>) composicionRepository.findAll();
        }
        return new ArrayList<>(composicionRepository.findVisibles());
    } 

    public ComposicionModel saveComposicion(ComposicionModel composicion){
        return composicionRepository.save(composicion);
    }

    public Optional<ComposicionModel> getById(Long id){
        return composicionRepository.findById(id);
    }

    public ComposicionModel updateById(ComposicionModel request,Long id){
        ComposicionModel composicion = composicionRepository.findById(id).get();
        composicion.setNombre(request.getNombre());
        composicion.setDescripcion(request.getDescripcion());
        composicionRepository.save(composicion);
        return composicion;
    }

    public Boolean deleteComposicion(Long id){
        try{
            composicionRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }

    public ComposicionModel setActivo(Long id, boolean activo) {
        ComposicionModel x = composicionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe id " + id));
        x.setActivo(activo);
        return composicionRepository.save(x);
    }
}
