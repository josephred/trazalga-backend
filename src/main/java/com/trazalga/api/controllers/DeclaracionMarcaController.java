package com.trazalga.api.controllers;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.services.DeclaracionMarcaService;

@RestController
@RequestMapping({"/declaracionmarca", "/api/declaracion-marcas"})
public class DeclaracionMarcaController {

    @Autowired
    private DeclaracionMarcaService service;

    @GetMapping
    public List<DeclaracionMarcaModel> getAll(
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) Boolean resuelta,
            @RequestParam(required = false, defaultValue = "false") boolean soloPendientes,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {

        Boolean estadoResuelta = soloPendientes ? Boolean.FALSE : resuelta;
        Date endOfDay = ajustarFinDeDia(endDate);

        if (marca != null || estadoResuelta != null || tipo != null || startDate != null || endDate != null) {
            return service.findConFiltros(marca, estadoResuelta, tipo, startDate, endOfDay);
        }

        return service.getAll();
    }

    @GetMapping("/resumen")
    public Map<String, Object> getResumen() {
        return service.getResumen();
    }

    @GetMapping("/{tipo}/{id}")
    public List<DeclaracionMarcaModel> getByDeclaracion(
            @PathVariable String tipo,
            @PathVariable Long id) {
        return service.getByDeclaracion(tipo.toUpperCase(), id);
    }

    @PutMapping("/{id}/resolver")
    public ResponseEntity<DeclaracionMarcaModel> resolver(@PathVariable Long id) {
        return service.resolverMarca(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reabrir")
    public ResponseEntity<DeclaracionMarcaModel> reabrir(@PathVariable Long id) {
        return service.reabrirMarca(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Date ajustarFinDeDia(Date date) {
        if (date == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTime();
    }
}
