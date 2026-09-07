package com.trazalga.api.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.models.PerfilModel;
import java.util.ArrayList;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DeclaracionAreaService {

    @Autowired
    private IDeclaracionAreaRepository declaracionAreaRepository;

    @Autowired
    private IAmerbRepository amerbRepository;

    @Autowired
    private IEmbarcacionRepository embarcacionRepository;

    @Autowired
    private IBuzoRepository buzoRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired
    private DeclaracionBuzosRepository declaracionBuzosRepository;

    @Autowired
    private ValidacionDeclaracionService validacionDeclaracionService;

    @Autowired
    private CapturaService capturaService;

    public List<DeclaracionAreaModel> getAllDeclaraciones() {
        List<DeclaracionAreaModel> declaraciones = declaracionAreaRepository.findAll();
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    public List<DeclaracionAreaModel> getDeclaracionesByUsuario(Long usuarioId) {
        List<DeclaracionAreaModel> declaraciones = declaracionAreaRepository.findAllByUsuarioIdOrderByFechaDeclaracionDesc(usuarioId);
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    // NUEVO MÉTODO AÑADIDO
    public List<DeclaracionAreaModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        List<DeclaracionAreaModel> declaraciones = declaracionAreaRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    // Variante para el formulario de edición: además de las no consumidas, incluye las
    // que ya consumió la declaración que se está editando (consumidasPorId), para que
    // el formulario pueda re-mostrarlas seleccionadas y recalcular el resumen consolidado.
    public List<DeclaracionAreaModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId, Long consumidasPorId) {
        if (consumidasPorId == null) {
            return getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId);
        }
        List<DeclaracionAreaModel> declaraciones = declaracionAreaRepository.findAsignadasParaEditar(usuarioDestinatarioId, consumidasPorId);
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    @Autowired
    private AlertaTriggerService alertaTriggerService;

    @Autowired
    private GestionMensajeService gestionMensajeService;

    public DeclaracionAreaModel saveDeclaracion(DeclaracionAreaModel declaracion) {
        if (declaracion.getFechaExtraccion() != null && declaracion.getFechaDeclaracion() != null) {
            if (declaracion.getFechaExtraccion().after(declaracion.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de extracción no puede ser posterior a la fecha de declaración.");
            }
        }
        sanearComposicion(declaracion);
        // Si la AMERB tiene datos, intentar encontrar la AMERB real en el servidor
        if (declaracion.getAmerb() != null) {
            AmerbModel amerbEncontrada = null;

            // 0. Si viene con un ID válido, intentar buscar directamente por ID
            if (declaracion.getAmerb().getId() != null) {
                amerbEncontrada = amerbRepository.findById(declaracion.getAmerb().getId()).orElse(null);
            }

            // 1. Buscar por codigoSernapesca
            if (amerbEncontrada == null && declaracion.getAmerb().getCodigoSernapesca() != null) {
                amerbEncontrada = amerbRepository.findByCodigoSernapesca(declaracion.getAmerb().getCodigoSernapesca());
            }

            // 2. Buscar por folioOrganizacion
            if (amerbEncontrada == null && declaracion.getAmerb().getFolioOrganizacion() != null) {
                amerbEncontrada = amerbRepository.findByFolioOrganizacion(declaracion.getAmerb().getFolioOrganizacion());
            }

            // 3. Buscar por nombre (extraer la parte antes del " - ")
            if (amerbEncontrada == null && declaracion.getAmerb().getNombre() != null) {
                String nombreAmerb = declaracion.getAmerb().getNombre();
                String nombreBusqueda = nombreAmerb.contains(" - ")
                    ? nombreAmerb.substring(0, nombreAmerb.indexOf(" - ")).trim()
                    : nombreAmerb.trim();
                List<AmerbModel> amerbsPorNombre = amerbRepository.findByNombreContaining(nombreBusqueda);
                if (!amerbsPorNombre.isEmpty()) {
                    amerbEncontrada = amerbsPorNombre.get(0);
                }
            }

            // Si se encontró la AMERB, usar esa
            if (amerbEncontrada != null) {
                declaracion.setAmerb(amerbEncontrada);
            } else {
                // No existe en la BD local: crear automáticamente con los datos de Sernapesca
                AmerbModel nuevaAmerb = new AmerbModel();
                nuevaAmerb.setNombre(declaracion.getAmerb().getNombre() != null
                    ? declaracion.getAmerb().getNombre() : "AMERB Sin Nombre");
                nuevaAmerb.setRegion(declaracion.getAmerb().getRegion() != null
                    ? declaracion.getAmerb().getRegion() : "Sin Región");
                nuevaAmerb.setUbicacion(declaracion.getAmerb().getUbicacion());
                nuevaAmerb.setFolioOrganizacion(declaracion.getAmerb().getFolioOrganizacion());
                nuevaAmerb.setCodigoSernapesca(declaracion.getAmerb().getCodigoSernapesca());
                nuevaAmerb.setTitular(declaracion.getAmerb().getTitular());
                nuevaAmerb.setEstado("Activo");
                AmerbModel amerbGuardada = amerbRepository.save(nuevaAmerb);
                declaracion.setAmerb(amerbGuardada);
            }
        }

        // Manejar embarcacion
        if (declaracion.getEmbarcacion() != null) {
            EmbarcacionModel embarcacionEncontrada = null;
            if (declaracion.getEmbarcacion().getId() != null) {
                embarcacionEncontrada = embarcacionRepository.findById(declaracion.getEmbarcacion().getId()).orElse(null);
            }
            if (embarcacionEncontrada == null && declaracion.getEmbarcacion().getCodigo() != null) {
                embarcacionEncontrada = embarcacionRepository.findByCodigo(declaracion.getEmbarcacion().getCodigo()).orElse(null);
            }
            if (embarcacionEncontrada != null) {
                declaracion.setEmbarcacion(embarcacionEncontrada);
            } else if (declaracion.getEmbarcacion().getNombre() != null) {
                EmbarcacionModel nuevaEmbarcacion = new EmbarcacionModel();
                nuevaEmbarcacion.setNombre(declaracion.getEmbarcacion().getNombre());
                nuevaEmbarcacion.setCodigo(declaracion.getEmbarcacion().getCodigo());
                EmbarcacionModel embarcacionGuardada = embarcacionRepository.save(nuevaEmbarcacion);
                declaracion.setEmbarcacion(embarcacionGuardada);
            }
        }

        // Manejar buzo
        if (declaracion.getBuzo() != null) {
            BuzoModel buzoEncontrado = null;
            if (declaracion.getBuzo().getId() != null) {
                buzoEncontrado = buzoRepository.findById(declaracion.getBuzo().getId()).orElse(null);
            }
            if (buzoEncontrado == null && declaracion.getBuzo().getCodigo() != null) {
                buzoEncontrado = buzoRepository.findByCodigo(declaracion.getBuzo().getCodigo()).orElse(null);
            }
            if (buzoEncontrado != null) {
                declaracion.setBuzo(buzoEncontrado);
            } else if (declaracion.getBuzo().getNombre() != null) {
                BuzoModel nuevoBuzo = new BuzoModel();
                nuevoBuzo.setNombre(declaracion.getBuzo().getNombre());
                nuevoBuzo.setCodigo(declaracion.getBuzo().getCodigo());
                BuzoModel buzoGuardado = buzoRepository.save(nuevoBuzo);
                declaracion.setBuzo(buzoGuardado);
            }
        }

        // Manejar extraccionTipo si viene en la declaracion
        if (declaracion.getExtraccionTipo() != null && declaracion.getExtraccionTipo().getId() != null) {
            extraccionTipoRepository.findById(declaracion.getExtraccionTipo().getId())
                    .ifPresent(declaracion::setExtraccionTipo);
        }

        // Validación del servidor (Veda, Cuota, LED, Desembarque atípico, y cálculo de Captura)
        Long comunaInscripcionId = (declaracion.getUsuario() != null && declaracion.getUsuario().getComuna() != null)
                ? declaracion.getUsuario().getComuna().getId() : null;
        Long comunaDesembarqueId = null;
        Long regionId = null;

        if (declaracion.getAmerb() != null) {
            if (declaracion.getAmerb().getComuna() != null) {
                comunaDesembarqueId = declaracion.getAmerb().getComuna().getId();
                if (declaracion.getAmerb().getComuna().getRegion() != null) {
                    regionId = declaracion.getAmerb().getComuna().getRegion().getId();
                }
            }
            if (regionId == null && declaracion.getAmerb().getRegionModel() != null) {
                regionId = declaracion.getAmerb().getRegionModel().getId();
            }
        }
        if (regionId == null && declaracion.getCaleta() != null && declaracion.getCaleta().getComuna() != null && declaracion.getCaleta().getComuna().getRegion() != null) {
            regionId = declaracion.getCaleta().getComuna().getRegion().getId();
        }
        if (comunaDesembarqueId == null && declaracion.getCaleta() != null && declaracion.getCaleta().getComuna() != null) {
            comunaDesembarqueId = declaracion.getCaleta().getComuna().getId();
        }

        BigDecimal desembarqueBd = declaracion.getDesembarque() != null ? BigDecimal.valueOf(declaracion.getDesembarque()) : BigDecimal.ZERO;

        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("AREA")
                .usuarioId(declaracion.getUsuario() != null ? declaracion.getUsuario().getId() : null)
                .buzoId(declaracion.getBuzo() != null ? declaracion.getBuzo().getId() : null)
                .embarcacionId(declaracion.getEmbarcacion() != null ? declaracion.getEmbarcacion().getId() : null)
                .amerbId(declaracion.getAmerb() != null ? declaracion.getAmerb().getId() : null)
                .especieId(declaracion.getEspecie() != null ? declaracion.getEspecie().getId() : null)
                .humedadEstadoId(declaracion.getHumedadEstado() != null ? declaracion.getHumedadEstado().getId() : null)
                .extraccionTipoId(declaracion.getExtraccionTipo() != null ? declaracion.getExtraccionTipo().getId() : null)
                .comunaDesembarqueId(comunaDesembarqueId)
                .comunaInscripcionId(comunaInscripcionId)
                .regionId(regionId)
                .fechaExtraccion(declaracion.getFechaExtraccion())
                .fechaDeclaracion(declaracion.getFechaDeclaracion())
                .desembarqueKg(desembarqueBd)
                .build();

        ResultadoValidacion resVal = validacionDeclaracionService.validar(ctx);
        if (resVal.esRechazado()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, resVal.getMotivoRechazo());
        }

        // El servidor es la única autoridad de cálculo: ignora declaracion.captura previa
        if (resVal.getCapturaCalculada() != null) {
            declaracion.setCaptura(resVal.getCapturaCalculada().doubleValue());
        }
        declaracion.setFactorAplicado(resVal.getFactorAplicado());
        declaracion.setFactorConversionId(resVal.getFactorConversionId());

        DeclaracionAreaModel savedDeclaracion = declaracionAreaRepository.save(declaracion);

        // Guardar los buzos asociados en la tabla declaracion_buzos
        if (declaracion.getBuzos() != null && !declaracion.getBuzos().isEmpty()) {
            PerfilModel perfil = savedDeclaracion.getUsuario() != null ? savedDeclaracion.getUsuario().getPerfil() : null;
            for (BuzoModel buzoRequest : declaracion.getBuzos()) {
                if (buzoRequest.getId() != null) {
                    Optional<BuzoModel> buzoOpt = buzoRepository.findById(buzoRequest.getId());
                    buzoOpt.ifPresent(buzo -> {
                        DeclaracionBuzosModel declaracionBuzo = new DeclaracionBuzosModel();
                        declaracionBuzo.setPerfil(perfil);
                        declaracionBuzo.setBuzo(buzo);
                        declaracionBuzo.setDeclaracionArea(savedDeclaracion);
                        declaracionBuzosRepository.save(declaracionBuzo);
                    });
                }
            }
        }

        populateBuzos(savedDeclaracion);
        
        // Procesar marcas de fiscalización y alertas push
        alertaTriggerService.procesarMarcas("AREA", savedDeclaracion.getId(),
                savedDeclaracion.getUsuario() != null ? savedDeclaracion.getUsuario().getId() : null,
                resVal.getMarcas());
        
        return savedDeclaracion;
    }

    public Optional<DeclaracionAreaModel> getById(Long id) {
        Optional<DeclaracionAreaModel> declaracion = declaracionAreaRepository.findById(id);
        declaracion.ifPresent(this::populateBuzos);
        return declaracion;
    }

    public DeclaracionAreaModel updateDeclaracion(Long id, DeclaracionAreaModel request) {
        if (request.getFechaExtraccion() != null && request.getFechaDeclaracion() != null) {
            if (request.getFechaExtraccion().after(request.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de extracción no puede ser posterior a la fecha de declaración.");
            }
        }
        DeclaracionAreaModel declaracion = declaracionAreaRepository.findById(id).orElseThrow();
        if (declaracion.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
        
        declaracion.setFolioOrigen(request.getFolioOrigen());
        declaracion.setFolioDesembarqueAmerb(request.getFolioDesembarqueAmerb());
        declaracion.setFechaExtraccion(request.getFechaExtraccion());
        declaracion.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracion.setHora(request.getHora());
        declaracion.setAmerb(request.getAmerb());
        declaracion.setEspecie(request.getEspecie());
        declaracion.setHumedadEstado(request.getHumedadEstado());
        declaracion.setDesembarque(request.getDesembarque());
        if (request.getExtraccionTipo() != null && request.getExtraccionTipo().getId() != null) {
            extraccionTipoRepository.findById(request.getExtraccionTipo().getId())
                    .ifPresent(declaracion::setExtraccionTipo);
        }

        BigDecimal desBd = declaracion.getDesembarque() != null ? BigDecimal.valueOf(declaracion.getDesembarque()) : null;
        CalculoCapturaResult capRes = capturaService.calcular(
                declaracion.getEspecie() != null ? declaracion.getEspecie().getId() : null,
                declaracion.getHumedadEstado() != null ? declaracion.getHumedadEstado().getId() : null,
                declaracion.getFechaExtraccion(),
                desBd);
        if (capRes.isExitoso()) {
            declaracion.setCaptura(capRes.getCaptura().doubleValue());
            declaracion.setFactorAplicado(capRes.getFactorAplicado());
            declaracion.setFactorConversionId(capRes.getFactorConversionId());
        } else if (request.getCaptura() != null) {
            declaracion.setCaptura(request.getCaptura());
        }
        declaracion.setTipoDestinatario(request.getTipoDestinatario());
        declaracion.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracion.setComposicion(request.getComposicion());
        sanearComposicion(declaracion);
        declaracion.setLatitud(request.getLatitud());
        declaracion.setLongitud(request.getLongitud());
        declaracion.setEmbarcacion(request.getEmbarcacion());
        declaracion.setBuzo(request.getBuzo());
        // Asegurarse de actualizar también el nuevo campo si es necesario
        declaracion.setDeclaracionDestinatario(request.getDeclaracionDestinatario());

        DeclaracionAreaModel updatedDeclaracion = declaracionAreaRepository.save(declaracion);

        // Actualizar buzos: eliminar los anteriores y guardar los nuevos
        List<DeclaracionBuzosModel> buzosAnteriores = declaracionBuzosRepository.findByDeclaracionAreaId(id);
        declaracionBuzosRepository.deleteAll(buzosAnteriores);

        if (request.getBuzos() != null && !request.getBuzos().isEmpty()) {
            PerfilModel perfil = updatedDeclaracion.getUsuario() != null ? updatedDeclaracion.getUsuario().getPerfil() : null;
            for (BuzoModel buzoRequest : request.getBuzos()) {
                if (buzoRequest.getId() != null) {
                    Optional<BuzoModel> buzoOpt = buzoRepository.findById(buzoRequest.getId());
                    buzoOpt.ifPresent(buzo -> {
                        DeclaracionBuzosModel declaracionBuzo = new DeclaracionBuzosModel();
                        declaracionBuzo.setPerfil(perfil);
                        declaracionBuzo.setBuzo(buzo);
                        declaracionBuzo.setDeclaracionArea(updatedDeclaracion);
                        declaracionBuzosRepository.save(declaracionBuzo);
                    });
                }
            }
        }

        populateBuzos(updatedDeclaracion);

        // Notificar al destinatario que la declaración fue modificada
        if (updatedDeclaracion.getUsuarioDestinatario() != null && updatedDeclaracion.getUsuarioDestinatario().getId() != null) {
            gestionMensajeService.notificarModificacion(
                "AREA", updatedDeclaracion.getId(),
                updatedDeclaracion.getUsuario() != null ? updatedDeclaracion.getUsuario().getId() : null,
                updatedDeclaracion.getUsuarioDestinatario().getId(),
                updatedDeclaracion.getFolioOrigen()
            );
        }

        return updatedDeclaracion;
    }

    public boolean deleteDeclaracion(Long id) {
        DeclaracionAreaModel model = declaracionAreaRepository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try {
            // Eliminar buzos asociados primero
            List<DeclaracionBuzosModel> buzos = declaracionBuzosRepository.findByDeclaracionAreaId(id);
            declaracionBuzosRepository.deleteAll(buzos);

            declaracionAreaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void populateBuzos(DeclaracionAreaModel declaracion) {
        if (declaracion != null && declaracion.getId() != null) {
            List<DeclaracionBuzosModel> declaracionBuzos = declaracionBuzosRepository.findByDeclaracionAreaId(declaracion.getId());
            List<BuzoModel> buzos = new ArrayList<>();
            for (DeclaracionBuzosModel db : declaracionBuzos) {
                buzos.add(db.getBuzo());
            }
            declaracion.setBuzos(buzos);
        }
    }

    public String getLastFolioOrigen() {
        List<String> folios = declaracionAreaRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    public String getLastFolioDesembarqueAmerb() {
        List<String> folios = declaracionAreaRepository.findLastFolioDesembarqueAmerb();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    private void sanearComposicion(DeclaracionAreaModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }
}