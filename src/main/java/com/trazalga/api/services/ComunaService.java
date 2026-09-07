package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.repositories.IComunaRepository;

@Service
public class ComunaService {

    @Autowired
    IComunaRepository comunaRepository;

    public ArrayList<ComunaModel> getComunas(){
        return (ArrayList<ComunaModel>) comunaRepository.findAll();
    } 

    public List<ComunaModel> getByRegion(Long regionId){
        return comunaRepository.findByRegionId(regionId);
    }

    public List<ComunaModel> getByProvincia(Long provinciaId){
        return comunaRepository.findByProvinciaId(provinciaId);
    }

    public ComunaModel saveComuna(ComunaModel comuna){
        return comunaRepository.save(comuna);
    }

    public Optional<ComunaModel> getById(Long id){
        return comunaRepository.findById(id);
    }

    public ComunaModel updateById(ComunaModel request,Long id){
        ComunaModel comuna = comunaRepository.findById(id).get();
        comuna.setNombre(request.getNombre());
        comuna.setRegion(request.getRegion());
        comunaRepository.save(comuna);
        return comuna;
    }

    public Boolean deleteComuna(Long id){
        try{
            comunaRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
