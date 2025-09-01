package com.trazalga.api.services;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;

@Service
public class DeclaracionAreaService {

    @Autowired
    private IDeclaracionAreaRepository declaracionAreaRepository;

    public List<DeclaracionAreaModel> getAllDeclaraciones() {
        return declaracionAreaRepository.findAll();
    }

    public List<DeclaracionAreaModel> getDeclaracionesByUsuario(Long usuarioId) {
        return declaracionAreaRepository.findAllByUsuarioIdOrderByFechaDeclaracionDesc(usuarioId);
    }
    
    // NUEVO MÉTODO AÑADIDO
    public List<DeclaracionAreaModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionAreaRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    public DeclaracionAreaModel saveDeclaracion(DeclaracionAreaModel declaracion) {
        return declaracionAreaRepository.save(declaracion);
    }

    public Optional<DeclaracionAreaModel> getById(Long id) {
        return declaracionAreaRepository.findById(id);
    }

    public DeclaracionAreaModel updateDeclaracion(Long id, DeclaracionAreaModel request) {
        DeclaracionAreaModel declaracion = declaracionAreaRepository.findById(id).orElseThrow();
        
        declaracion.setFolioOrigen(request.getFolioOrigen());
        declaracion.setFolioDesembarqueAmerb(request.getFolioDesembarqueAmerb());
        declaracion.setFechaExtraccion(request.getFechaExtraccion());
        declaracion.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracion.setHora(request.getHora());
        declaracion.setAmerb(request.getAmerb());
        declaracion.setEspecie(request.getEspecie());
        declaracion.setCaptura(request.getCaptura());
        declaracion.setDesembarque(request.getDesembarque());
        declaracion.setTipoDestinatario(request.getTipoDestinatario());
        declaracion.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracion.setComposicion(request.getComposicion());
        declaracion.setHumedadEstado(request.getHumedadEstado());
        declaracion.setLatitud(request.getLatitud());
        declaracion.setLongitud(request.getLongitud());
        // Asegurarse de actualizar también el nuevo campo si es necesario
        declaracion.setDeclaracionDestinatario(request.getDeclaracionDestinatario());

        return declaracionAreaRepository.save(declaracion);
    }

    public boolean deleteDeclaracion(Long id) {
        try {
            declaracionAreaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getLastFolioOrigen() {
        List<String> folios = declaracionAreaRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.get(0);
    }

    public String getLastFolioDesembarqueAmerb() {
        List<String> folios = declaracionAreaRepository.findLastFolioDesembarqueAmerb();
        return folios.isEmpty() ? null : folios.get(0);
    }
}