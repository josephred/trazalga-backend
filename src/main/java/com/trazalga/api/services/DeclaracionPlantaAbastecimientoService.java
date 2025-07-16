package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class DeclaracionPlantaAbastecimientoService {

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository repository;

    public List<DeclaracionPlantaAbastecimientoModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaIngresoPlantaDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaAbastecimientoModel> getById(Long id) {
        return repository.findById(id);
    }

    public DeclaracionPlantaAbastecimientoModel save(DeclaracionPlantaAbastecimientoModel declaracion) {
        if (declaracion.getFolioDeclaracionAPla() == null || declaracion.getFolioDeclaracionAPla().isEmpty()) {
            declaracion.setFolioDeclaracionAPla(generarFolioAPla());
        }

        if (declaracion.getFechaIngresoPlanta() == null) {
            declaracion.setFechaIngresoPlanta(new Date());
        }

        return repository.save(declaracion);
    }

    private String generarFolioAPla() {
        String prefijo = "A-PLA";
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findLastFolioDeclaracionAPla().stream().findFirst().orElse(null);

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return String.format("%s-%s-%06d", prefijo, anio, correlativo);
    }
}