package com.trazalga.api.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

/**
 * Registro del peso verificado por el receptor al recibir una declaración
 * (comercializador que recibe de origen, o planta que recibe del comercializador).
 * Alimenta el indicador de variación de peso origen/destino.
 */
@RestController
@RequestMapping("/api/recepcion")
public class RecepcionController {

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired
    private IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    private IDeclaracionAreaRepository areaRepository;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    public static class PesoRequest {
        public Double pesoRecepcionado;
    }

    @PutMapping("/peso/{tipo}/{id}")
    public ResponseEntity<?> registrarPesoRecepcionado(
            @PathVariable String tipo,
            @PathVariable Long id,
            @RequestBody PesoRequest req) {

        if (req == null || req.pesoRecepcionado == null || req.pesoRecepcionado < 0) {
            return ResponseEntity.badRequest().body("El campo pesoRecepcionado es requerido y debe ser >= 0.");
        }

        Double pesoDeclarado;
        String tipoNormalizado = tipo == null ? "" : tipo.toUpperCase();

        switch (tipoNormalizado) {
            case "RECOLECTOR": {
                Optional<DeclaracionRecolectorModel> opt = recolectorRepository.findById(id);
                if (opt.isEmpty()) return ResponseEntity.notFound().build();
                DeclaracionRecolectorModel d = opt.get();
                if (d.getDeclaracionDestinatario() == null) {
                    return ResponseEntity.badRequest().body("La declaración aún no ha sido recepcionada por un destinatario.");
                }
                d.setPesoRecepcionado(req.pesoRecepcionado);
                recolectorRepository.save(d);
                pesoDeclarado = d.getDesembarque() != null ? d.getDesembarque().doubleValue() : null;
                break;
            }
            case "ARMADOR": {
                Optional<DeclaracionArmadorModel> opt = armadorRepository.findById(id);
                if (opt.isEmpty()) return ResponseEntity.notFound().build();
                DeclaracionArmadorModel d = opt.get();
                if (d.getDeclaracionDestinatario() == null) {
                    return ResponseEntity.badRequest().body("La declaración aún no ha sido recepcionada por un destinatario.");
                }
                d.setPesoRecepcionado(req.pesoRecepcionado);
                armadorRepository.save(d);
                pesoDeclarado = d.getDesembarque() != null ? d.getDesembarque().doubleValue() : null;
                break;
            }
            case "AREA": {
                Optional<DeclaracionAreaModel> opt = areaRepository.findById(id);
                if (opt.isEmpty()) return ResponseEntity.notFound().build();
                DeclaracionAreaModel d = opt.get();
                if (d.getDeclaracionDestinatario() == null) {
                    return ResponseEntity.badRequest().body("La declaración aún no ha sido recepcionada por un destinatario.");
                }
                d.setPesoRecepcionado(req.pesoRecepcionado);
                areaRepository.save(d);
                pesoDeclarado = d.getDesembarque();
                break;
            }
            case "COMERCIALIZADOR": {
                Optional<DeclaracionComercializadorModel> opt = comercializadorRepository.findById(id);
                if (opt.isEmpty()) return ResponseEntity.notFound().build();
                DeclaracionComercializadorModel d = opt.get();
                if (d.getDeclaracionDestinatario() == null) {
                    return ResponseEntity.badRequest().body("La declaración aún no ha sido recepcionada por un destinatario.");
                }
                d.setPesoRecepcionado(req.pesoRecepcionado);
                comercializadorRepository.save(d);
                pesoDeclarado = d.getCantidad() != null ? d.getCantidad().doubleValue() : null;
                break;
            }
            default:
                return ResponseEntity.badRequest().body("Tipo inválido. Use RECOLECTOR, ARMADOR, AREA o COMERCIALIZADOR.");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("tipo", tipoNormalizado);
        out.put("id", id);
        out.put("pesoDeclarado", pesoDeclarado);
        out.put("pesoRecepcionado", req.pesoRecepcionado);
        if (pesoDeclarado != null && pesoDeclarado > 0) {
            double variacionKg = req.pesoRecepcionado - pesoDeclarado;
            double variacionPct = Math.round(variacionKg / pesoDeclarado * 100.0 * 10.0) / 10.0;
            out.put("variacionKg", Math.round(variacionKg * 100.0) / 100.0);
            out.put("variacionPct", variacionPct);
        } else {
            out.put("variacionKg", null);
            out.put("variacionPct", null);
        }
        return ResponseEntity.ok(out);
    }
}
