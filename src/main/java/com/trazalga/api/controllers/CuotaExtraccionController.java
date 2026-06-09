package com.trazalga.api.controllers;

import java.math.BigDecimal; // Importante: Importar BigDecimal
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.services.CuotaExtraccionService;
import java.util.List;

@RestController
@RequestMapping("/api/cuotas")
public class CuotaExtraccionController {

    @Autowired
    CuotaExtraccionService cuotaService;

    @GetMapping("/dashboard-diario")
    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) Date endDate,
            @RequestParam(required = false, defaultValue = "DIARIO") String periodo,
            @RequestParam(required = false, defaultValue = "RECOLECTOR") String perfil) {
        return cuotaService.getControlCuotasDiarioGlobal(startDate, endDate, periodo, perfil);
    }

    @GetMapping
    public ArrayList<CuotaExtraccionModel> getAll() {
        return (ArrayList<CuotaExtraccionModel>) cuotaService.getAll();
    }

    @PostMapping
    public CuotaExtraccionModel create(@RequestBody CuotaExtraccionModel cuota) {
        return cuotaService.save(cuota);
    }

    @GetMapping(path = "/{id}")
    public Optional<CuotaExtraccionModel> getById(@PathVariable("id") Long id) {
        return cuotaService.getById(id);
    }

    @PutMapping(path = "/{id}")
    public CuotaExtraccionModel update(@RequestBody CuotaExtraccionModel request, @PathVariable("id") Long id) {
        request.setId(id);
        return cuotaService.save(request);
    }

    @DeleteMapping(path = "/{id}")
    public String delete(@PathVariable("id") Long id) {
        boolean ok = cuotaService.delete(id);
        if (ok) return "Cuota id " + id + " eliminada";
        return "ERROR al eliminar cuota";
    }

    // DTO simple para validación de declaración
    public static class ValidateDeclarationRequest {
        public Long usuarioId;
        public String perfil; // "RECOLECTOR" o "ARMADOR"
        public Long especieId;
        public Date fechaDeclaracion;
        public Double cantidadKg; // JSON envía números como Double por defecto
    }

    @PostMapping(path = "/validate-declaration")
    public Map<String, Object> validateDeclaration(@RequestBody ValidateDeclarationRequest req) {
        Map<String, Object> out = new HashMap<>();
        
        // CORRECCIÓN: Convertir el Double del request a BigDecimal para el servicio
        BigDecimal cantidadParaValidar = (req.cantidadKg != null) 
            ? BigDecimal.valueOf(req.cantidadKg) 
            : BigDecimal.ZERO;

        CuotaExtraccionService.QuotaCheckResult res = cuotaService.checkDeclarationQuota(
            req.usuarioId, 
            req.perfil, 
            req.especieId, 
            req.fechaDeclaracion, 
            cantidadParaValidar // Ahora pasamos un BigDecimal
        );
        
        out.put("allowed", res.isAllowed());
        out.put("message", res.getMessage());
        return out;
    }

}