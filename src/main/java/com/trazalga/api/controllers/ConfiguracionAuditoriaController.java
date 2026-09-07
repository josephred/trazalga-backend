package com.trazalga.api.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.ConfiguracionAuditoriaModel;
import com.trazalga.api.services.ConfiguracionAuditoriaService;

@RestController
@RequestMapping({"/configuracionauditoria", "/api/configuracion-auditoria"})
public class ConfiguracionAuditoriaController {

    @Autowired
    private ConfiguracionAuditoriaService service;

    @GetMapping
    public List<ConfiguracionAuditoriaModel> getAll(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String entidadId) {
        if (entidad != null && entidadId != null) {
            return service.getByEntidadAndId(entidad, entidadId);
        }
        if (entidad != null) {
            return service.getByEntidad(entidad);
        }
        return service.getAll();
    }
}
