package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

@Service
public class DeclaracionRecolectorService {
    
    @Autowired
    IDeclaracionRecolectorRepository declaracionRecolectorRepository;
    
    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolector(){
        return (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAll();
        // return (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAllOrderByCampoEspecificoDesc();
    }

    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolectorIdUsuario(Long id){
        return (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAllByUsuarioId(id);
    }

    public List<DeclaracionRecolectorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionRecolectorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }


    public DeclaracionRecolectorModel saveDeclaracionRecolector(DeclaracionRecolectorModel declaracionRecolectorModel){
        return declaracionRecolectorRepository.save(declaracionRecolectorModel);
    }

    public Optional<DeclaracionRecolectorModel> getById(Long id){
        return declaracionRecolectorRepository.findById(id);
    }

    public DeclaracionRecolectorModel updateById(DeclaracionRecolectorModel request, Long id){
        DeclaracionRecolectorModel declaracionRecolectorModel = declaracionRecolectorRepository.findById(id).get();
        declaracionRecolectorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionRecolectorModel.setFolioDesembarqueRo(request.getFolioDesembarqueRo());
        declaracionRecolectorModel.setFechaExtraccion(request.getFechaExtraccion());
        declaracionRecolectorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionRecolectorModel.setHora(request.getHora());
        declaracionRecolectorModel.setNombre(request.getNombre());
        declaracionRecolectorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionRecolectorModel.setVaradero(request.getVaradero());        
        declaracionRecolectorModel.setCaleta(request.getCaleta());
        declaracionRecolectorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionRecolectorModel.setEspecie(request.getEspecie());
        declaracionRecolectorModel.setComuna(request.getComuna());
        declaracionRecolectorModel.setExtraccionTipo(request.getExtraccionTipo());
        declaracionRecolectorModel.setComposicion(request.getComposicion());
        declaracionRecolectorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionRecolectorModel.setHumedad(request.getHumedad());
        declaracionRecolectorModel.setDesembarque(request.getDesembarque());
        declaracionRecolectorModel.setCaptura(request.getCaptura());
        declaracionRecolectorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        declaracionRecolectorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        
        declaracionRecolectorRepository.save(declaracionRecolectorModel);
        return declaracionRecolectorModel;
    }

    public Boolean deleteDeclaracionRecolector(Long id){
        try{
            declaracionRecolectorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }

    public String getLastFolioOrigen() {
        List<String> folios = declaracionRecolectorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.get(0);
    }

    public String getLastFolioDesembarqueRo() {
        List<String> folios = declaracionRecolectorRepository.findLastFolioDesembarqueRo();
        return folios.isEmpty() ? null : folios.get(0);
    }


}
