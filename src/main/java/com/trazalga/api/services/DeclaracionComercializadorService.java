package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.services.trazabilidad.SeleccionTokens;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;

@Service
public class DeclaracionComercializadorService {
    
    @Autowired
    IDeclaracionComercializadorRepository declaracionComercializadorRepository;

    @Autowired
    IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired
    IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    IDeclaracionAreaRepository areaRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Detalle consolidado de una declaración de comercializador: una línea por
     * especie + humedad + composición con su total, como los ítems de un documento
     * tributario. Lee primero el snapshot persistido (resumenDocumento, congelado al
     * guardar/editar); si la declaración es anterior a esta funcionalidad y no tiene
     * snapshot, recurre al cálculo en vivo desde las declaraciones de origen consumidas.
     */
    public List<Map<String, Object>> getDetalleConsolidado(Long declaracionId) {
        Optional<DeclaracionComercializadorModel> opt = declaracionComercializadorRepository.findById(declaracionId);
        if (opt.isPresent() && opt.get().getResumenDocumento() != null && !opt.get().getResumenDocumento().isBlank()) {
            try {
                return JSON.readValue(opt.get().getResumenDocumento(),
                        JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                // Snapshot corrupto/ilegible: no es crítico, recalcular en vivo como respaldo
            }
        }
        return calcularDetalleConsolidado(declaracionId);
    }

    /**
     * Cálculo en vivo del detalle consolidado, agrupando las declaraciones de origen
     * que esta declaración consume. Es la fuente que se congela en resumenDocumento
     * justo después de marcar las declaraciones como consumidas (guardar/editar).
     */
    private List<Map<String, Object>> calcularDetalleConsolidado(Long declaracionId) {
        String sql = "SELECT e.nombre AS especie, h.nombre AS humedad, c.nombre AS composicion, "
                + "COALESCE(SUM(o.desembarque), 0) AS total_kg, COUNT(*) AS docs "
                + "FROM ("
                + "  SELECT especie_id, humedad_estado_id, composicion_id, desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_recolector "
                + "  UNION ALL "
                + "  SELECT especie_id, humedad_estado_id, composicion_id, desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_armador "
                + "  UNION ALL "
                + "  SELECT especie_id, humedad_estado_id, composicion_id, desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_area "
                + ") o "
                + "INNER JOIN especie e ON e.id = o.especie_id "
                + "LEFT JOIN humedad_estado h ON h.id = o.humedad_estado_id "
                + "LEFT JOIN composicion c ON c.id = o.composicion_id "
                + "WHERE o.declaracion_destinatario_id = :id AND o.consumida_por_tipo = 'COMERCIALIZADOR' "
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

    /**
     * Recalcula el detalle consolidado y lo congela en resumenDocumento. Se llama
     * justo después de marcar las declaraciones seleccionadas como consumidas (guardar
     * o editar), momento en que declaracion_destinatario_id ya apunta a este documento.
     */
    private void congelarResumenDocumento(Long declaracionId) {
        try {
            List<Map<String, Object>> detalle = calcularDetalleConsolidado(declaracionId);
            String json = JSON.writeValueAsString(detalle);
            declaracionComercializadorRepository.findById(declaracionId).ifPresent(d -> {
                d.setResumenDocumento(json);
                declaracionComercializadorRepository.save(d);
            });
        } catch (Exception e) {
            // No bloquear el guardado de la declaración por un fallo al congelar el resumen;
            // getDetalleConsolidado recurre al cálculo en vivo si no queda snapshot.
            System.err.println("No se pudo congelar el resumen del documento " + declaracionId + ": " + e.getMessage());
        }
    }

    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializador(){
        return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAll();
        // return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAllOrderByCampoEspecificoDesc();
    }

    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializadorIdUsuario(Long id){
        return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAllByUsuarioId(id);
    }

    public ArrayList<DeclaracionComercializadorModel> getDeclaracionesComercializadorUsuarioIdDesc(Long id){
        return (ArrayList<DeclaracionComercializadorModel>) declaracionComercializadorRepository.findAllByUsuarioIdOrderByFechaDeclaracionDesc(id);
    }

    @Transactional
    public DeclaracionComercializadorModel saveDeclaracionComercializador(DeclaracionComercializadorModel declaracionComercializadorModel){
        sanearComposicion(declaracionComercializadorModel);
        DeclaracionComercializadorModel saved = declaracionComercializadorRepository.save(declaracionComercializadorModel);
        if (saved.getDeclaracionesSeleccionadas() != null && !saved.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                saved.getDeclaracionesSeleccionadas(),
                saved.getId(),
                "COMERCIALIZADOR",
                saved.getUsuario().getId()
            );
            congelarResumenDocumento(saved.getId());
        }
        return saved;
    }

    public Optional<DeclaracionComercializadorModel> getById(Long id){
        return declaracionComercializadorRepository.findById(id);
    }

    public List<DeclaracionComercializadorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionComercializadorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    @Transactional
    public DeclaracionComercializadorModel updateById(DeclaracionComercializadorModel request, Long id){
        DeclaracionComercializadorModel declaracionComercializadorModel = declaracionComercializadorRepository.findById(id).get();
        if (declaracionComercializadorModel.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
        String oldSeleccionadas = declaracionComercializadorModel.getDeclaracionesSeleccionadas();

        declaracionComercializadorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionComercializadorModel.setFolioDesembarqueAc(request.getFolioDesembarqueAc());
        declaracionComercializadorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionComercializadorModel.setFechaTraslado(request.getFechaTraslado());
        declaracionComercializadorModel.setHora(request.getHora());
        declaracionComercializadorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionComercializadorModel.setNombreComercializador(request.getNombreComercializador());
        //declaracionComercializadorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionComercializadorModel.setLatitud(request.getLatitud());
        declaracionComercializadorModel.setLongitud(request.getLongitud()); 
        declaracionComercializadorModel.setEspecie(request.getEspecie());
        declaracionComercializadorModel.setComposicion(request.getComposicion());
        declaracionComercializadorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionComercializadorModel.setHumedadHigrometro(request.getHumedadHigrometro());
        declaracionComercializadorModel.setCantidad(request.getCantidad());
        declaracionComercializadorModel.setDocumentoTributarioOrigenTipo(request.getDocumentoTributarioOrigenTipo());
        declaracionComercializadorModel.setDocumentoTributarioOrigenNumero(request.getDocumentoTributarioOrigenNumero());
        declaracionComercializadorModel.setDocumentoTributarioOrigenFecha(request.getDocumentoTributarioOrigenFecha());
        declaracionComercializadorModel.setDocumentoTributarioDestinoTipo(request.getDocumentoTributarioDestinoTipo());
        declaracionComercializadorModel.setDocumentoTributarioDestinoNumero(request.getDocumentoTributarioDestinoNumero());
        declaracionComercializadorModel.setDocumentoTributarioDestinoFecha(request.getDocumentoTributarioDestinoFecha());
        declaracionComercializadorModel.setVehiculoTransporte(request.getVehiculoTransporte());
        declaracionComercializadorModel.setChoferTransporte(request.getChoferTransporte());
        declaracionComercializadorModel.setRutChofer(request.getRutChofer());
        declaracionComercializadorModel.setPlacaPatente(request.getPlacaPatente());
        declaracionComercializadorModel.setPlacaPatenteCarro(request.getPlacaPatenteCarro());
        declaracionComercializadorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        // declaracionComercializadorModel.setNombreDestinatario(request.getNombreDestinatario());
        declaracionComercializadorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracionComercializadorModel.setPatente(request.getPatente());
        declaracionComercializadorModel.setDeclaracionesSeleccionadas(request.getDeclaracionesSeleccionadas());
        
        sanearComposicion(declaracionComercializadorModel);
        declaracionComercializadorRepository.save(declaracionComercializadorModel);

        if (oldSeleccionadas != null && !oldSeleccionadas.isEmpty()) {
            liberarDeclaracionesConsumidas(oldSeleccionadas, id);
        }
        if (request.getDeclaracionesSeleccionadas() != null && !request.getDeclaracionesSeleccionadas().isEmpty()) {
            marcarDeclaracionesComoConsumidas(
                request.getDeclaracionesSeleccionadas(),
                id,
                "COMERCIALIZADOR",
                declaracionComercializadorModel.getUsuario().getId()
            );
            congelarResumenDocumento(id);
        } else {
            // Sin líneas seleccionadas tras la edición: no dejar un snapshot obsoleto
            declaracionComercializadorModel.setResumenDocumento(null);
            declaracionComercializadorRepository.save(declaracionComercializadorModel);
        }

        return declaracionComercializadorModel;
    }

    @Transactional
    public Boolean deleteDeclaracionComercializador(Long id){
        DeclaracionComercializadorModel model = declaracionComercializadorRepository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try{
            if (model != null && model.getDeclaracionesSeleccionadas() != null && !model.getDeclaracionesSeleccionadas().isEmpty()) {
                liberarDeclaracionesConsumidas(model.getDeclaracionesSeleccionadas(), id);
            }
            declaracionComercializadorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
    
    public String getLastFolioOrigen() {
        List<String> folios = declaracionComercializadorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    public String getLastFolioDesembarqueAc() {
        List<String> folios = declaracionComercializadorRepository.findLastFolioDesembarqueAc();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    private void marcarDeclaracionesComoConsumidas(String idsCSV, Long consumidaPorId, String tipo, Long usuarioDestinatarioId) {
        Map<String, List<Long>> sel = SeleccionTokens.parse(idsCSV);
        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");

        if (!recolectorIds.isEmpty()) {
            for (DeclaracionRecolectorModel r : recolectorRepository.findAllById(recolectorIds)) {
                if (r.getUsuarioDestinatario() != null && r.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && r.getDeclaracionDestinatario() == null) {
                    r.setDeclaracionDestinatario(consumidaPorId);
                    r.setConsumidaPorTipo(tipo);
                    recolectorRepository.save(r);
                }
            }
        }

        if (!armadorIds.isEmpty()) {
            for (DeclaracionArmadorModel a : armadorRepository.findAllById(armadorIds)) {
                if (a.getUsuarioDestinatario() != null && a.getUsuarioDestinatario().getId().equals(usuarioDestinatarioId) && a.getDeclaracionDestinatario() == null) {
                    a.setDeclaracionDestinatario(consumidaPorId);
                    a.setConsumidaPorTipo(tipo);
                    armadorRepository.save(a);
                }
            }
        }

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
        List<Long> recolectorIds = SeleccionTokens.idsParaTipo(sel, "RECOLECTOR");
        List<Long> armadorIds = SeleccionTokens.idsParaTipo(sel, "ARMADOR");
        List<Long> areaIds = SeleccionTokens.idsParaTipo(sel, "AREA");

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

    private void sanearComposicion(DeclaracionComercializadorModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }
}
