package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.VedaEspecieRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class VedaEspecieService {

    @Autowired
    private VedaEspecieRepository vedaRepository;

    public List<VedaEspecieModel> getAll() {
        return vedaRepository.findAll();
    }

    public Optional<VedaEspecieModel> getById(Long id) {
        return vedaRepository.findById(id);
    }

    public VedaEspecieModel save(VedaEspecieModel veda) {
        return vedaRepository.save(veda);
    }

    public boolean delete(Long id) {
        try {
            vedaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEspecieEnVeda(Long especieId, Date fecha) {
        List<VedaEspecieModel> vedas = vedaRepository.findVedasActivasPorEspecieYFecha(especieId, fecha);
        return !vedas.isEmpty();
    }
}
