package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;

@Service
public class DeclaracionPlantaAbastecimientoService {

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository repository;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired
    private IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    private IDeclaracionAreaRepository areaRepository;

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
        sanearComposicion(declaracion);
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
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        
        List<Long> comercializadorIds = SeleccionTokens.idsParaTipo(sel, "COMERCIALIZADOR");
        if (!comercializadorIds.isEmpty()) {
            for (DeclaracionComercializadorModel c : comercializadorRepository.findAllById(comercializadorIds)) {
                if (c.getUsuarioDestinatario() != null && c.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && c.getDeclaracionDestinatario() == null) {
                    c.setDeclaracionDestinatario(consumidaPorId);
                    c.setConsumidaPorTipo(tipo);
                    comercializadorRepository.save(c);
                }
            }
        }

        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        if (!recolectorIds.isEmpty()) {
            for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                if (r.getUsuarioDestinatario() != null && r.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && r.getDeclaracionDestinatario() == null) {
                    r.setDeclaracionDestinatario(consumidaPorId);
                    r.setConsumidaPorTipo(tipo);
                    recolectorRepository.save(r);
                }
            }
        }

        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        if (!armadorIds.isEmpty()) {
            for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                if (a.getUsuarioDestinatario() != null && a.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && a.getDeclaracionDestinatario() == null) {
                    a.setDeclaracionDestinatario(consumidaPorId);
                    a.setConsumidaPorTipo(tipo);
                    armadorRepository.save(a);
                }
            }
        }

        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");
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

    private void sanearComposicion(DeclaracionPlantaAbastecimientoModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }
}