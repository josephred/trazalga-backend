package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;

@Service
public class DeclaracionComercializadorService {
    
    @Autowired
    IDeclaracionComercializadorRepository declaracionComercializadorRepository;
    
    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializador(){
        return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAll();
        // return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAllOrderByCampoEspecificoDesc();
    }

    public DeclaracionComercializadorModel saveDeclaracionComercializador(DeclaracionComercializadorModel declaracionComercializadorModel){
        return declaracionComercializadorRepository.save(declaracionComercializadorModel);
    }

    public Optional<DeclaracionComercializadorModel> getById(Long id){
        return declaracionComercializadorRepository.findById(id);
    }

    public DeclaracionComercializadorModel updateById(DeclaracionComercializadorModel request, Long id){
        DeclaracionComercializadorModel declaracionComercializadorModel = declaracionComercializadorRepository.findById(id).get();
        declaracionComercializadorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionComercializadorModel.setFolioDeclaracionAC(request.getFolioDeclaracionAC());
        declaracionComercializadorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionComercializadorModel.setHora(request.getHora());
        declaracionComercializadorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionComercializadorModel.setNombreComercializador(request.getNombreComercializador());
        declaracionComercializadorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionComercializadorModel.setEspecie(request.getEspecie());
        declaracionComercializadorModel.setComposicion(request.getComposicion());
        declaracionComercializadorModel.setEstadoHumedad(request.getEstadoHumedad());
        declaracionComercializadorModel.setCantidad(request.getCantidad());
        declaracionComercializadorModel.setDocumentoTributarioOrigen(request.getDocumentoTributarioOrigen());
        declaracionComercializadorModel.setDocumentoTributarioDestino(request.getDocumentoTributarioDestino());
        declaracionComercializadorModel.setVehiculoTransporte(request.getVehiculoTransporte());
        declaracionComercializadorModel.setChoferTransporte(request.getChoferTransporte());
        declaracionComercializadorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        declaracionComercializadorModel.setNombreDestinatario(request.getNombreDestinatario());
        declaracionComercializadorModel.setPatente(request.getPatente());

        declaracionComercializadorRepository.save(declaracionComercializadorModel);
        return declaracionComercializadorModel;
    }

    public Boolean deleteDeclaracionComercializador(Long id){
        try{
            declaracionComercializadorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
}
