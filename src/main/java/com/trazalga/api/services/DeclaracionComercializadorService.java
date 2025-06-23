package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;
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

    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializadorIdUsuario(Long id){
        return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAllByUsuarioId(id);
    }

    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializadorUsuarioIdDesc(Long id){
        return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAllByUsuarioIdOrderByFechaDeclaracionDesc(id);
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
        declaracionComercializadorModel.setFolioDesembarqueAc(request.getFolioDesembarqueAc());
        declaracionComercializadorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionComercializadorModel.setHora(request.getHora());
        declaracionComercializadorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionComercializadorModel.setNombreComercializador(request.getNombreComercializador());
        declaracionComercializadorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionComercializadorModel.setEspecie(request.getEspecie());
        declaracionComercializadorModel.setComposicion(request.getComposicion());
        declaracionComercializadorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionComercializadorModel.setCantidad(request.getCantidad());
        declaracionComercializadorModel.setDocumentoTributarioOrigenTipo(request.getDocumentoTributarioOrigenTipo());
        declaracionComercializadorModel.setDocumentoTributarioOrigenNumero(request.getDocumentoTributarioOrigenNumero());
        declaracionComercializadorModel.setDocumentoTributarioOrigenFecha(request.getDocumentoTributarioOrigenFecha());
        declaracionComercializadorModel.setDocumentoTributarioDestinoTipo(request.getDocumentoTributarioDestinoTipo());
        declaracionComercializadorModel.setDocumentoTributarioDestinoNumero(request.getDocumentoTributarioDestinoNumero());
        declaracionComercializadorModel.setDocumentoTributarioDestinoFecha(request.getDocumentoTributarioDestinoFecha());
        declaracionComercializadorModel.setVehiculoTransporte(request.getVehiculoTransporte());
        declaracionComercializadorModel.setChoferTransporte(request.getChoferTransporte());
        declaracionComercializadorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        // declaracionComercializadorModel.setNombreDestinatario(request.getNombreDestinatario());
        declaracionComercializadorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracionComercializadorModel.setPatente(request.getPatente());
        declaracionComercializadorModel.setDeclaracionesSeleccionadas(request.getDeclaracionesSeleccionadas());

        declaracionComercializadorRepository.save(declaracionComercializadorModel);
        return declaracionComercializadorModel;
    }

    public Boolean deleteDeclaracionComercializador(Long id){
        try{
            declaracionComercializadorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
    
    public String getLastFolioOrigen() {
        List<String> folios = declaracionComercializadorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.get(0);
    }

    public String getLastFolioDesembarqueAc() {
        List<String> folios = declaracionComercializadorRepository.findLastFolioDesembarqueAc();
        return folios.isEmpty() ? null : folios.get(0);
    }

}
