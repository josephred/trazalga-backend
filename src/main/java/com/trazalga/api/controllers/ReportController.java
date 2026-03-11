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
@CrossOrigin(origins = "*") // Ajustar según sea necesario
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<List<ReportDTO>> generateReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaFin,
            @RequestParam Integer tipoReporte,
            @RequestParam(required = false) String rut) {
        
        List<ReportDTO> report = reportService.getReport(fechaInicio, fechaFin, tipoReporte, rut);
        return ResponseEntity.ok(report);
    }
}
