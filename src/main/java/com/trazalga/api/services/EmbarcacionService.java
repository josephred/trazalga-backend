package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.repositories.IEmbarcacionRepository;


@Service
public class EmbarcacionService {

    // private final EmbarcacionRepository embarcacionRepository;
    @Autowired
    IEmbarcacionRepository embarcacionRepository;

       public ArrayList<EmbarcacionModel> getEmbarcaciones(){
        return (ArrayList<EmbarcacionModel>) embarcacionRepository.findAll();
    } 

    public EmbarcacionModel saveEmbarcacion(EmbarcacionModel embarcacion){
        return embarcacionRepository.save(embarcacion);
    }

    public Optional<EmbarcacionModel> getById(Long id){
        return embarcacionRepository.findById(id);
    }

    public EmbarcacionModel updateById(EmbarcacionModel request,Long id){
        EmbarcacionModel embarcacion = embarcacionRepository.findById(id).get();
        embarcacion.setNombre(request.getNombre());
        embarcacionRepository.save(embarcacion);
        return embarcacion;
    }

    public Boolean deleteEmbarcacion(Long id){
        try{
            embarcacionRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
    // public EmbarcacionDTO guardarEmbarcacion(EmbarcacionDTO dto) {
    //     Embarcacion embarcacion = Embarcacion.builder()
    //             .nombre(dto.getNombre())
    //             .codigo(dto.getCodigo())
    //             .build();
    //     embarcacion = embarcacionRepository.save(embarcacion);
    //     dto.setId(embarcacion.getId());
    //     return dto;
    // }

    // public List<EmbarcacionDTO> listarEmbarcaciones() {
    //     return embarcacionRepository.findAll()
    //             .stream()
    //             .map(e -> new EmbarcacionDTO(e.getId(), e.getNombre(), e.getCodigo()))
    //             .collect(Collectors.toList());
    // }

    // public Optional<EmbarcacionDTO> obtenerPorCodigo(String codigo) {
    //     return embarcacionRepository.findByCodigo(codigo)
    //             .map(e -> new EmbarcacionDTO(e.getId(), e.getNombre(), e.getCodigo()));
    // }

    // public void eliminarEmbarcacion(Long id) {
    //     embarcacionRepository.deleteById(id);
    // }
}