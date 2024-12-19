package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.repositories.IExtraccionTipoRepository;

@Service
public class ExtraccionTipoService {

    @Autowired
    IExtraccionTipoRepository extraccionTipoRepository;

    public ArrayList<ExtraccionTipoModel> getExtraccionTipos(){
        return (ArrayList<ExtraccionTipoModel>) extraccionTipoRepository.findAll();
    } 

    public ExtraccionTipoModel saveExtraccionTipo(ExtraccionTipoModel extraccionTipo){
        return extraccionTipoRepository.save(extraccionTipo);
    }

    public Optional<ExtraccionTipoModel> getById(Long id){
        return extraccionTipoRepository.findById(id);
    }

    public ExtraccionTipoModel updateById(ExtraccionTipoModel request,Long id){
        ExtraccionTipoModel extraccionTipo = extraccionTipoRepository.findById(id).get();
        extraccionTipo.setNombre(request.getNombre());
        extraccionTipo.setDescripcion(request.getDescripcion());
        extraccionTipoRepository.save(extraccionTipo);
        return extraccionTipo;
    }

    public Boolean deleteExtraccionTipo(Long id){
        try{
            extraccionTipoRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
