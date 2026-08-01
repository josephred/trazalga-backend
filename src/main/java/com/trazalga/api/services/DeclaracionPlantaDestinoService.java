package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
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

import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;

@Service
public class DeclaracionPlantaDestinoService {

    @Autowired
    private IDeclaracionPlantaDestinoRepository repository;

    @Autowired
    private IDeclaracionPlantaProduccionRepository produccionRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    public List<DeclaracionPlantaDestinoModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaDeclaracionDestinoDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaDestinoModel> getById(Long id) {
        return repository.findById(id);
    }

    public List<Map<String, Object>> getDetalleConsolidado(Long declaracionId) {
        Optional<DeclaracionPlantaDestinoModel> opt = repository.findById(declaracionId);
        if (opt.isPresent() && opt.get().getResumenDocumento() != null && !opt.get().getResumenDocumento().isBlank()) {
            try {
                return JSON.readValue(opt.get().getResumenDocumento(),
                        JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                // Snapshot corrupto: recalcular en vivo
            }
        }
        return calcularDetalleConsolidado(declaracionId);
    }

    private List<Map<String, Object>> calcularDetalleConsolidado(Long declaracionId) {
        String sql = "SELECT e.nombre AS especie, pr.nombre AS producto, "
                + "COALESCE(SUM(p.cantidad_producto), 0) AS total_kg, COUNT(*) AS docs "
                + "FROM declaracion_planta_produccion p "
                + "INNER JOIN especie e ON e.id = p.materia_prima_especie_id "
                + "LEFT JOIN producto pr ON pr.id = p.producto_resultante_id "
                + "WHERE p.declaracion_destinatario_id = :id AND p.consumida_por_tipo = 'PLANTA_DESTINO' "
                + "GROUP BY e.nombre, pr.nombre "
                + "ORDER BY total_kg DESC";

        jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", declaracionId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> linea = new java.util.HashMap<>();
            linea.put("especie", row[0]);
            linea.put("producto", row[1]);
            linea.put("totalKg", row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            linea.put("declaraciones", row[3] != null ? ((Number) row[3]).longValue() : 0);
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
            System.err.println("No se pudo congelar el resumen del documento destino " + declaracionId + ": " + e.getMessage());
        }
    }

    @Transactional
    public DeclaracionPlantaDestinoModel save(DeclaracionPlantaDestinoModel declaracion) {
        if (declaracion.getFolioDeclaracionDestino() == null || declaracion.getFolioDeclaracionDestino().isEmpty()) {
            declaracion.setFolioDeclaracionDestino(generarFolioDestino());
        }

        if (declaracion.getFechaDeclaracionDestino() == null) {
            declaracion.setFechaDeclaracionDestino(new Date());
        }

        DeclaracionPlantaDestinoModel saved = repository.save(declaracion);

        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(),
                saved.getId(),
                "PLANTA_DESTINO",
                saved.getUsuario().getId()
            );
            congelarResumenDocumento(saved.getId());
        }

        return saved;
    }

    @Transactional
    public DeclaracionPlantaDestinoModel updateById(DeclaracionPlantaDestinoModel request, Long id) {
        DeclaracionPlantaDestinoModel model = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Declaración no encontrada: " + id));

        if (model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido consumida y no puede ser modificada.");
        }
        String oldSeleccionadas = model.getDeclaracionesSeleccionadas();

        model.setFolioOrigen(request.getFolioOrigen());
        model.setFolioDeclaracionDestino(request.getFolioDeclaracionDestino());
        model.setFolioDeclaracionAbastecimientoPlanta(request.getFolioDeclaracionAbastecimientoPlanta());
        model.setFechaDeclaracionDestino(request.getFechaDeclaracionDestino());
        model.setFechaTrasladoDestino(request.getFechaTrasladoDestino());
        model.setFechaTraslado(request.getFechaTraslado());
        model.setHora(request.getHora());
        model.setNombrePlanta(request.getNombrePlanta());
        model.setCodigoSernapesca(request.getCodigoSernapesca());
        model.setLatitud(request.getLatitud());
        model.setLongitud(request.getLongitud());
        model.setEspecie(request.getEspecie());
        model.setProducto(request.getProducto());
        model.setCantidad(request.getCantidad());
        model.setHumedadHigrometro(request.getHumedadHigrometro());
        model.setDocumentoTributarioOrigenTipo(request.getDocumentoTributarioOrigenTipo());
        model.setDocumentoTributarioOrigenNumero(request.getDocumentoTributarioOrigenNumero());
        model.setDocumentoTributarioOrigenFecha(request.getDocumentoTributarioOrigenFecha());
        model.setDocumentoTributarioTipo(request.getDocumentoTributarioTipo());
        model.setDocumentoTributarioNumero(request.getDocumentoTributarioNumero());
        model.setDocumentoTributarioFecha(request.getDocumentoTributarioFecha());
        model.setVehiculoTransporte(request.getVehiculoTransporte());
        model.setChoferTransporte(request.getChoferTransporte());
        model.setRutChofer(request.getRutChofer());
        model.setPlacaPatente(request.getPlacaPatente());
        model.setPlacaPatenteCarro(request.getPlacaPatenteCarro());
        model.setNombreDestino(request.getNombreDestino());
        model.setRutDestino(request.getRutDestino());
        model.setCodigoSernapescaDestino(request.getCodigoSernapescaDestino());
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
                "PLANTA_DESTINO",
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
        DeclaracionPlantaDestinoModel model = repository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido consumida y no puede ser eliminada.");
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

    public List<DeclaracionPlantaDestinoModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId, Long consumidasPorId) {
        List<DeclaracionPlantaDestinoModel> libres = repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        if (consumidasPorId != null) {
            List<DeclaracionPlantaDestinoModel> consumidasPorEsta = repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioId(usuarioDestinatarioId, consumidasPorId);
            List<DeclaracionPlantaDestinoModel> combinadas = new ArrayList<>(libres);
            combinadas.addAll(consumidasPorEsta);
            return combinadas;
        }
        return libres;
    }

    private String generarFolioDestino() {
        String prefijo = "DPLA";
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findLastFolioDeclaracionDestino().stream().findFirst().orElse(null);

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return "%s-%s-%06d".formatted(prefijo, anio, correlativo);
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> ids = SeleccionTokens.idsParaTipo(sel, "PLANTA_PRODUCCION");

        if (ids.isEmpty()) return;

        List<DeclaracionPlantaProduccionModel> producciones = produccionRepository.findAllById(ids);
        for (DeclaracionPlantaProduccionModel p : producciones) {
            if (p.getDeclaracionDestinatario() == null) {
                p.setDeclaracionDestinatario(consumidaPorId);
                p.setConsumidaPorTipo(tipo);
                produccionRepository.save(p);
            }
        }
    }

    private void liberarDeclaracionesConsumidas(String idsCSV, Long consumidaPorId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> ids = SeleccionTokens.idsParaTipo(sel, "PLANTA_PRODUCCION");

        if (ids.isEmpty()) return;

        List<DeclaracionPlantaProduccionModel> producciones = produccionRepository.findAllById(ids);
        for (DeclaracionPlantaProduccionModel p : producciones) {
            if (consumidaPorId.equals(p.getDeclaracionDestinatario())) {
                p.setDeclaracionDestinatario(null);
                p.setConsumidaPorTipo(null);
                produccionRepository.save(p);
            }
        }
    }
}