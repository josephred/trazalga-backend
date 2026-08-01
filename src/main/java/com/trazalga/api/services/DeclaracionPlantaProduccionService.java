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

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    public List<DeclaracionPlantaProduccionModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaProduccionDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaProduccionModel> getById(Long id) {
        return repository.findById(id);
    }

    public List<Map<String, Object>> getDetalleConsolidado(Long declaracionId) {
        Optional<DeclaracionPlantaProduccionModel> opt = repository.findById(declaracionId);
        if (opt.isPresent() && opt.get().getResumenDocumento() != null && !opt.get().getResumenDocumento().isBlank()) {
            try {
                return JSON.readValue(opt.get().getResumenDocumento(),
                        JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                // Snapshot corrupto/ilegible: recalcular en vivo como respaldo
            }
        }
        return calcularDetalleConsolidado(declaracionId);
    }

    private List<Map<String, Object>> calcularDetalleConsolidado(Long declaracionId) {
        String sql = "SELECT e.nombre AS especie, h.nombre AS humedad, c.nombre AS composicion, "
                + "COALESCE(SUM(o.cantidad), 0) AS total_kg, COUNT(*) AS docs "
                + "FROM declaracion_planta_abastecimiento o "
                + "INNER JOIN especie e ON e.id = o.especie_id "
                + "LEFT JOIN humedad_estado h ON h.id = o.humedad_estado_id "
                + "LEFT JOIN composicion c ON c.id = o.composicion_id "
                + "WHERE o.declaracion_destinatario_id = :id AND o.consumida_por_tipo = 'PLANTA_PRODUCCION' "
                + "GROUP BY e.nombre, h.nombre, c.nombre "
                + "ORDER BY total_kg DESC";

        jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", declaracionId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> linea = new java.util.HashMap<>();
            linea.put("especie", row[0]);
            linea.put("humedad", row[1]);
            linea.put("composicion", row[2]);
            linea.put("totalKg", row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
            linea.put("declaraciones", row[4] != null ? ((Number) row[4]).longValue() : 0);
            out.add(linea);
        }
        return out;
    }

    private void congelarResumenDocumento(Long declaracionId) {
        try {
            List<Map<String, Object>> detalle = calcularDetalleConsolidado(declaracionId);
            String json = JSON.writeValueAsString(detalle);
            repository.findById(declaracionId).ifPresent(d -> {
                d.setResumenDocumento(json);
                repository.save(d);
            });
        } catch (Exception e) {
            System.err.println("No se pudo congelar el resumen del documento " + declaracionId + ": " + e.getMessage());
        }
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
            congelarResumenDocumento(saved.getId());
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
        model.setFechaTraslado(request.getFechaTraslado());
        model.setHora(request.getHora());
        model.setNombrePlanta(request.getNombrePlanta());
        model.setCodigoSernapesca(request.getCodigoSernapesca());
        model.setLatitud(request.getLatitud());
        model.setLongitud(request.getLongitud());
        model.setMateriaPrimaEspecie(request.getMateriaPrimaEspecie());
        model.setMateriaPrimaProducto(request.getMateriaPrimaProducto());
        model.setHumedadEstado(request.getHumedadEstado());
        model.setHumedadHigrometro(request.getHumedadHigrometro());
        model.setCantidadMateriaPrima(request.getCantidadMateriaPrima());
        model.setProductoResultante(request.getProductoResultante());
        model.setCantidadProducto(request.getCantidadProducto());
        model.setDocumentoTributarioOrigenTipo(request.getDocumentoTributarioOrigenTipo());
        model.setDocumentoTributarioOrigenNumero(request.getDocumentoTributarioOrigenNumero());
        model.setDocumentoTributarioOrigenFecha(request.getDocumentoTributarioOrigenFecha());
        model.setDocumentoTributarioDestinoTipo(request.getDocumentoTributarioDestinoTipo());
        model.setDocumentoTributarioDestinoNumero(request.getDocumentoTributarioDestinoNumero());
        model.setDocumentoTributarioDestinoFecha(request.getDocumentoTributarioDestinoFecha());
        model.setVehiculoTransporte(request.getVehiculoTransporte());
        model.setChoferTransporte(request.getChoferTransporte());
        model.setRutChofer(request.getRutChofer());
        model.setPlacaPatente(request.getPlacaPatente());
        model.setPlacaPatenteCarro(request.getPlacaPatenteCarro());
        model.setPatente(request.getPatente());
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
            congelarResumenDocumento(id);
        } else {
            model.setResumenDocumento(null);
            repository.save(model);
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