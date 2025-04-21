package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;

@Service
public class DeclaracionArmadorService {
    
    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;
    
    /**
     * Obtiene todas las declaraciones de armador
     */
    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmador() {
        return (ArrayList<DeclaracionArmadorModel>) declaracionArmadorRepository.findAll();
    }

    /**
     * Obtiene todas las declaraciones de armador por usuario ID
     */
    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmadorIdUsuario(Long id) {
        return (ArrayList<DeclaracionArmadorModel>) declaracionArmadorRepository.findAllByUsuarioId(id);
    }

    /**
     * Obtiene declaraciones donde usuarioDestinatario es NULL
     */
    public List<DeclaracionArmadorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionArmadorRepository.findByUsuarioDestinatarioIdAndUsuarioDestinatarioIsNull(usuarioDestinatarioId);
    }

    /**
     * Guarda una declaración de armador
     */
    public DeclaracionArmadorModel saveDeclaracionArmador(DeclaracionArmadorModel declaracionArmadorModel) {
        return declaracionArmadorRepository.save(declaracionArmadorModel);
    }

    /**
     * Obtiene una declaración de armador por ID
     */
    public Optional<DeclaracionArmadorModel> getById(Long id) {
        return declaracionArmadorRepository.findById(id);
    }

    /**
     * Actualiza una declaración de armador por ID
     */
    public DeclaracionArmadorModel updateById(DeclaracionArmadorModel request, Long id) {
        DeclaracionArmadorModel declaracionArmadorModel = declaracionArmadorRepository.findById(id).orElse(null);
        if (declaracionArmadorModel == null) {
            return null; // Manejo de error si el ID no existe
        }

        declaracionArmadorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionArmadorModel.setFolioDesembarqueDa(request.getFolioDesembarqueDa());
        declaracionArmadorModel.setFechaExtraccion(request.getFechaExtraccion());
        declaracionArmadorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionArmadorModel.setHora(request.getHora());
        declaracionArmadorModel.setEmbarcacion(request.getEmbarcacion());
        declaracionArmadorModel.setBuzo(request.getBuzo());
        declaracionArmadorModel.setDesembarque(request.getDesembarque());
        declaracionArmadorModel.setCaptura(request.getCaptura());
        // declaracionArmadorModel.setTipoDestinatario(request.getTipoDestinatario());
        declaracionArmadorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracionArmadorModel.setCaleta(request.getCaleta());
        declaracionArmadorModel.setEspecie(request.getEspecie());
        declaracionArmadorModel.setComposicion(request.getComposicion());
        declaracionArmadorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionArmadorModel.setLatitud(request.getLatitud());
        declaracionArmadorModel.setLongitud(request.getLongitud());

        return declaracionArmadorRepository.save(declaracionArmadorModel);
    }

    /**
     * Elimina una declaración de armador por ID
     */
    public Boolean deleteDeclaracionArmador(Long id) {
        try {
            declaracionArmadorRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtiene el último folioOrigen registrado
     */
    public String getLastFolioOrigen() {
        List<String> folios = declaracionArmadorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.get(0);
    }

    /**
     * Obtiene el último folioDesembarqueDa registrado
     */
    public String getLastFolioDesembarqueDa() {
        List<String> folios = declaracionArmadorRepository.findLastFolioDesembarqueDa();
        return folios.isEmpty() ? null : folios.get(0);
    }
}
