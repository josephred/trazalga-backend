package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;

@Service
public class DeclaracionPlantaProduccionService {

    @Autowired
    private IDeclaracionPlantaProduccionRepository repository;

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;

    public List<DeclaracionPlantaProduccionModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaProduccionDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaProduccionModel> getById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public DeclaracionPlantaProduccionModel save(DeclaracionPlantaProduccionModel declaracion) {
        if (declaracion.getFolioDeclaracionPpla() == null || declaracion.getFolioDeclaracionPpla().isEmpty()) {
            declaracion.setFolioDeclaracionPpla(generarFolioPpla());
        }

        if (declaracion.getFechaProduccion() == null) {
            declaracion.setFechaProduccion(new Date());
        }

        DeclaracionPlantaProduccionModel saved = repository.save(declaracion);

        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(), 
                saved.getId(), 
                "PLANTA_PRODUCCION", 
                saved.getUsuario().getId()
            );
        }

        return saved;
    }

    @Transactional
    public DeclaracionPlantaProduccionModel updateById(DeclaracionPlantaProduccionModel request, Long id) {
        DeclaracionPlantaProduccionModel model = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Declaración no encontrada: " + id));

        if (model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
        String oldSeleccionadas = model.getDeclaracionesSeleccionadas();

        model.setFolioOrigen(request.getFolioOrigen());
        model.setFolioDeclaracionPpla(request.getFolioDeclaracionPpla());
        model.setFechaProduccion(request.getFechaProduccion());
        model.setHora(request.getHora());
        model.setNombrePlanta(request.getNombrePlanta());
        model.setCodigoSernapesca(request.getCodigoSernapesca());
        model.setLatitud(request.getLatitud());
        model.setLongitud(request.getLongitud());
        model.setMateriaPrimaEspecie(request.getMateriaPrimaEspecie());
        model.setMateriaPrimaProducto(request.getMateriaPrimaProducto());
        model.setHumedadEstado(request.getHumedadEstado());
        model.setCantidadMateriaPrima(request.getCantidadMateriaPrima());
        model.setProductoResultante(request.getProductoResultante());
        model.setCantidadProducto(request.getCantidadProducto());
        model.setUsuarioDestinatario(request.getUsuarioDestinatario());
        model.setDeclaracionesSeleccionadas(request.getDeclaracionesSeleccionadas());

        repository.save(model);

        if (oldSeleccionadas != null && !oldSeleccionadas.isEmpty()) {
            liberarDeclaracionesConsumidas(oldSeleccionadas, id);
        }
        if (request.getDeclaracionesSeleccionadas() != null && !request.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                request.getDeclaracionesSeleccionadas(),
                id,
                "PLANTA_PRODUCCION",
                model.getUsuario().getId()
            );
        }

        return model;
    }

    @Transactional
    public Boolean deleteById(Long id) {
        DeclaracionPlantaProduccionModel model = repository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try {
            if (model != null && model.getDeclaracionesSeleccionadas() != null && !model.getDeclaracionesSeleccionadas().isEmpty()) {
                liberarDeclaracionesConsumidas(model.getDeclaracionesSeleccionadas(), id);
            }
            repository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<DeclaracionPlantaProduccionModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId, Long consumidasPorId) {
        List<DeclaracionPlantaProduccionModel> libres = repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        if (consumidasPorId != null) {
            List<DeclaracionPlantaProduccionModel> consumidasPorEsta = repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioId(usuarioDestinatarioId, consumidasPorId);
            List<DeclaracionPlantaProduccionModel> combinadas = new ArrayList<>(libres);
            combinadas.addAll(consumidasPorEsta);
            return combinadas;
        }
        return libres;
    }

    private String generarFolioPpla() {
        String prefijo = "PPLA";
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findLastFolioDeclaracionPpla().stream().findFirst().orElse(null);

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return "%s-%s-%06d".formatted(prefijo, anio, correlativo);
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> ids = SeleccionTokens.idsParaTipo(sel, "PLANTA_ABASTECIMIENTO");

        if (ids.isEmpty()) return;

        List<DeclaracionPlantaAbastecimientoModel> abastecimientos = abastecimientoRepository.findAllById(ids);
        for (DeclaracionPlantaAbastecimientoModel a : abastecimientos) {
            if (a.getDeclaracionDestinatario() == null) {
                a.setDeclaracionDestinatario(consumidaPorId);
                a.setConsumidaPorTipo(tipo);
                abastecimientoRepository.save(a);
            }
        }
    }

    private void liberarDeclaracionesConsumidas(String idsCSV, Long consumidaPorId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> ids = SeleccionTokens.idsParaTipo(sel, "PLANTA_ABASTECIMIENTO");

        if (ids.isEmpty()) return;

        List<DeclaracionPlantaAbastecimientoModel> abastecimientos = abastecimientoRepository.findAllById(ids);
        for (DeclaracionPlantaAbastecimientoModel a : abastecimientos) {
            if (consumidaPorId.equals(a.getDeclaracionDestinatario())) {
                a.setDeclaracionDestinatario(null);
                a.setConsumidaPorTipo(null);
                abastecimientoRepository.save(a);
            }
        }
    }
}