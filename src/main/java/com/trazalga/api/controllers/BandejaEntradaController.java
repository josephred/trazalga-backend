package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.trazalga.api.dto.DeclaracionResumenDTO;
import com.trazalga.api.services.BandejaEntradaService;
import java.util.List;

@RestController
@RequestMapping("/bandeja-entrada") // Un nombre claro para el nuevo recurso
public class BandejaEntradaController {

    @Autowired
    private BandejaEntradaService bandejaEntradaService;

    @GetMapping("/destinatario/{destinatarioId}")
    public List<DeclaracionResumenDTO> getBandejaPorDestinatario(@PathVariable("destinatarioId") Long destinatarioId) {
        return this.bandejaEntradaService.getDeclaracionesPorDestinatario(destinatarioId);
    }
}