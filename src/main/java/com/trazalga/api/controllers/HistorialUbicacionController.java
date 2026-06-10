package com.trazalga.api.controllers;

import com.trazalga.api.models.HistorialUbicacionModel;
import com.trazalga.api.services.HistorialUbicacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/historial-ubicacion")
public class HistorialUbicacionController {

    @Autowired
    private HistorialUbicacionService service;

    @PostMapping("/usuario/{usuarioId}")
    public ResponseEntity<HistorialUbicacionModel> createLocation(
            @PathVariable Long usuarioId,
            @RequestBody HistorialUbicacionModel location) {
        return new ResponseEntity<>(service.saveLocation(usuarioId, location), HttpStatus.CREATED);
    }

    @PostMapping("/usuario/{usuarioId}/bulk")
    public ResponseEntity<List<HistorialUbicacionModel>> createLocations(
            @PathVariable Long usuarioId,
            @RequestBody List<HistorialUbicacionModel> locations) {
        return new ResponseEntity<>(service.saveLocations(usuarioId, locations), HttpStatus.CREATED);
    }

    @GetMapping("/usuario/{usuarioId}/trayecto")
    public ResponseEntity<List<HistorialUbicacionModel>> getTrayecto(
            @PathVariable Long usuarioId,
            @RequestParam("desde") Long desde,
            @RequestParam("hasta") Long hasta) {
        Date desdeDate = new Date(desde);
        Date hastaDate = new Date(hasta);
        List<HistorialUbicacionModel> trayecto = service.getTrayecto(usuarioId, desdeDate, hastaDate);
        return ResponseEntity.ok(trayecto);
    }
}
