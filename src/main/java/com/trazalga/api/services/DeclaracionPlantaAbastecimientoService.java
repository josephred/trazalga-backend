package com.trazalga.api.services;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
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

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;

@Service
public class DeclaracionPlantaAbastecimientoService {

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository repository;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired
    private IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    private IDeclaracionAreaRepository areaRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    public List<DeclaracionPlantaAbastecimientoModel> getAllByUsuarioId(Long usuarioId) {
        return repository.findAllByUsuarioIdOrderByFechaIngresoPlantaDesc(usuarioId);
    }

    public Optional<DeclaracionPlantaAbastecimientoModel> getById(Long id) {
        return repository.findById(id);
    }

    public List<Map<String, Object>> getDetalleConsolidado(Long declaracionId) {
        Optional<DeclaracionPlantaAbastecimientoModel> opt = repository.findById(declaracionId);
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
                + "COALESCE(SUM(o.desembarque), 0) AS total_kg, COUNT(*) AS docs "
                + "FROM ("
                + "  SELECT especie_id, humedad_estado_id, composicion_id, desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_recolector "
                + "  UNION ALL "
                + "  SELECT especie_id, humedad_estado_id, composicion_id, desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_armador "
                + "  UNION ALL "
                + "  SELECT especie_id, humedad_estado_id, composicion_id, desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_area "
                + "  UNION ALL "
                + "  SELECT especie_id, humedad_estado_id, composicion_id, cantidad AS desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_comercializador "
                + ") o "
                + "INNER JOIN especie e ON e.id = o.especie_id "
                + "LEFT JOIN humedad_estado h ON h.id = o.humedad_estado_id "
                + "LEFT JOIN composicion c ON c.id = o.composicion_id "
                + "WHERE o.declaracion_destinatario_id = :id AND o.consumida_por_tipo = 'PLANTA_ABASTECIMIENTO' "
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
    public DeclaracionPlantaAbastecimientoModel save(DeclaracionPlantaAbastecimientoModel declaracion) {
        if (declaracion.getFolioDeclaracionAPla() == null || declaracion.getFolioDeclaracionAPla().isEmpty()) {
            declaracion.setFolioDeclaracionAPla(generarFolio());
        }
        if (declaracion.getFechaIngresoPlanta() == null) {
            declaracion.setFechaIngresoPlanta(new Date());
        }
        sanearComposicion(declaracion);
        DeclaracionPlantaAbastecimientoModel saved = repository.save(declaracion);

        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(), 
                saved.getId(), 
                "PLANTA_ABASTECIMIENTO", 
                saved.getUsuario().getId()
            );
            congelarResumenDocumento(saved.getId());
        }

