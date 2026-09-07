package com.trazalga.api.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.repositories.IProvinciaRepository;

@Service
public class ProvinciaService {

    @Autowired
    private IProvinciaRepository provinciaRepository;

    public List<ProvinciaModel> getProvincias() {
        return provinciaRepository.findAll();
    }

    public List<ProvinciaModel> getByRegion(Long regionId) {
        return provinciaRepository.findByRegionId(regionId);
    }

    public Optional<ProvinciaModel> getById(Long id) {
        return provinciaRepository.findById(id);
    }

    public ProvinciaModel saveProvincia(ProvinciaModel provincia) {
        return provinciaRepository.save(provincia);
    }

    public ProvinciaModel updateById(ProvinciaModel request, Long id) {
        ProvinciaModel provincia = provinciaRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Provincia no encontrada con id: " + id));
        provincia.setNombre(request.getNombre());
        provincia.setCodigo(request.getCodigo());
        if (request.getRegion() != null) {
            provincia.setRegion(request.getRegion());
        }
        return provinciaRepository.save(provincia);
    }

    public Boolean deleteProvincia(Long id) {
        try {
            provinciaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
