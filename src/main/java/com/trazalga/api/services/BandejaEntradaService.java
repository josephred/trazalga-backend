package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.trazalga.api.dto.DeclaracionResumenDTO;
import com.trazalga.api.repositories.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BandejaEntradaService {

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepo;
    
    @Autowired
    private IDeclaracionArmadorRepository armadorRepo;

    @Autowired
    private IDeclaracionAreaRepository areaRepo;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepo;

    public List<DeclaracionResumenDTO> getDeclaracionesPorDestinatario(Long destinatarioId) {
        
        // 1. Obtener las listas de cada tipo de declaración
        List<DeclaracionResumenDTO> declaracionesRecolector = recolectorRepo.findByUsuarioDestinatarioId(destinatarioId)
                .stream()
                .map(DeclaracionResumenDTO::new) // Mapea cada objeto al DTO
                .collect(Collectors.toList());

        List<DeclaracionResumenDTO> declaracionesArmador = armadorRepo.findByUsuarioDestinatarioId(destinatarioId)
                .stream()
                .map(DeclaracionResumenDTO::new)
                .collect(Collectors.toList());

        List<DeclaracionResumenDTO> declaracionesArea = areaRepo.findByUsuarioDestinatarioId(destinatarioId)
                .stream()
                .map(DeclaracionResumenDTO::new)
                .collect(Collectors.toList());

        List<DeclaracionResumenDTO> declaracionesComercializador = comercializadorRepo.findByUsuarioDestinatarioId(destinatarioId)
                .stream()
                .map(DeclaracionResumenDTO::new)
                .collect(Collectors.toList());

        // 2. Unificar todas las listas en una sola
        List<DeclaracionResumenDTO> bandejaCompleta = new ArrayList<>();
        bandejaCompleta.addAll(declaracionesRecolector);
        bandejaCompleta.addAll(declaracionesArmador);
        bandejaCompleta.addAll(declaracionesArea);
        bandejaCompleta.addAll(declaracionesComercializador);

        // 3. Ordenar la lista final por fecha de declaración, de la más reciente a la más antigua
        bandejaCompleta.sort(Comparator.comparing(DeclaracionResumenDTO::getFechaDeclaracion).reversed());

        return bandejaCompleta;
    }
}