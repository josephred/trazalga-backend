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
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;

@Service
public class DeclaracionPlantaDestinoService {

    @Autowired
    private IDeclaracionPlantaDestinoRepository repository;

    @Autowired
    private IDeclaracionPlantaProduccionRepository produccionRepository;

    public List<DeclaracionPlantaDestinoModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaDeclaracionDestinoDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaDestinoModel> getById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public DeclaracionPlantaDestinoModel save(DeclaracionPlantaDestinoModel declaracion) {
        if (declaracion.getFolioDeclaracionDestino() == null || declaracion.getFolioDeclaracionDestino().isEmpty()) {
            declaracion.setFolioDeclaracionDestino(generarFolioAbastecimientoPlanta());
        }

        if (declaracion.getFechaDeclaracionDestino() == null) {
            declaracion.setFechaDeclaracionDestino(new Date());
        }

        DeclaracionPlantaDestinoModel saved = repository.save(declaracion);

        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(), 
                saved.getId(), 
                "PLANTA_DESTINO", 
                saved.getUsuario().getId()
            );
        }

        return saved;
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

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        List<Long> ids = Arrays.stream(idsCSV.split(","))
                               .map(String::trim)
                               .filter(s -> !s.isEmpty())
                               .map(Long::valueOf)
                               .collect(Collectors.toList());
        
        if (ids.isEmpty()) return;

        List<DeclaracionPlantaProduccionModel> producciones = produccionRepository.findAllById(ids);
        for (DeclaracionPlantaProduccionModel p : producciones) {
            if (p.getDeclaracionDestinatario() == null) {
                // Validación de usuarioDestinatario si aplica:
                // && p.getUsuarioDestinatario() != null && p.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId)
                p.setDeclaracionDestinatario(consumidaPorId);
                p.setConsumidaPorTipo(tipo);
                produccionRepository.save(p);
            }
        }
    }
}