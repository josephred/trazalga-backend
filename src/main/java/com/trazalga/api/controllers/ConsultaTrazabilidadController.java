package com.trazalga.api.controllers;

import com.trazalga.api.dto.FichaTrazabilidadDTO;
import com.trazalga.api.dto.ResultadoFolioDTO;
import com.trazalga.api.services.ConsultaFolioService;
import com.trazalga.api.services.FichaTrazabilidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador para consultas puntuales de trazabilidad, búsqueda por folio (T2.2)
 * y ficha de trazabilidad (T2.3).
 */
@RestController
@RequestMapping({"/api/consultas", "/consultas"})
@RequiredArgsConstructor
@Slf4j
public class ConsultaTrazabilidadController {

    private final ConsultaFolioService consultaFolioService;
    private final FichaTrazabilidadService fichaTrazabilidadService;

    /**
     * Búsqueda puntual por folio, patente, embarcación o documento tributario.
     *
     * @param q              Texto o folio a buscar
     * @param authentication Información de autenticación del usuario (JWT)
     * @return Lista de resultados ordenados del más reciente al más antiguo
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
     *
     * @param tipo Tipo de declaración consultada (RECOLECTOR, ARMADOR, AREA, COMERCIALIZADOR, PLANTA_ABASTECIMIENTO)
     * @param id   ID de la declaración
     * @return Ficha con bloques de Origen, Comercializador, Planta y Alertas
     */
    @GetMapping("/ficha/{tipo}/{id}")
    public ResponseEntity<FichaTrazabilidadDTO> getFicha(
            @PathVariable String tipo,
            @PathVariable Long id) {
        FichaTrazabilidadDTO ficha = fichaTrazabilidadService.obtenerFicha(tipo, id);
        return ResponseEntity.ok(ficha);
    }
}
