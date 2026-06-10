package com.trazalga.api.services;

import com.trazalga.api.models.HistorialUbicacionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.HistorialUbicacionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.List;

@Service
public class HistorialUbicacionService {

    @Autowired
    private HistorialUbicacionRepository repository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    public HistorialUbicacionModel saveLocation(Long usuarioId, HistorialUbicacionModel model) {
        UsuarioModel usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        model.setUsuario(usuario);
        if (model.getFechaRegistro() == null) {
            model.setFechaRegistro(new Date());
        }
        return repository.save(model);
    }

    public List<HistorialUbicacionModel> saveLocations(Long usuarioId, List<HistorialUbicacionModel> models) {
        UsuarioModel usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
        for (HistorialUbicacionModel model : models) {
            model.setUsuario(usuario);
            if (model.getFechaRegistro() == null) {
                model.setFechaRegistro(new Date());
            }
        }
        return repository.saveAll(models);
    }

    public List<HistorialUbicacionModel> getTrayecto(Long usuarioId, Date desde, Date hasta) {
        return repository.findByUsuarioIdAndFechaRegistroBetweenOrderByFechaRegistroAsc(usuarioId, desde, hasta);
    }
}
