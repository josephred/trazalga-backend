package com.trazalga.api.controllers;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.services.ConsultaPatenteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({"/api/public/patentes", "/public/patentes"})
@Tag(name = "Consulta Pública de Patentes", description = "Servicio público de verificación de vigencia de traslado de algas por patente de camión o carro")
public class ConsultaPatentePublicaController {

    @Autowired
    private ConsultaPatenteService patenteService;


    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> patenteVacia() {
        return ResponseEntity.badRequest().body(Map.of("error", "Patente no válida"));
    }

    @GetMapping("/{p1}/{p2}")
    public ResponseEntity<Map<String, Object>> patenteConBarra() {
        return ResponseEntity.badRequest().body(Map.of("error", "Patente no válida"));
    }

    @GetMapping("/{patente}")
    @Operation(summary = "Consulta el último movimiento y vigencia de traslado para una patente de camión o carro (sin datos personales)")
    public ResponseEntity<Map<String, Object>> consultarPatente(
            @PathVariable String patente,
            HttpServletRequest request) {
        if ("comerciantes".equalsIgnoreCase(patente) || "camiones-comerciantes".equalsIgnoreCase(patente)) {
            return ResponseEntity.notFound().build();
        }
        String normalizada = ConsultaPatenteService.normalizarPatente(patente);
        if (!ConsultaPatenteService.esPatenteValida(normalizada)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Patente no válida"));
        }
        Map<String, Object> resultado = patenteService.consultar(normalizada, request);
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/{patente}/denuncia")
    @Operation(summary = "Registra intención ciudadana de denuncia sin solicitar datos personales (ZC.5 / D54)")
    public ResponseEntity<?> registrarIntencionDenuncia(
            @PathVariable String patente,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String normalizada = ConsultaPatenteService.normalizarPatente(patente);
        if (!ConsultaPatenteService.esPatenteValida(normalizada)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Patente no válida"));
        }
        String canalRaw = body != null ? body.get("canal") : null;
        String canal = canalRaw != null ? canalRaw.trim().toUpperCase(java.util.Locale.ROOT) : "";
        if (!"FONO".equals(canal) && !"WHATSAPP".equals(canal)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Canal no válido"));
        }
        patenteService.registrarIntencionDenuncia(normalizada, canal, request);
        return ResponseEntity.noContent().build();
    }
}
