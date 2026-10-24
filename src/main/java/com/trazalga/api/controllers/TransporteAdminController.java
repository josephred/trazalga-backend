package com.trazalga.api.controllers;

import com.trazalga.api.dto.FusionSolicitudDTO;
import com.trazalga.api.dto.InformeReconstruccionDTO;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ITransportistaRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.FusionTransportistaService;
import com.trazalga.api.services.ReconstruccionTransporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping({"/api/admin/transporte", "/admin/transporte"})
@Tag(name = "Administración de Transporte", description = "Operaciones administrativas de nómina de transportistas y reconstrucción histórica")
public class TransporteAdminController {

    @Autowired
    private ReconstruccionTransporteService reconstruccionService;

    @Autowired
    private FusionTransportistaService fusionService;

    @Autowired
    private ITransportistaRepository transportistaRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @PostMapping("/reconstruir")
    @Operation(summary = "TT.3 · Reconstruye de forma idempotente la nómina histórica de choferes y vehículos")
    public ResponseEntity<InformeReconstruccionDTO> reconstruir() {
        InformeReconstruccionDTO informe = reconstruccionService.reconstruir();
        return ResponseEntity.ok(informe);
    }

    @PostMapping("/transportistas/{destinoId}/fusionar")
    @Operation(summary = "TT.4 · Fusiona un transportista origen en el destino, reasignando declaraciones y alias")
    public ResponseEntity<TransportistaModel> fusionar(
            @PathVariable Long destinoId,
            @RequestBody FusionSolicitudDTO solicitud,
            Authentication auth) {

        Long usuarioId = 0L;
        if (auth != null && auth.getName() != null) {
            UsuarioModel u = usuarioRepository.findByRut(auth.getName()).orElse(null);
            if (u != null) {
                usuarioId = u.getId();
            }
        }

        TransportistaModel resultado = fusionService.fusionar(solicitud.getOrigenId(), destinoId, solicitud.getMotivo(), usuarioId);
        return ResponseEntity.ok(resultado);
    }

    @PutMapping("/transportistas/{id}")
    @Operation(summary = "TT.5 · Actualiza el nombre canónico o confirma un transportista en estado REVISAR")
    public ResponseEntity<TransportistaModel> actualizarTransportista(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        TransportistaModel t = transportistaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el transportista #" + id));

        if (body.containsKey("nombreCanonico")) {
            String nuevoNombre = (String) body.get("nombreCanonico");
            if (nuevoNombre != null && !nuevoNombre.isBlank()) {
                t.setNombreCanonico(nuevoNombre.trim());
            }
        }

        if (body.containsKey("confirmar") && Boolean.TRUE.equals(body.get("confirmar"))) {
            t.setEstado(TransportistaModel.ESTADO_ACTIVO);
        }

        t.setUpdatedAt(new Date());
        TransportistaModel guardado = transportistaRepository.save(t);
        return ResponseEntity.ok(guardado);
    }
}
