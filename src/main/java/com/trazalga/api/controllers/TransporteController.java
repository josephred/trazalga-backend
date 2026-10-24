package com.trazalga.api.controllers;

import com.trazalga.api.dto.CandidatoFusionDTO;
import com.trazalga.api.dto.MisFrecuentesDTO;
import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.VehiculoModel;
import com.trazalga.api.repositories.ITransportistaAliasRepository;
import com.trazalga.api.repositories.ITransportistaRepository;
import com.trazalga.api.repositories.IVehiculoRepository;
import com.trazalga.api.services.FusionTransportistaService;
import com.trazalga.api.services.ReporteTransporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/transporte", "/transporte"})
@Tag(name = "Nómina de Transporte", description = "Consultas de nómina de choferes, vehículos y sugerencias de autocompletado")
public class TransporteController {

    @Autowired
    private ITransportistaRepository transportistaRepository;

    @Autowired
    private ITransportistaAliasRepository aliasRepository;

    @Autowired
    private IVehiculoRepository vehiculoRepository;

    @Autowired
    private FusionTransportistaService fusionService;

    @Autowired
    private ReporteTransporteService reporteTransporteService;

    @GetMapping("/transportistas")
    @Operation(summary = "TT.5 · Lista transportistas con filtros opcionales de búsqueda y estado")
    public ResponseEntity<List<TransportistaModel>> listarTransportistas(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "estado", required = false) String estado) {

        List<TransportistaModel> list;
        if (q != null && !q.isBlank()) {
            list = transportistaRepository.buscarPorTermino(q.trim());
        } else if (estado != null && !estado.isBlank()) {
            list = transportistaRepository.findByEstado(estado.trim().toUpperCase());
        } else {
            list = transportistaRepository.findAllActivos();
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/transportistas/{id}")
    @Operation(summary = "TT.5 · Obtiene el detalle de un transportista con sus alias")
    public ResponseEntity<Map<String, Object>> obtenerTransportista(@PathVariable Long id) {
        TransportistaModel t = transportistaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el transportista #" + id));

        List<TransportistaAliasModel> aliases = aliasRepository.findByTransportistaIdOrderByVecesDesc(id);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("transportista", t);
        resp.put("aliases", aliases);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/transportistas/candidatos")
    @Operation(summary = "TT.4 · Obtiene pares de transportistas candidatos a fusión")
    public ResponseEntity<List<CandidatoFusionDTO>> obtenerCandidatos(
            @RequestParam(name = "similitudMin", required = false) Double similitudMin) {
        List<CandidatoFusionDTO> candidatos = fusionService.obtenerCandidatos(similitudMin);
        return ResponseEntity.ok(candidatos);
    }

    @GetMapping("/vehiculos")
    @Operation(summary = "TT.5 · Lista vehículos registrados con filtro opcional")
    public ResponseEntity<List<VehiculoModel>> listarVehiculos(@RequestParam(name = "q", required = false) String q) {
        List<VehiculoModel> list;
        if (q != null && !q.isBlank()) {
            list = vehiculoRepository.buscarPorPatente(q.trim());
        } else {
            list = vehiculoRepository.findAll();
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/vehiculos/{id}")
    @Operation(summary = "TT.5 · Detalle de vehículo")
    public ResponseEntity<VehiculoModel> obtenerVehiculo(@PathVariable Long id) {
        VehiculoModel v = vehiculoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el vehículo #" + id));
        return ResponseEntity.ok(v);
    }

    @GetMapping("/mis-frecuentes")
    @Operation(summary = "TT.7 · Sugerencias frecuentes de choferes y vehículos para el emisor autenticado")
    public ResponseEntity<MisFrecuentesDTO> obtenerMisFrecuentes(Authentication auth) {
        String userRut = auth != null ? auth.getName() : null;
        MisFrecuentesDTO dto = reporteTransporteService.obtenerMisFrecuentes(userRut);
        return ResponseEntity.ok(dto);
    }
}
