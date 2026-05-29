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
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;

@Service
public class DeclaracionPlantaProduccionService {

    @Autowired
    private IDeclaracionPlantaProduccionRepository repository;

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;

    public List<DeclaracionPlantaProduccionModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaProduccionDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaProduccionModel> getById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public DeclaracionPlantaProduccionModel save(DeclaracionPlantaProduccionModel declaracion) {
        if (declaracion.getFolioDeclaracionPpla() == null || declaracion.getFolioDeclaracionPpla().isEmpty()) {
            declaracion.setFolioDeclaracionPpla(generarFolioPpla());
        }

        if (declaracion.getFechaProduccion() == null) {
            declaracion.setFechaProduccion(new Date());
        }

        DeclaracionPlantaProduccionModel saved = repository.save(declaracion);

        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(), 
                saved.getId(), 
                "PLANTA_PRODUCCION", 
                saved.getUsuario().getId()
            );
        }

        return saved;
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
        return "%s-%s-%06d".formatted(prefijo, anio, correlativo);
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        List<Long> ids = Arrays.stream(idsCSV.split(","))
                               .map(String::trim)
                               .filter(s -> !s.isEmpty())
                               .map(Long::valueOf)
                               .collect(Collectors.toList());
        
        if (ids.isEmpty()) return;

        List<DeclaracionPlantaAbastecimientoModel> abastecimientos = abastecimientoRepository.findAllById(ids);
        for (DeclaracionPlantaAbastecimientoModel a : abastecimientos) {
            // Asumiendo que la validación de usuario_destinatario no aplica o es diferente aquí,
            // pero si PlantaProduccion es el destinatario, se valida igual.
            // Para mantener consistencia con los demás servicios:
            if (a.getDeclaracionDestinatario() == null) {
                // En abastecimiento puede que el usuarioDestinatario sea nulo o no se use de la misma forma,
                // si se usa, añadir: && a.getUsuarioDestinatario() != null && a.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId)
                a.setDeclaracionDestinatario(consumidaPorId);
                a.setConsumidaPorTipo(tipo);
                abastecimientoRepository.save(a);
            }
        }
    }
}