package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.repositories.IBuzoRepository;


@Service
public class BuzoService {

    // private final BuzoRepository buzoRepository;
    @Autowired
    IBuzoRepository buzoRepository;

       public ArrayList<BuzoModel> getBuzos(){
        return (ArrayList<BuzoModel>) buzoRepository.findAll();
    } 

    public BuzoModel saveBuzo(BuzoModel buzo){
        return buzoRepository.save(buzo);
    }

    public Optional<BuzoModel> getById(Long id){
        return buzoRepository.findById(id);
    }

    public BuzoModel updateById(BuzoModel request,Long id){
        BuzoModel buzo = buzoRepository.findById(id).get();
        buzo.setNombre(request.getNombre());
        buzoRepository.save(buzo);
        return buzo;
    }

    public Boolean deleteBuzo(Long id){
        try{
            buzoRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }

}