package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.repositories.IAmerbRepository;
import java.util.List;
import java.util.Optional;

@Service
public class AmerbService {

    @Autowired
    private IAmerbRepository amerbRepository;

    // Obtener todas las AMERB
    public List<AmerbModel> getAllAmerbs() {
        return amerbRepository.findAll();
    }

    // Obtener AMERB por ID
    public Optional<AmerbModel> getAmerbById(Long id) {
        return amerbRepository.findById(id);
    }

    // Guardar o actualizar una AMERB
    public AmerbModel saveAmerb(AmerbModel amerb) {
        return amerbRepository.save(amerb);
    }

    // Eliminar una AMERB
    public boolean deleteAmerb(Long id) {
        if (amerbRepository.existsById(id)) {
            amerbRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // Obtener AMERB por región
    public List<AmerbModel> getAmerbsByRegion(String region) {
        return amerbRepository.findByRegion(region);
    }

    // Obtener AMERB por estado
    public List<AmerbModel> getAmerbsByEstado(String estado) {
        return amerbRepository.findByEstado(estado);
    }

    // Obtener AMERB por código Sernapesca
    public AmerbModel getAmerbByCodigoSernapesca(String codigoSernapesca) {
        return amerbRepository.findByCodigoSernapesca(codigoSernapesca);
    }
}
