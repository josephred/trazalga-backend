package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.repositories.IHumedadEstadoRepository;

@Service
public class HumedadEstadoService {

    @Autowired
    IHumedadEstadoRepository humedadEstadoRepository;

    public ArrayList<HumedadEstadoModel> getHumedadEstados(){
        return (ArrayList<HumedadEstadoModel>) humedadEstadoRepository.findAll();
    } 

    public HumedadEstadoModel saveHumedadEstado(HumedadEstadoModel humedadEstado){
        return humedadEstadoRepository.save(humedadEstado);
    }

    public Optional<HumedadEstadoModel> getById(Long id){
        return humedadEstadoRepository.findById(id);
    }

    public HumedadEstadoModel updateById(HumedadEstadoModel request,Long id){
        HumedadEstadoModel humedadEstado = humedadEstadoRepository.findById(id).get();
        humedadEstado.setNombre(request.getNombre());
        humedadEstado.setRangoInicio(request.getRangoInicio());
        humedadEstado.setRangoFin(request.getRangoFin());
        humedadEstadoRepository.save(humedadEstado);
        return humedadEstado;
    }

    public Boolean deleteHumedadEstado(Long id){
        try{
            humedadEstadoRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
