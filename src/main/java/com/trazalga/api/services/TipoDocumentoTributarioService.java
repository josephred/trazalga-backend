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
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(33L).nombre("Factura Electrónica").codigo("33").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(34L).nombre("Factura No Afecta o Exenta Electrónica").codigo("34").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(39L).nombre("Boleta Electrónica").codigo("39").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(41L).nombre("Boleta No Afecta o Exenta Electrónica").codigo("41").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(43L).nombre("Liquidación Factura Electrónica").codigo("43").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(46L).nombre("Factura de Compra Electrónica").codigo("46").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(52L).nombre("Guía de Despacho Electrónica").codigo("52").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(56L).nombre("Nota de Débito Electrónica").codigo("56").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(61L).nombre("Nota de Crédito Electrónica").codigo("61").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(110L).nombre("Factura de Exportación Electrónica").codigo("110").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(111L).nombre("Nota de Débito de Exportación Electrónica").codigo("111").build());
            tipoDocumentoTributarioRepository.save(TipoDocumentoTributarioModel.builder().id(112L).nombre("Nota de Crédito de Exportación Electrónica").codigo("112").build());
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
