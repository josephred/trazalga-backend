package com.trazalga.api.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.services.indicadores.SubreporteHuiroPaloService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tarjeta "Subreporte Huiro Palo" del panel (refinamiento 23-10-2026, punto 3).
 * Mismo acceso que el resto de los reportes (ADMIN, FISCALIZADOR, AUDITOR).
 */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
@Slf4j
public class SubreporteHuiroPaloController {

    private final SubreporteHuiroPaloService subreporteHuiroPaloService;

    @GetMapping("/subreporte-huiro-palo")
    public ResponseEntity<?> getSubreporteHuiroPalo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return ResponseEntity.ok(subreporteHuiroPaloService.calcular(startDate, endDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error obteniendo el Subreporte Huiro Palo", e);
            return ResponseEntity.status(500).body(Map.of("message", "Error interno al calcular el Subreporte Huiro Palo."));
        }
    }
}
