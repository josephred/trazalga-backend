package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class DeclaracionPlantaDestinoService {

    @Autowired
    private IDeclaracionPlantaDestinoRepository repository;

    public List<DeclaracionPlantaDestinoModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaDeclaracionDestinoDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaDestinoModel> getById(Long id) {
        return repository.findById(id);
    }

    public DeclaracionPlantaDestinoModel save(DeclaracionPlantaDestinoModel declaracion) {
        if (declaracion.getFolioDeclaracionDestino() == null || declaracion.getFolioDeclaracionDestino().isEmpty()) {
            declaracion.setFolioDeclaracionDestino(generarFolioAbastecimientoPlanta());
        }

        if (declaracion.getFechaDeclaracionDestino() == null) {
            declaracion.setFechaDeclaracionDestino(new Date());
        }

        return repository.save(declaracion);
    }

    public List<DeclaracionPlantaDestinoModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(
            Long usuarioDestinatarioId) {
        return repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    private String generarFolioAbastecimientoPlanta() {
        String prefijo = "DPLA"; // He cambiado el prefijo para reflejar "Destino Planta"
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findTopByOrderByIdDescFolioDeclaracionAbastecimientoPlanta();

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return "%s-%s-%06d".formatted(prefijo, anio, correlativo);
    }
}