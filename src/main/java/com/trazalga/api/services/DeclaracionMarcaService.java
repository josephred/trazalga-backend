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

    public List<DeclaracionMarcaModel> findConFiltros(String marca, Boolean resuelta, String estadoGestion, String declaracionTipo, Date startDate, Date endDate) {
        return repository.findConFiltros(marca, resuelta, estadoGestion, declaracionTipo, startDate, endDate);
    }

    public List<DeclaracionMarcaModel> findConFiltros(String marca, Boolean resuelta, String declaracionTipo, Date startDate, Date endDate) {
        return findConFiltros(marca, resuelta, null, declaracionTipo, startDate, endDate);
    }

    public Map<String, Object> getResumen() {
        List<DeclaracionMarcaModel> todas = repository.findAll();
        long total = todas.size();
        long pendientes = todas.stream().filter(m -> !Boolean.TRUE.equals(m.getResuelta()) && !"DERIVADA_CITACION".equalsIgnoreCase(m.getEstadoGestion())).count();
        long enCitacion = todas.stream().filter(m -> !Boolean.TRUE.equals(m.getResuelta()) && "DERIVADA_CITACION".equalsIgnoreCase(m.getEstadoGestion())).count();
        long resueltas = todas.stream().filter(m -> Boolean.TRUE.equals(m.getResuelta()) || "RESUELTA".equalsIgnoreCase(m.getEstadoGestion())).count();

        Map<String, Long> porMarca = new HashMap<>();
        porMarca.put("EN_VEDA", todas.stream().filter(m -> "EN_VEDA".equals(m.getMarca())).count());
        porMarca.put("LED_EXCEDIDO", todas.stream().filter(m -> "LED_EXCEDIDO".equals(m.getMarca())).count());
        porMarca.put("DESEMBARQUE_ATIPICO", todas.stream().filter(m -> "DESEMBARQUE_ATIPICO".equals(m.getMarca())).count());
        porMarca.put("CUOTA_EXCEDIDA", todas.stream().filter(m -> "CUOTA_EXCEDIDA".equals(m.getMarca())).count());
        porMarca.put("POSTERIOR_CIERRE", todas.stream().filter(m -> "POSTERIOR_CIERRE".equals(m.getMarca())).count());

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("total", total);
        resumen.put("pendientes", pendientes);
        resumen.put("enCitacion", enCitacion);
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

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IDeclaracionArmadorRepository armadorRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IDeclaracionAreaRepository areaRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired(required = false)
    private com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository plantaAbastecimientoRepository;

    public Optional<DeclaracionMarcaModel> resolverMarca(Long id) {
        return repository.findById(id).map(m -> {
            m.setResuelta(true);
            m.setResolucionTipo("LIBERADA");
            m.setObservacionResolucion("Resolución administrativa automática");
            m.setFechaResolucion(new Date());
            m.setEstadoGestion("RESUELTA");
            DeclaracionMarcaModel saved = repository.save(m);
            notificarLiberacionDestinatario(saved);
            return saved;
        });
    }

    public DeclaracionMarcaModel resolverMarca(Long id, String resolucionTipo, String observacion, Long usuarioId) {
        DeclaracionMarcaModel m = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hallazgo #" + id + " no encontrado"));

        if (observacion == null || observacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La observación es obligatoria para resolver el hallazgo.");
        }

        String tipoNorm = (resolucionTipo != null && !resolucionTipo.isBlank())
                ? resolucionTipo.trim().toUpperCase() : "LIBERADA";

        if (!List.of("LIBERADA", "DECOMISO", "SANCION", "DESCARTADA").contains(tipoNorm)) {
            throw new IllegalArgumentException("Tipo de resolución inválido: " + resolucionTipo +
                    ". Opciones permitidas: LIBERADA, DECOMISO, SANCION, DESCARTADA.");
        }

        m.setResuelta(true);
        m.setResolucionTipo(tipoNorm);
        m.setObservacionResolucion(observacion.trim());
        m.setFechaResolucion(new Date());
        m.setResueltaPorUsuarioId(usuarioId);
        m.setEstadoGestion("RESUELTA");

        DeclaracionMarcaModel saved = repository.save(m);

        if ("LIBERADA".equals(tipoNorm) || "DESCARTADA".equals(tipoNorm)) {
            notificarLiberacionDestinatario(saved);
        }

        return saved;
    }

    public Optional<DeclaracionMarcaModel> reabrirMarca(Long id) {
        return repository.findById(id).map(m -> {
            m.setResuelta(false);
            m.setResolucionTipo(null);
            m.setObservacionResolucion(null);
            m.setFechaResolucion(null);
            m.setResueltaPorUsuarioId(null);
            m.setEstadoGestion("PENDIENTE");
            return repository.save(m);
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public DeclaracionMarcaModel derivarACitacion(Long marcaId, String numeroCitacion, Long usuarioId) {
        if (numeroCitacion == null || numeroCitacion.trim().isEmpty()) {
            throw new IllegalArgumentException("El número de citación es obligatorio para derivar a fiscalización.");
        }

        DeclaracionMarcaModel marca = repository.findById(marcaId)
                .orElseThrow(() -> new IllegalArgumentException("Hallazgo #" + marcaId + " no encontrado"));

        marca.setEstadoGestion("DERIVADA_CITACION");
        marca.setObservacionResolucion("Citación N° " + numeroCitacion.trim());
        marca.setResueltaPorUsuarioId(usuarioId);
        marca.setFechaResolucion(new Date());
        // resuelta sigue en false: la citación no cierra el hallazgo hasta su resolución final

        return repository.save(marca);
    }

    @org.springframework.transaction.annotation.Transactional
    public DeclaracionMarcaModel derivarACitacionPorDeclaracion(String declaracionTipo, Long declaracionId, String numeroCitacion, Long usuarioId) {
        if (numeroCitacion == null || numeroCitacion.trim().isEmpty()) {
            throw new IllegalArgumentException("El número de citación es obligatorio para derivar a fiscalización.");
        }
        String tipoNorm = declaracionTipo.trim().toUpperCase();
        DeclaracionMarcaModel marca = repository.findFirstByDeclaracionTipoAndDeclaracionIdAndMarca(tipoNorm, declaracionId, "EN_VEDA")
                .orElseGet(() -> DeclaracionMarcaModel.builder()
                        .declaracionTipo(tipoNorm)
                        .declaracionId(declaracionId)
                        .marca("EN_VEDA")
                        .detalle("Infracción a decreto de veda biológica derivada a citación")
                        .resuelta(false)
                        .build());

        marca.setEstadoGestion("DERIVADA_CITACION");
        marca.setObservacionResolucion("Citación N° " + numeroCitacion.trim());
        marca.setResueltaPorUsuarioId(usuarioId);
        marca.setFechaResolucion(new Date());

        return repository.save(marca);
    }

    private void notificarLiberacionDestinatario(DeclaracionMarcaModel marca) {
        if (notificationService == null || marca.getDeclaracionId() == null || marca.getDeclaracionTipo() == null) {
            return;
        }
        try {
            Long destUserId = null;
            String tipo = marca.getDeclaracionTipo().toUpperCase();
            Long declId = marca.getDeclaracionId();

            if ("ARMADOR".equals(tipo) && armadorRepository != null) {
                destUserId = armadorRepository.findById(declId)
                        .map(com.trazalga.api.models.DeclaracionArmadorModel::getUsuarioDestinatario)
                        .map(com.trazalga.api.models.UsuarioModel::getId).orElse(null);
            } else if ("RECOLECTOR".equals(tipo) && recolectorRepository != null) {
                destUserId = recolectorRepository.findById(declId)
                        .map(com.trazalga.api.models.DeclaracionRecolectorModel::getUsuarioDestinatario)
                        .map(com.trazalga.api.models.UsuarioModel::getId).orElse(null);
            } else if ("AREA".equals(tipo) && areaRepository != null) {
                destUserId = areaRepository.findById(declId)
                        .map(com.trazalga.api.models.DeclaracionAreaModel::getUsuarioDestinatario)
                        .map(com.trazalga.api.models.UsuarioModel::getId).orElse(null);
            } else if ("COMERCIALIZADOR".equals(tipo) && comercializadorRepository != null) {
                destUserId = comercializadorRepository.findById(declId)
                        .map(com.trazalga.api.models.DeclaracionComercializadorModel::getUsuarioDestinatario)
                        .map(com.trazalga.api.models.UsuarioModel::getId).orElse(null);
            } else if ("PLANTA_ABASTECIMIENTO".equals(tipo) && plantaAbastecimientoRepository != null) {
                destUserId = plantaAbastecimientoRepository.findById(declId)
                        .map(com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel::getUsuarioDestinatario)
                        .map(com.trazalga.api.models.UsuarioModel::getId).orElse(null);
            }

            if (destUserId != null) {
                String titulo = "Carga liberada en bodega virtual";
                String mensaje = String.format("La carga %s #%d ha sido liberada por fiscalización (%s). Ya puede ser recepcionada o despachada.",
                        tipo, declId, marca.getResolucionTipo());
                notificationService.sendPushNotificationToUser(destUserId, titulo, mensaje);
            }
        } catch (Exception ex) {
            System.err.println("Error al notificar liberación de carga: " + ex.getMessage());
        }
    }
}
