package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.trazalga.api.models.TipoDocumentoTributarioModel;
import com.trazalga.api.repositories.ITipoDocumentoTributarioRepository;

@Service
public class TipoDocumentoTributarioService {

    @Autowired
    private ITipoDocumentoTributarioRepository tipoDocumentoTributarioRepository;

    @PostConstruct
    public void seedDefaults() {
        if (tipoDocumentoTributarioRepository.count() == 0) {
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder()
                    .nombre("Guía de Despacho")
                    .codigo("GD")
                    .build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder()
                    .nombre("Factura")
                    .codigo("FC")
                    .build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder()
                    .nombre("Boleta")
                    .codigo("BL")
                    .build());
        }
    }

    public ArrayList<TipoDocumentoTributarioModel> getTiposDocumentoTributario() {
        return (ArrayList<TipoDocumentoTributarioModel>) tipoDocumentoTributarioRepository.findAll();
    }

    public TipoDocumentoTributarioModel saveTipoDocumentoTributario(TipoDocumentoTributarioModel tipo) {
        return tipoDocumentoTributarioRepository.save(tipo);
    }

    public Optional<TipoDocumentoTributarioModel> getById(Long id) {
        return tipoDocumentoTributarioRepository.findById(id);
    }

    public TipoDocumentoTributarioModel updateById(TipoDocumentoTributarioModel request, Long id) {
        TipoDocumentoTributarioModel tipo = tipoDocumentoTributarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de documento no encontrado"));
        tipo.setNombre(request.getNombre());
        tipo.setCodigo(request.getCodigo());
        return tipoDocumentoTributarioRepository.save(tipo);
    }

    public Boolean deleteById(Long id) {
        try {
            tipoDocumentoTributarioRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
