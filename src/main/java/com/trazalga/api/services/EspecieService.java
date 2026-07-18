package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.repositories.IEspecieRepository;

@Service
public class EspecieService {

    @Autowired
    IEspecieRepository especieRepository;

    public ArrayList<EspecieModel> getEspecies(boolean incluirOcultas){
        if (incluirOcultas) {
            return (ArrayList<EspecieModel>) especieRepository.findAll();
        }
        return new ArrayList<>(especieRepository.findVisibles());
    } 

    public EspecieModel saveEspecie(EspecieModel especie){
        return especieRepository.save(especie);
    }

    public Optional<EspecieModel> getById(Long id){
        return especieRepository.findById(id);
    }

    public EspecieModel updateById(EspecieModel request,Long id){
        EspecieModel especie = especieRepository.findById(id).get();
        especie.setNombre(request.getNombre());
        especie.setDescripcion(request.getDescripcion());
        especieRepository.save(especie);
        return especie;
    }

    public Boolean deleteEspecie(Long id){
        try{
            especieRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }

    public EspecieModel setActivo(Long id, boolean activo) {
        EspecieModel x = especieRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe id " + id));
        x.setActivo(activo);
        return especieRepository.save(x);
    }
}
