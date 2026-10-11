package com.trazalga.api.services;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.TareaProgramadaEjecucionModel;
import com.trazalga.api.repositories.ITareaProgramadaEjecucionRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional(readOnly = true)
public class TareaProgramadaService {

    @Autowired
    private ITareaProgramadaEjecucionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long registrarInicio(String nombre) {
        TareaProgramadaEjecucionModel ejecucion = TareaProgramadaEjecucionModel.builder()
                .nombre(nombre)
                .inicio(new Date())
                .estado("EJECUTANDO")
                .procesados(0)
                .build();
        return repository.save(ejecucion).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarExito(Long id, int procesados, String mensaje) {
        if (id == null) return;
        repository.findById(id).ifPresent(e -> {
            e.setFin(new Date());
            e.setEstado("OK");
            e.setProcesados(procesados);
            e.setMensaje(mensaje);
            repository.save(e);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarError(Long id, String errorMensaje) {
        if (id == null) return;
        repository.findById(id).ifPresent(e -> {
            e.setFin(new Date());
            e.setEstado("ERROR");
            e.setMensaje(errorMensaje);
            repository.save(e);
        });
    }

    public List<TareaProgramadaEjecucionModel> listarHistorial() {
        return repository.findTop50ByOrderByInicioDesc();
    }

    public List<TareaProgramadaEjecucionModel> listarUltimoEstado() {
        return repository.findUltimaEjecucionPorTarea();
    }
}
