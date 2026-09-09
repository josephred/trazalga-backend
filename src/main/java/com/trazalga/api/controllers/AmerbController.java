package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.services.AmerbService;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping({"/amerb", "/api/amerbs"})
public class AmerbController {

    @Autowired
    private AmerbService amerbService;

    // Obtener todas las AMERB
    @GetMapping
    public List<AmerbModel> getAllAmerbs() {
        return amerbService.getAllAmerbs();
    }

    // Obtener AMERB por ID
    @GetMapping("/{id}")
    public Optional<AmerbModel> getAmerbById(@PathVariable Long id) {
        return amerbService.getAmerbById(id);
    }

    // Crear o actualizar una AMERB
    @PostMapping
    public AmerbModel saveAmerb(@RequestBody AmerbModel amerb) {
        return amerbService.saveAmerb(amerb);
    }

    // Eliminar una AMERB por ID
    @DeleteMapping("/{id}")
    public String deleteAmerb(@PathVariable Long id) {
        boolean deleted = amerbService.deleteAmerb(id);
        return deleted ? "AMERB con ID " + id + " eliminada." : "Error al eliminar la AMERB.";
    }

    // Obtener AMERB por región
    @GetMapping("/region/{region}")
    public List<AmerbModel> getAmerbsByRegion(@PathVariable String region) {
        return amerbService.getAmerbsByRegion(region);
    }

    // Obtener AMERB por estado
    @GetMapping("/estado/{estado}")
    public List<AmerbModel> getAmerbsByEstado(@PathVariable String estado) {
        return amerbService.getAmerbsByEstado(estado);
    }

    // Obtener AMERB por código Sernapesca
    @GetMapping("/codigo/{codigoSernapesca}")
    public AmerbModel getAmerbByCodigoSernapesca(@PathVariable String codigoSernapesca) {
        return amerbService.getAmerbByCodigoSernapesca(codigoSernapesca);
    }
}