        return saved;
    }

    @Transactional
    public DeclaracionPlantaAbastecimientoModel updateById(DeclaracionPlantaAbastecimientoModel request, Long id) {
        DeclaracionPlantaAbastecimientoModel model = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Declaración no encontrada: " + id));

        if (model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
        String oldSeleccionadas = model.getDeclaracionesSeleccionadas();

        model.setFolioOrigen(request.getFolioOrigen());
        model.setFolioDeclaracionAPla(request.getFolioDeclaracionAPla());
        model.setFechaIngresoPlanta(request.getFechaIngresoPlanta());
        model.setFechaTraslado(request.getFechaTraslado());
        model.setHora(request.getHora());
        model.setNombrePlanta(request.getNombrePlanta());
        model.setCodigoSernapesca(request.getCodigoSernapesca());
        model.setLatitud(request.getLatitud());
        model.setLongitud(request.getLongitud());
        model.setEspecie(request.getEspecie());
        model.setComposicion(request.getComposicion());
        model.setHumedadEstado(request.getHumedadEstado());
        model.setHumedadHigrometro(request.getHumedadHigrometro());
        model.setCantidad(request.getCantidad());
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

        sanearComposicion(model);
        repository.save(model);

        if (oldSeleccionadas != null && !oldSeleccionadas.isEmpty()) {
            liberarDeclaracionesConsumidas(oldSeleccionadas, id);
        }
        if (request.getDeclaracionesSeleccionadas() != null && !request.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                request.getDeclaracionesSeleccionadas(),
                id,
                "PLANTA_ABASTECIMIENTO",
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
        DeclaracionPlantaAbastecimientoModel model = repository.findById(id).orElse(null);
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

    public List<DeclaracionPlantaAbastecimientoModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId, Long consumidasPorId) {
        List<DeclaracionPlantaAbastecimientoModel> libres = repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        if (consumidasPorId != null) {
            List<DeclaracionPlantaAbastecimientoModel> consumidasPorEsta = repository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioId(usuarioDestinatarioId, consumidasPorId);
            List<DeclaracionPlantaAbastecimientoModel> combinadas = new ArrayList<>(libres);
            combinadas.addAll(consumidasPorEsta);
            return combinadas;
        }
        return libres;
    }

    private String generarFolio() {
        String prefijo = "DAPLA"; // Declaracion Abastecimiento Planta
        String anio = new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime());
        String ultimoFolio = repository.findLastFolioDeclaracionAPla().stream().findFirst().orElse(null);

        int correlativo = 1;
        if (ultimoFolio != null && ultimoFolio.startsWith(prefijo + "-" + anio)) {
            String[] partes = ultimoFolio.split("-");
            correlativo = Integer.parseInt(partes[2]) + 1;
        }
        return "%s-%s-%06d".formatted(prefijo, anio, correlativo);
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        
        List<Long> comercializadorIds = SeleccionTokens.idsParaTipo(sel, "COMERCIALIZADOR");
        if (!comercializadorIds.isEmpty()) {
            for (DeclaracionComercializadorModel c : comercializadorRepository.findAllById(comercializadorIds)) {
                if (c.getUsuarioDestinatario() != null && c.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && c.getDeclaracionDestinatario() == null) {
                    c.setDeclaracionDestinatario(consumidaPorId);
                    c.setConsumidaPorTipo(tipo);
                    comercializadorRepository.save(c);
                }
            }
        }

        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        if (!recolectorIds.isEmpty()) {
            for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                if (r.getUsuarioDestinatario() != null && r.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && r.getDeclaracionDestinatario() == null) {
                    r.setDeclaracionDestinatario(consumidaPorId);
                    r.setConsumidaPorTipo(tipo);
                    recolectorRepository.save(r);
                }
            }
        }

        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        if (!armadorIds.isEmpty()) {
            for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                if (a.getUsuarioDestinatario() != null && a.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && a.getDeclaracionDestinatario() == null) {
                    a.setDeclaracionDestinatario(consumidaPorId);
                    a.setConsumidaPorTipo(tipo);
                    armadorRepository.save(a);
                }
            }
        }

        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");
        if (!areaIds.isEmpty()) {
            for (DeclaracionAreaModel ar : areaRepository.findAllById(areaIds)) {
                if (ar.getUsuarioDestinatario() != null && ar.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && ar.getDeclaracionDestinatario() == null) {
                    ar.setDeclaracionDestinatario(consumidaPorId);
                    ar.setConsumidaPorTipo(tipo);
                    areaRepository.save(ar);
                }
            }
        }
    }

    private void liberarDeclaracionesConsumidas(String idsCSV, Long consumidaPorId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> comercializadorIds = SeleccionTokens.idsParaTipo(sel, "COMERCIALIZADOR");
        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");

        if (!comercializadorIds.isEmpty()) {
            for (DeclaracionComercializadorModel c : comercializadorRepository.findAllById(comercializadorIds)) {
                if (consumidaPorId.equals(c.getDeclaracionDestinatario())) {
                    c.setDeclaracionDestinatario(null);
                    c.setConsumidaPorTipo(null);
                    comercializadorRepository.save(c);
                }
            }
        }

        if (!recolectorIds.isEmpty()) {
            for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                if (consumidaPorId.equals(r.getDeclaracionDestinatario())) {
                    r.setDeclaracionDestinatario(null);
                    r.setConsumidaPorTipo(null);
                    recolectorRepository.save(r);
                }
            }
        }

        if (!armadorIds.isEmpty()) {
            for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                if (consumidaPorId.equals(a.getDeclaracionDestinatario())) {
                    a.setDeclaracionDestinatario(null);
                    a.setConsumidaPorTipo(null);
                    armadorRepository.save(a);
                }
            }
        }

        if (!areaIds.isEmpty()) {
            for (DeclaracionAreaModel ar : areaRepository.findAllById(areaIds)) {
                if (consumidaPorId.equals(ar.getDeclaracionDestinatario())) {
                    ar.setDeclaracionDestinatario(null);
                    ar.setConsumidaPorTipo(null);
                    areaRepository.save(ar);
                }
            }
        }
    }

    private void sanearComposicion(DeclaracionPlantaAbastecimientoModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }
}