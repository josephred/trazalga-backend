package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.repositories.ICaletaRepository;

@Service
public class CaletaService {

    @Autowired
    ICaletaRepository caletaRepository;

    public ArrayList<CaletaModel> getCaletas(){
        return (ArrayList<CaletaModel>) caletaRepository.findAll();
    } 

    public java.util.List<CaletaModel> getByComuna(Long comunaId){
        return caletaRepository.findByComunaId(comunaId);
    }

    public java.util.List<CaletaModel> getByRegion(Long regionId){
        return caletaRepository.findByRegionId(regionId);
    }

    public CaletaModel saveCaleta(CaletaModel caleta){
        return caletaRepository.save(caleta);
    }

    public Optional<CaletaModel> getById(Long id){
        return caletaRepository.findById(id);
    }

    public CaletaModel updateById(CaletaModel request,Long id){
        CaletaModel caleta = caletaRepository.findById(id).get();
        caleta.setNombre(request.getNombre());
        caletaRepository.save(caleta);
        return caleta;
    }

    public Boolean deleteCaleta(Long id){
        try{
            caletaRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
