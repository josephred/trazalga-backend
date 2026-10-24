package com.trazalga.api.controllers;

import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO;
import com.trazalga.api.dto.FichaTrazabilidadDTO;
import com.trazalga.api.dto.ResultadoFolioDTO;
import com.trazalga.api.services.ConsultaFolioService;
import com.trazalga.api.services.ConsultaPatenteService;
import com.trazalga.api.services.FichaTrazabilidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador para consultas puntuales de trazabilidad, búsqueda por folio (T2.2),
 * ficha de trazabilidad (T2.3) y consulta de fiscalizador por patente (TN.4).
 */
@RestController
@RequestMapping({"/api/consultas", "/consultas"})
@Slf4j
public class ConsultaTrazabilidadController {

    private final ConsultaFolioService consultaFolioService;
    private final FichaTrazabilidadService fichaTrazabilidadService;
    private ConsultaPatenteService consultaPatenteService;

    public ConsultaTrazabilidadController(
            ConsultaFolioService consultaFolioService,
            FichaTrazabilidadService fichaTrazabilidadService) {
        this.consultaFolioService = consultaFolioService;
        this.fichaTrazabilidadService = fichaTrazabilidadService;
    }

    @Autowired(required = false)
    public void setConsultaPatenteService(ConsultaPatenteService consultaPatenteService) {
        this.consultaPatenteService = consultaPatenteService;
    }

    /**
     * Búsqueda puntual por folio, patente, embarcación o documento tributario.
     */
    @GetMapping("/folio")
    public ResponseEntity<List<ResultadoFolioDTO>> buscarPorFolio(
            @RequestParam(name = "q", required = false) String q,
            Authentication authentication) {
        String userRut = authentication != null ? authentication.getName() : "ANONIMO";
        List<ResultadoFolioDTO> resultados = consultaFolioService.buscar(q, userRut);
        return ResponseEntity.ok(resultados);
    }

    /**
     * Ficha de trazabilidad consolidada para un lote a partir de cualquiera de sus declaraciones (T2.3).
     */
    @GetMapping("/ficha/{tipo}/{id}")
    public ResponseEntity<FichaTrazabilidadDTO> getFicha(
            @PathVariable String tipo,
            @PathVariable Long id) {
        FichaTrazabilidadDTO ficha = fichaTrazabilidadService.obtenerFicha(tipo, id);
        return ResponseEntity.ok(ficha);
    }

    /**
     * TN.4 · Consulta exhaustiva de patente para FISCALIZADOR, ADMIN o AUDITOR.
     */
    @GetMapping("/patentes/{patente}")
    public ResponseEntity<ConsultaPatenteFiscalizadorDTO> getPatenteFiscalizador(
            @PathVariable String patente,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        String userRut = authentication != null ? authentication.getName() : "ANONIMO";
        if (consultaPatenteService == null) {
            return ResponseEntity.notFound().build();
        }
        ConsultaPatenteFiscalizadorDTO dto = consultaPatenteService.consultarFiscalizador(patente, desde, hasta, userRut, null);
        return ResponseEntity.ok(dto);
    }
}
