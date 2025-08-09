package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class DeclaracionPlantaProduccionService {

    @Autowired
    private IDeclaracionPlantaProduccionRepository repository;

    public List<DeclaracionPlantaProduccionModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaProduccionDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaProduccionModel> getById(Long id) {
        return repository.findById(id);
    }

    public DeclaracionPlantaProduccionModel save(DeclaracionPlantaProduccionModel declaracion) {
        if (declaracion.getFolioDeclaracionPpla() == null || declaracion.getFolioDeclaracionPpla().isEmpty()) {
            declaracion.setFolioDeclaracionPpla(generarFolioPpla());
        }

        if (declaracion.getFechaProduccion() == null) {
            declaracion.setFechaProduccion(new Date());
        }

        return repository.save(declaracion);
    }

    public List<DeclaracionPlantaProduccionModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    private String generarFolioPpla() {
        String prefijo = "PPLA";
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findLastFolioDeclaracionPpla().stream().findFirst().orElse(null);

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return String.format("%s-%s-%06d", prefijo, anio, correlativo);
    }
}