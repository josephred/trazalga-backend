package com.trazalga.api.controllers;

import com.trazalga.api.dto.geo.CoberturaCoordenadasDto;
import com.trazalga.api.dto.geo.ResumenImportacionDto;
import com.trazalga.api.services.geo.ImportarCoordenadasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * Controlador administrativo para carga masiva y auditoría de coordenadas GPS (TG.1).
 */
@RestController
@RequestMapping("/api/admin/coordenadas")
@Tag(name = "Coordenadas y Georreferenciación", description = "Endpoints de carga y cobertura geográfica para administradores")
@RequiredArgsConstructor
@Slf4j
public class CoordenadasAdminController {

    private final ImportarCoordenadasService importarCoordenadasService;

    @PostMapping(value = "/importar", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "*/*"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Carga verificable de planilla CSV de coordenadas (TG.1)")
    public ResponseEntity<ResumenImportacionDto> importarCoordenadas(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String csvBody,
            @RequestParam(value = "aplicar", defaultValue = "false") boolean aplicar,
            @RequestParam(value = "forzar", defaultValue = "false") boolean forzar,
            Principal principal
    ) {
        String usuario = (principal != null) ? principal.getName() : "ADMIN";
        InputStream is;

        try {
            if (file != null && !file.isEmpty()) {
                is = file.getInputStream();
            } else if (csvBody != null && !csvBody.isBlank()) {
                is = new ByteArrayInputStream(csvBody.getBytes(StandardCharsets.UTF_8));
            } else {
                return ResponseEntity.badRequest().build();
            }

            ResumenImportacionDto resumen = importarCoordenadasService.procesarPlanilla(is, aplicar, forzar, usuario);
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            log.error("Error al importar coordenadas: {}", e.getMessage(), e);
            throw new RuntimeException("Error en importación de coordenadas: " + e.getMessage(), e);
        }
    }

    @GetMapping("/cobertura")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtiene estadísticas de cobertura georreferencial por región y global (TG.1)")
    public ResponseEntity<CoberturaCoordenadasDto> getCobertura() {
        return ResponseEntity.ok(importarCoordenadasService.obtenerCobertura());
    }
}
