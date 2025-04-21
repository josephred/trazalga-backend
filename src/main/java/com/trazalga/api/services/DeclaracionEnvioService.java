package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionEnvioModel;
import com.trazalga.api.repositories.IDeclaracionEnvioRepository;

@Service
public class DeclaracionEnvioService {

    @Autowired
    IDeclaracionEnvioRepository declaracionEnvioRepository;

    public ArrayList<DeclaracionEnvioModel> getDeclaracionEnvios(){
        return (ArrayList<DeclaracionEnvioModel>) declaracionEnvioRepository.findAll();
    }

    public DeclaracionEnvioModel saveDeclaracionEnvio(DeclaracionEnvioModel declaracionEnvioModel){
        return declaracionEnvioRepository.save(declaracionEnvioModel);
    }

    public Optional<DeclaracionEnvioModel> getById(Long id){
        return declaracionEnvioRepository.findById(id);
    }

    public DeclaracionEnvioModel updateById(DeclaracionEnvioModel request, Long id){
        DeclaracionEnvioModel declaracionEnvioModel = declaracionEnvioRepository.findById(id).get();
        declaracionEnvioModel.setIdOrigen(request.getIdOrigen());
        declaracionEnvioModel.setIdDestino(request.getIdDestino());
        declaracionEnvioRepository.save(declaracionEnvioModel);
        return declaracionEnvioModel;
    }

    public Boolean deleteDeclaracionEnvio(Long id){
        try{
            declaracionEnvioRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
}
