package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.services.trazabilidad.SeleccionTokens;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;

@Service
public class DeclaracionComercializadorService {
    
    @Autowired
    IDeclaracionComercializadorRepository declaracionComercializadorRepository;

    @Autowired
    IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired
    IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    IDeclaracionAreaRepository areaRepository;
    
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

    @Transactional
    public DeclaracionComercializadorModel saveDeclaracionComercializador(DeclaracionComercializadorModel declaracionComercializadorModel){
        sanearComposicion(declaracionComercializadorModel);
        DeclaracionComercializadorModel saved = declaracionComercializadorRepository.save(declaracionComercializadorModel);
        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(), 
                saved.getId(), 
                "COMERCIALIZADOR", 
                saved.getUsuario().getId()
            );
        }
        return saved;
    }

    public Optional<DeclaracionComercializadorModel> getById(Long id){
        return declaracionComercializadorRepository.findById(id);
    }

    public List<DeclaracionComercializadorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionComercializadorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    @Transactional
    public DeclaracionComercializadorModel updateById(DeclaracionComercializadorModel request, Long id){
        DeclaracionComercializadorModel declaracionComercializadorModel = declaracionComercializadorRepository.findById(id).get();
        if (declaracionComercializadorModel.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
        String oldSeleccionadas = declaracionComercializadorModel.getDeclaracionesSeleccionadas();

        declaracionComercializadorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionComercializadorModel.setFolioDesembarqueAc(request.getFolioDesembarqueAc());
        declaracionComercializadorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionComercializadorModel.setFechaTraslado(request.getFechaTraslado());
        declaracionComercializadorModel.setHora(request.getHora());
        declaracionComercializadorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionComercializadorModel.setNombreComercializador(request.getNombreComercializador());
        //declaracionComercializadorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionComercializadorModel.setLatitud(request.getLatitud());
        declaracionComercializadorModel.setLongitud(request.getLongitud()); 
        declaracionComercializadorModel.setEspecie(request.getEspecie());
        declaracionComercializadorModel.setComposicion(request.getComposicion());
        declaracionComercializadorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionComercializadorModel.setHumedadHigrometro(request.getHumedadHigrometro());
        declaracionComercializadorModel.setCantidad(request.getCantidad());
        declaracionComercializadorModel.setDocumentoTributarioOrigenTipo(request.getDocumentoTributarioOrigenTipo());
        declaracionComercializadorModel.setDocumentoTributarioOrigenNumero(request.getDocumentoTributarioOrigenNumero());
        declaracionComercializadorModel.setDocumentoTributarioOrigenFecha(request.getDocumentoTributarioOrigenFecha());
        declaracionComercializadorModel.setDocumentoTributarioDestinoTipo(request.getDocumentoTributarioDestinoTipo());
        declaracionComercializadorModel.setDocumentoTributarioDestinoNumero(request.getDocumentoTributarioDestinoNumero());
        declaracionComercializadorModel.setDocumentoTributarioDestinoFecha(request.getDocumentoTributarioDestinoFecha());
        declaracionComercializadorModel.setVehiculoTransporte(request.getVehiculoTransporte());
        declaracionComercializadorModel.setChoferTransporte(request.getChoferTransporte());
        declaracionComercializadorModel.setRutChofer(request.getRutChofer());
        declaracionComercializadorModel.setPlacaPatente(request.getPlacaPatente());
        declaracionComercializadorModel.setPlacaPatenteCarro(request.getPlacaPatenteCarro());
        declaracionComercializadorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        // declaracionComercializadorModel.setNombreDestinatario(request.getNombreDestinatario());
        declaracionComercializadorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracionComercializadorModel.setPatente(request.getPatente());
        declaracionComercializadorModel.setDeclaracionesSeleccionadas(request.getDeclaracionesSeleccionadas());
        
        sanearComposicion(declaracionComercializadorModel);
        declaracionComercializadorRepository.save(declaracionComercializadorModel);

        if (oldSeleccionadas != null && !oldSeleccionadas.isEmpty()) {
            liberarDeclaracionesConsumidas(oldSeleccionadas, id);
        }
        if (request.getDeclaracionesSeleccionadas() != null && !request.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                request.getDeclaracionesSeleccionadas(), 
                id, 
                "COMERCIALIZADOR", 
                declaracionComercializadorModel.getUsuario().getId()
            );
        }

        return declaracionComercializadorModel;
    }

    @Transactional
    public Boolean deleteDeclaracionComercializador(Long id){
        DeclaracionComercializadorModel model = declaracionComercializadorRepository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try{
            if (model != null && model.getDeclaracionesSeleccionadas() != null && !model.getDeclaracionesSeleccionadas().isEmpty()) {
                liberarDeclaracionesConsumidas(model.getDeclaracionesSeleccionadas(), id);
            }
            declaracionComercializadorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
    
    public String getLastFolioOrigen() {
        List<String> folios = declaracionComercializadorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    public String getLastFolioDesembarqueAc() {
        List<String> folios = declaracionComercializadorRepository.findLastFolioDesembarqueAc();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");

        if (!recolectorIds.isEmpty()) {
            for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                if (r.getUsuarioDestinatario() != null && r.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && r.getDeclaracionDestinatario() == null) {
                    r.setDeclaracionDestinatario(consumidaPorId);
                    r.setConsumidaPorTipo(tipo);
                    recolectorRepository.save(r);
                }
            }
        }

        if (!armadorIds.isEmpty()) {
            for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                if (a.getUsuarioDestinatario() != null && a.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && a.getDeclaracionDestinatario() == null) {
                    a.setDeclaracionDestinatario(consumidaPorId);
                    a.setConsumidaPorTipo(tipo);
                    armadorRepository.save(a);
                }
            }
        }

        if (!areaIds.isEmpty()) {
            for (DeclaracionAreaModel ar : areaRepository.findAllById(areaIds)) {
                if (ar.getUsuarioDestinatario() != null && ar.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && ar.getDeclaracionDestinatario() == null) {
                    ar.setDeclaracionDestinatario(consumidaPorId);
                    ar.setConsumidaPorTipo(tipo);
                    areaRepository.save(ar);
                }
            }
        }
    }

    private void liberarDeclaracionesConsumidas(String idsCSV, Long consumidaPorId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");

        if (!recolectorIds.isEmpty()) {
            for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                if (consumidaPorId.equals(r.getDeclaracionDestinatario())) {
                    r.setDeclaracionDestinatario(null);
                    r.setConsumidaPorTipo(null);
                    recolectorRepository.save(r);
                }
            }
        }

        if (!armadorIds.isEmpty()) {
            for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                if (consumidaPorId.equals(a.getDeclaracionDestinatario())) {
                    a.setDeclaracionDestinatario(null);
                    a.setConsumidaPorTipo(null);
                    armadorRepository.save(a);
                }
            }
        }

        if (!areaIds.isEmpty()) {
            for (DeclaracionAreaModel ar : areaRepository.findAllById(areaIds)) {
                if (consumidaPorId.equals(ar.getDeclaracionDestinatario())) {
                    ar.setDeclaracionDestinatario(null);
                    ar.setConsumidaPorTipo(null);
                    areaRepository.save(ar);
                }
            }
        }
    }

    private void sanearComposicion(DeclaracionComercializadorModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }
}
