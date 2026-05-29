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
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;

@Service
public class DeclaracionPlantaAbastecimientoService {

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository repository;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    public List<DeclaracionPlantaAbastecimientoModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaIngresoPlantaDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaAbastecimientoModel> getById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public DeclaracionPlantaAbastecimientoModel save(DeclaracionPlantaAbastecimientoModel declaracion) {
        if (declaracion.getFolioDeclaracionAPla() == null || declaracion.getFolioDeclaracionAPla().isEmpty()) {
            declaracion.setFolioDeclaracionAPla(generarFolio());
        }
        if (declaracion.getFechaIngresoPlanta() == null) {
            declaracion.setFechaIngresoPlanta(new Date());
        }
        DeclaracionPlantaAbastecimientoModel saved = repository.save(declaracion);

        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(), 
                saved.getId(), 
                "PLANTA_ABASTECIMIENTO", 
                saved.getUsuario().getId()
            );
        }

        return saved;
    }

    public List<DeclaracionPlantaAbastecimientoModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    private String generarFolio() {
        String prefijo = "DAPLA"; // Declaracion Abastecimiento Planta
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findLastFolioDeclaracionAPla().stream().findFirst().orElse(null);

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return "%s-%s-%06d".formatted(prefijo, anio, correlativo);
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        List<Long> ids = Arrays.stream(idsCSV.split(","))
                               .map(String::trim)
                               .filter(s -> !s.isEmpty())
                               .map(Long::valueOf)
                               .collect(Collectors.toList());
        
        if (ids.isEmpty()) return;

        List<DeclaracionComercializadorModel> comercializadores = comercializadorRepository.findAllById(ids);
        for (DeclaracionComercializadorModel c : comercializadores) {
            if (c.getUsuarioDestinatario() != null && c.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && c.getDeclaracionDestinatario() == null) {
                c.setDeclaracionDestinatario(consumidaPorId);
                c.setConsumidaPorTipo(tipo);
                comercializadorRepository.save(c);
            }
        }
    }
}