package com.trazalga.api.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.TareaProgramadaEjecucionModel;
import com.trazalga.api.services.TareaProgramadaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador de administración técnica y monitoreo del sistema (TA.6).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administración del Sistema", description = "Endpoints de administración técnica y observabilidad")
public class AdminController {

    @Autowired
    private TareaProgramadaService tareaProgramadaService;

    @GetMapping("/tareas-programadas")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtiene el estado de la última ejecución de cada tarea programada (TA.6)")
    public ResponseEntity<List<TareaProgramadaEjecucionModel>> getUltimoEstadoTareas() {
        return ResponseEntity.ok(tareaProgramadaService.listarUltimoEstado());
    }

    @GetMapping("/tareas-programadas/historial")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtiene el historial reciente de ejecuciones de tareas programadas (TA.6)")
    public ResponseEntity<List<TareaProgramadaEjecucionModel>> getHistorialTareas() {
        return ResponseEntity.ok(tareaProgramadaService.listarHistorial());
    }
}
