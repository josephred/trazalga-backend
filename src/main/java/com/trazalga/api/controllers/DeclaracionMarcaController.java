package com.trazalga.api.controllers;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.services.DeclaracionMarcaService;

@RestController
@RequestMapping({"/declaracionmarca", "/api/declaracion-marcas"})
public class DeclaracionMarcaController {

    @Autowired
    private DeclaracionMarcaService service;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IUsuarioRepository usuarioRepository;

    @GetMapping
    public List<DeclaracionMarcaModel> getAll(
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) Boolean resuelta,
            @RequestParam(required = false, defaultValue = "false") boolean soloPendientes,
            @RequestParam(required = false) String estadoGestion,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {

        Boolean estadoResuelta = soloPendientes ? Boolean.FALSE : resuelta;
        Date endOfDay = ajustarFinDeDia(endDate);

        if (marca != null || estadoResuelta != null || estadoGestion != null || tipo != null || startDate != null || endDate != null) {
            return service.findConFiltros(marca, estadoResuelta, estadoGestion, tipo, startDate, endOfDay);
        }

        return service.getAll();
    }

    @GetMapping("/resumen")
    public Map<String, Object> getResumen() {
        return service.getResumen();
    }

    @GetMapping("/{tipo}/{id}")
    public List<DeclaracionMarcaModel> getByDeclaracion(
            @PathVariable String tipo,
            @PathVariable Long id) {
        return service.getByDeclaracion(tipo.toUpperCase(), id);
    }

    @PutMapping("/{id}/resolver")
    public ResponseEntity<?> resolver(
            @PathVariable Long id,
            @RequestBody(required = false) com.trazalga.api.dto.ResolucionMarcaDTO request,
            java.security.Principal principal) {
        Long usuarioId = extractUserId(principal);
        if (request == null) {
            return service.resolverMarca(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }

        try {
            DeclaracionMarcaModel resuelta = service.resolverMarca(
                    id, request.getResolucionTipo(), request.getObservacion(), usuarioId);
            return ResponseEntity.ok(resuelta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/derivar-citacion")
    public ResponseEntity<?> derivarACitacion(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            java.security.Principal principal) {
        String numeroCitacion = body != null ? (body.get("numeroCitacion") != null ? body.get("numeroCitacion") : body.get("numero")) : null;
        if (numeroCitacion == null || numeroCitacion.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "numeroCitacion es obligatorio"));
        }
        Long usuarioId = extractUserId(principal);
        try {
            DeclaracionMarcaModel derivada = service.derivarACitacion(id, numeroCitacion.trim(), usuarioId);
            return ResponseEntity.ok(derivada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/declaracion/{tipo}/{id}/derivar-citacion")
    public ResponseEntity<?> derivarACitacionPorDeclaracion(
            @PathVariable String tipo,
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            java.security.Principal principal) {
        String numeroCitacion = body != null ? (body.get("numeroCitacion") != null ? body.get("numeroCitacion") : body.get("numero")) : null;
        if (numeroCitacion == null || numeroCitacion.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "numeroCitacion es obligatorio"));
        }
        Long usuarioId = extractUserId(principal);
        try {
            DeclaracionMarcaModel derivada = service.derivarACitacionPorDeclaracion(tipo, id, numeroCitacion.trim(), usuarioId);
            return ResponseEntity.ok(derivada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/reabrir")
    public ResponseEntity<DeclaracionMarcaModel> reabrir(@PathVariable Long id) {
        return service.reabrirMarca(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Long extractUserId(java.security.Principal principal) {
        if (principal == null || usuarioRepository == null) return null;
        try {
            return usuarioRepository.findByRut(principal.getName())
                    .map(com.trazalga.api.models.UsuarioModel::getId)
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Date ajustarFinDeDia(Date date) {
        if (date == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }
}
