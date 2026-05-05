package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DeclaracionBuzosService {

    @Autowired
    private DeclaracionBuzosRepository declaracionBuzosRepository;

    public ArrayList<DeclaracionBuzosModel> obtenerDeclaracionBuzos() {
        return (ArrayList<DeclaracionBuzosModel>) declaracionBuzosRepository.findAll();
    }

    public DeclaracionBuzosModel guardarDeclaracionBuzos(DeclaracionBuzosModel declaracionBuzos) {
        return declaracionBuzosRepository.save(declaracionBuzos);
    }

    public Optional<DeclaracionBuzosModel> obtenerPorId(Long id) {
        return declaracionBuzosRepository.findById(id);
    }

    public List<DeclaracionBuzosModel> obtenerPorDeclaracionArmadorId(Long id) {
        return declaracionBuzosRepository.findByDeclaracionArmadorId(id);
    }

    public List<DeclaracionBuzosModel> obtenerPorDeclaracionRecolectorId(Long id) {
        return declaracionBuzosRepository.findByDeclaracionRecolectorId(id);
    }

    public List<DeclaracionBuzosModel> obtenerPorDeclaracionAreaId(Long id) {
        return declaracionBuzosRepository.findByDeclaracionAreaId(id);
    }

    public boolean eliminarDeclaracionBuzos(Long id) {
        try {
            declaracionBuzosRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
