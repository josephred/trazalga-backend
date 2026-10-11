package com.trazalga.api.controllers;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.trazalga.api.dto.FiltroHallazgos;
import com.trazalga.api.dto.HallazgoDetalleDTO;
import com.trazalga.api.repositories.HallazgoQueryRepository;

/**
 * Controlador de consulta unificada para la Consola de Hallazgos (TA.3).
 * Restringido a perfiles de auditoría y fiscalización: ADMIN, FISCALIZADOR, AUDITOR.
 */
@RestController
@RequestMapping({"/api/hallazgos", "/hallazgos"})
@PreAuthorize("hasAnyRole('ADMIN', 'FISCALIZADOR', 'AUDITOR')")
public class HallazgoController {

    @Autowired
    private HallazgoQueryRepository queryRepository;

    @GetMapping
    public ResponseEntity<Page<HallazgoDetalleDTO>> buscarHallazgos(
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String estadoGestion,
            @RequestParam(required = false) Boolean resuelta,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long reglaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date hasta,
            @RequestParam(required = false) String rpa,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Date finDeDia = ajustarFinDeDia(hasta);

        FiltroHallazgos filtro = FiltroHallazgos.builder()
                .marca(marca)
                .estadoGestion(estadoGestion)
                .resuelta(resuelta)
                .tipo(tipo)
                .reglaId(reglaId)
                .desde(desde)
                .hasta(finDeDia)
                .rpa(rpa)
                .build();

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<HallazgoDetalleDTO> resultado = queryRepository.buscar(filtro, pageable);

        return ResponseEntity.ok(resultado);
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
