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

    @Autowired
    private com.trazalga.api.services.UsuarioVersionService usuarioVersionService;

    @Autowired
    private com.trazalga.api.services.indicadores.RecalculoRendimientoFinalService recalculoRendimientoFinalService;

    @Autowired
    private com.trazalga.api.services.declaraciones.CorregirFechasDeclaracionesService corregirFechasDeclaracionesService;

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

    @GetMapping("/versiones-app")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtiene el resumen y listado de versiones de app móvil activas por usuario y plantas (TP.1)")
    public ResponseEntity<com.trazalga.api.dto.ResumenVersionesAppDTO> getVersionesApp(
            @org.springframework.web.bind.annotation.RequestParam(value = "perfil", required = false) String perfil) {
        return ResponseEntity.ok(usuarioVersionService.listarUsuariosVersion(perfil));
    }

    @org.springframework.web.bind.annotation.PostMapping("/rendimiento/recalcular-final")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recálculo histórico de captura_origen_kg, captura_origen_completa y rendimiento_final_pct sin crear marcas ni bloquear destinos (QR.4 / D36)")
    public ResponseEntity<java.util.Map<String, Object>> recalcularRendimientoFinal(
            @org.springframework.web.bind.annotation.RequestParam(value = "aplicar", defaultValue = "true") boolean aplicar) {
        return ResponseEntity.ok(recalculoRendimientoFinalService.recalcularHistorico(aplicar));
    }

    @org.springframework.web.bind.annotation.PostMapping("/declaraciones/corregir-fechas")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Simulación y corrección controlada de fechas desfasadas por UTC usando gps_capturado_en (ZT.3 / D50)")
    public ResponseEntity<java.util.Map<String, Object>> corregirFechasDeclaraciones(
            @org.springframework.web.bind.annotation.RequestParam(value = "aplicar", defaultValue = "false") boolean aplicar) {
        return ResponseEntity.ok(aplicar
                ? corregirFechasDeclaracionesService.aplicarCorreccion()
                : corregirFechasDeclaracionesService.simularCorreccion());
    }
}
