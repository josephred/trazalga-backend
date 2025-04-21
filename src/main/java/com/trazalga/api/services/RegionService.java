package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.IRegionRepository;

@Service
public class RegionService {

    @Autowired
    IRegionRepository regionRepository;

    public ArrayList<RegionModel> getRegions(){
        return (ArrayList<RegionModel>) regionRepository.findAll();
    } 

    public RegionModel saveRegion(RegionModel region){
        return regionRepository.save(region);
    }

    public Optional<RegionModel> getById(Long id){
        return regionRepository.findById(id);
    }

    public RegionModel updateById(RegionModel request,Long id){
        RegionModel region = regionRepository.findById(id).get();
        region.setNombre(request.getNombre());
        regionRepository.save(region);
        return region;
    }

    public Boolean deleteRegion(Long id){
        try{
            regionRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
