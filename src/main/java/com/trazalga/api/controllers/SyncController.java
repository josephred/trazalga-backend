package com.trazalga.api.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.sync.SyncResult;
import com.trazalga.api.services.sync.SernapescaSyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Herramienta de poblamiento de datos maestros desde el API de Sernapesca.
 * Operaciones idempotentes: pueden ejecutarse varias veces sin duplicar datos.
 */
@RestController
@RequestMapping("/sync/sernapesca")
@Tag(name = "Sincronización Sernapesca", description = "Pobla datos maestros desde data-api.sernapesca.cl")
public class SyncController {

    private final SernapescaSyncService syncService;

    public SyncController(SernapescaSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/all")
    @Operation(summary = "Pobla todas las tablas disponibles desde Sernapesca")
    public List<SyncResult> syncAll() {
        return syncService.syncAll();
    }

    @PostMapping("/regiones")
    @Operation(summary = "Pobla region, comuna y caleta (jerárquico)")
    public List<SyncResult> syncRegiones() {
        return syncService.syncRegionesComunasCaletas();
    }

    @PostMapping("/tipos-extraccion")
    @Operation(summary = "Pobla extraccion_tipo (métodos de recolección)")
    public SyncResult syncTiposExtraccion() {
        return syncService.syncTiposExtraccion();
    }

    @PostMapping("/especies")
    @Operation(summary = "Pobla especie (autorizadas para recolección de orilla)")
    public SyncResult syncEspecies() {
        return syncService.syncEspecies();
    }

    @PostMapping("/embarcaciones")
    @Operation(summary = "Pobla embarcacion (itera por región)")
    public SyncResult syncEmbarcaciones() {
        return syncService.syncEmbarcaciones();
    }

    @PostMapping("/buzos")
    @Operation(summary = "Pobla buzo (recolectores de orilla, itera por región)")
    public SyncResult syncBuzos() {
        return syncService.syncBuzos();
    }

    @PostMapping("/amerb")
    @Operation(summary = "Pobla amerb (áreas de manejo, itera por región)")
    public SyncResult syncAmerbs() {
        return syncService.syncAmerbs();
    }

    @PostMapping("/plantas")
    @Operation(summary = "Pobla plantas (destinatarios tipo planta, itera por región)")
    public SyncResult syncPlantas() {
        return syncService.syncPlantas();
    }
}
