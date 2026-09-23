package com.trazalga.api.services;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;

@Service
public class DeclaracionMarcaService {

    @Autowired
    private IDeclaracionMarcaRepository repository;

    public List<DeclaracionMarcaModel> getAll() {
        return repository.findAll();
    }

    public List<DeclaracionMarcaModel> getByDeclaracion(String tipo, Long declaracionId) {
        return repository.findByDeclaracionTipoAndDeclaracionId(tipo, declaracionId);
    }

    public List<DeclaracionMarcaModel> getByMarca(String marca) {
        return repository.findByMarca(marca);
    }

    public List<DeclaracionMarcaModel> getPendientes() {
        return repository.findByResueltaFalse();
    }

    public List<DeclaracionMarcaModel> findConFiltros(String marca, Boolean resuelta, String declaracionTipo, Date startDate, Date endDate) {
        return repository.findConFiltros(marca, resuelta, declaracionTipo, startDate, endDate);
    }

    public Map<String, Object> getResumen() {
        List<DeclaracionMarcaModel> todas = repository.findAll();
        long total = todas.size();
        long pendientes = todas.stream().filter(m -> Boolean.FALSE.equals(m.getResuelta())).count();
        long resueltas = todas.stream().filter(m -> Boolean.TRUE.equals(m.getResuelta())).count();

        Map<String, Long> porMarca = new HashMap<>();
        porMarca.put("EN_VEDA", todas.stream().filter(m -> "EN_VEDA".equals(m.getMarca())).count());
        porMarca.put("LED_EXCEDIDO", todas.stream().filter(m -> "LED_EXCEDIDO".equals(m.getMarca())).count());
        porMarca.put("DESEMBARQUE_ATIPICO", todas.stream().filter(m -> "DESEMBARQUE_ATIPICO".equals(m.getMarca())).count());
        porMarca.put("CUOTA_EXCEDIDA", todas.stream().filter(m -> "CUOTA_EXCEDIDA".equals(m.getMarca())).count());
        porMarca.put("POSTERIOR_CIERRE", todas.stream().filter(m -> "POSTERIOR_CIERRE".equals(m.getMarca())).count());

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("total", total);
        resumen.put("pendientes", pendientes);
        resumen.put("resueltas", resueltas);
        resumen.put("porMarca", porMarca);
        return resumen;
    }

    public DeclaracionMarcaModel marcar(String declaracionTipo, Long declaracionId, String marca, String detalle, Long reglaId) {
        DeclaracionMarcaModel model = DeclaracionMarcaModel.builder()
                .declaracionTipo(declaracionTipo)
                .declaracionId(declaracionId)
                .marca(marca)
                .detalle(detalle)
                .reglaId(reglaId)
                .resuelta(false)
                .build();
        return repository.save(model);
    }

    public Optional<DeclaracionMarcaModel> resolverMarca(Long id) {
        return repository.findById(id).map(m -> {
            m.setResuelta(true);
            return repository.save(m);
        });
    }

    public Optional<DeclaracionMarcaModel> reabrirMarca(Long id) {
        return repository.findById(id).map(m -> {
            m.setResuelta(false);
            return repository.save(m);
        });
    }
}
