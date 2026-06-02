package com.trazalga.api.controllers;

import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.services.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<?> generateReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaFin,
            @RequestParam Integer tipoReporte,
            @RequestParam(required = false) String rut) {
        
        try {
            System.out.println("Generando reporte: " + tipoReporte + " desde " + fechaInicio + " hasta " + fechaFin);
            List<ReportDTO> report = reportService.getReport(fechaInicio, fechaFin, tipoReporte, rut);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            System.err.println("Error generando reporte: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/indicadores-recolector")
    public ResponseEntity<?> getIndicadoresRecolector() {
        try {
            return ResponseEntity.ok(reportService.getIndicadoresRecolector());
        } catch (Exception e) {
            System.err.println("Error obteniendo indicadores de recolector: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }
}
