package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.repositories.IBuzoRepository;

@Service
public class DeclaracionRecolectorService {
    
    @Autowired
    IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    private DeclaracionBuzosRepository declaracionBuzosRepository;

    @Autowired
    private IBuzoRepository buzoRepository;

    @Autowired
    private ValidacionDeclaracionService validacionDeclaracionService;

    @Autowired
    private CapturaService capturaService;
    
    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolector(){
        ArrayList<DeclaracionRecolectorModel> list = (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAll();
        list.forEach(this::populateBuzos);
        return list;
    }

    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolectorIdUsuario(Long id){
        ArrayList<DeclaracionRecolectorModel> list = (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAllByUsuarioId(id);
        list.forEach(this::populateBuzos);
        return list;
    }

    public List<DeclaracionRecolectorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        List<DeclaracionRecolectorModel> list = declaracionRecolectorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        list.forEach(this::populateBuzos);
        return list;
    }

    // Variante para el formulario de edición: además de las no consumidas, incluye las
    // que ya consumió la declaración que se está editando (consumidasPorId), para que
    // el formulario pueda re-mostrarlas seleccionadas y recalcular el resumen consolidado.
    public List<DeclaracionRecolectorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId, Long consumidasPorId) {
        if (consumidasPorId == null) {
            return getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(usuarioDestinatarioId);
        }
        List<DeclaracionRecolectorModel> list = declaracionRecolectorRepository.findAsignadasParaEditar(usuarioDestinatarioId, consumidasPorId);
        list.forEach(this::populateBuzos);
        return list;
    }


    @Autowired
    private AlertaTriggerService alertaTriggerService;

    @Autowired
    private GestionMensajeService gestionMensajeService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private void resolveDependencies(DeclaracionRecolectorModel model) {
        if (model.getUsuario() != null && model.getUsuario().getId() != null) {
            model.setUsuario(entityManager.find(com.trazalga.api.models.UsuarioModel.class, model.getUsuario().getId()));
        }
        if (model.getCaleta() != null && model.getCaleta().getId() != null) {
            model.setCaleta(entityManager.find(com.trazalga.api.models.CaletaModel.class, model.getCaleta().getId()));
        }
        if (model.getEspecie() != null && model.getEspecie().getId() != null) {
            model.setEspecie(entityManager.find(com.trazalga.api.models.EspecieModel.class, model.getEspecie().getId()));
        }
        if (model.getComuna() != null && model.getComuna().getId() != null) {
            model.setComuna(entityManager.find(com.trazalga.api.models.ComunaModel.class, model.getComuna().getId()));
        }
        if (model.getExtraccionTipo() != null && model.getExtraccionTipo().getId() != null) {
            model.setExtraccionTipo(entityManager.find(com.trazalga.api.models.ExtraccionTipoModel.class, model.getExtraccionTipo().getId()));
        }
        if (model.getComposicion() != null && model.getComposicion().getId() != null) {
            model.setComposicion(entityManager.find(com.trazalga.api.models.ComposicionModel.class, model.getComposicion().getId()));
        }
        if (model.getHumedadEstado() != null && model.getHumedadEstado().getId() != null) {
            model.setHumedadEstado(entityManager.find(com.trazalga.api.models.HumedadEstadoModel.class, model.getHumedadEstado().getId()));
        }
        if (model.getUsuarioDestinatario() != null && model.getUsuarioDestinatario().getId() != null) {
            model.setUsuarioDestinatario(entityManager.find(com.trazalga.api.models.UsuarioModel.class, model.getUsuarioDestinatario().getId()));
        }
    }

    public DeclaracionRecolectorModel saveDeclaracionRecolector(DeclaracionRecolectorModel declaracionRecolectorModel){
        if (declaracionRecolectorModel.getFechaDeclaracion() != null) {
            if (declaracionRecolectorModel.getFechaExtraccion() != null && declaracionRecolectorModel.getFechaExtraccion().after(declaracionRecolectorModel.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de extracción no puede ser posterior a la fecha de declaración.");
            }
            if (declaracionRecolectorModel.getPeriodoExtraccionInicio() != null && declaracionRecolectorModel.getPeriodoExtraccionInicio().after(declaracionRecolectorModel.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período de inicio de extracción no puede ser posterior a la fecha de declaración.");
            }
            if (declaracionRecolectorModel.getPeriodoExtraccionFin() != null && declaracionRecolectorModel.getPeriodoExtraccionFin().after(declaracionRecolectorModel.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período de fin de extracción no puede ser posterior a la fecha de declaración.");
            }
        }
        sanearComposicion(declaracionRecolectorModel);
        calcularTasaDiaria(declaracionRecolectorModel);
        resolveDependencies(declaracionRecolectorModel);

        // Validación del servidor (Veda, Cuota, LED, Desembarque atípico, y cálculo de Captura)
        Long comunaInscripcionId = (declaracionRecolectorModel.getUsuario() != null && declaracionRecolectorModel.getUsuario().getComuna() != null)
                ? declaracionRecolectorModel.getUsuario().getComuna().getId() : null;
        Long comunaDesembarqueId = declaracionRecolectorModel.getComuna() != null ? declaracionRecolectorModel.getComuna().getId() : null;
        Long regionId = (declaracionRecolectorModel.getComuna() != null && declaracionRecolectorModel.getComuna().getRegion() != null)
                ? declaracionRecolectorModel.getComuna().getRegion().getId() : null;

        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR")
                .usuarioId(declaracionRecolectorModel.getUsuario() != null ? declaracionRecolectorModel.getUsuario().getId() : null)
                .especieId(declaracionRecolectorModel.getEspecie() != null ? declaracionRecolectorModel.getEspecie().getId() : null)
                .humedadEstadoId(declaracionRecolectorModel.getHumedadEstado() != null ? declaracionRecolectorModel.getHumedadEstado().getId() : null)
                .extraccionTipoId(declaracionRecolectorModel.getExtraccionTipo() != null ? declaracionRecolectorModel.getExtraccionTipo().getId() : null)
                .comunaDesembarqueId(comunaDesembarqueId)
                .comunaInscripcionId(comunaInscripcionId)
                .regionId(regionId)
                .fechaExtraccion(declaracionRecolectorModel.getFechaExtraccion())
                .fechaDeclaracion(declaracionRecolectorModel.getFechaDeclaracion())
                .desembarqueKg(declaracionRecolectorModel.getDesembarque())
                .build();

        ResultadoValidacion resVal = validacionDeclaracionService.validar(ctx);
        if (resVal.esRechazado()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, resVal.getMotivoRechazo());
        }

        // El servidor es la única autoridad de cálculo: ignora request.captura
        declaracionRecolectorModel.setCaptura(resVal.getCapturaCalculada());
        declaracionRecolectorModel.setFactorAplicado(resVal.getFactorAplicado());
        declaracionRecolectorModel.setFactorConversionId(resVal.getFactorConversionId());

        DeclaracionRecolectorModel saved = declaracionRecolectorRepository.save(declaracionRecolectorModel);

        // Guardar buzos
        if (declaracionRecolectorModel.getBuzos() != null && !declaracionRecolectorModel.getBuzos().isEmpty()) {
            PerfilModel perfil = saved.getUsuario() != null ? saved.getUsuario().getPerfil() : null;
            for (BuzoModel buzoReq : declaracionRecolectorModel.getBuzos()) {
                if (buzoReq.getId() != null) {
                    buzoRepository.findById(buzoReq.getId()).ifPresent(buzo -> {
                        DeclaracionBuzosModel db = new DeclaracionBuzosModel();
                        db.setPerfil(perfil);
                        db.setBuzo(buzo);
                        db.setDeclaracionRecolector(saved);
                        declaracionBuzosRepository.save(db);
                    });
                }
            }
        }
        populateBuzos(saved);
        
        // Procesar marcas de fiscalización y notificaciones push
        alertaTriggerService.procesarMarcas("RECOLECTOR", saved.getId(),
                saved.getUsuario() != null ? saved.getUsuario().getId() : null,
                resVal.getMarcas());
        
        return saved;
    }

    public Optional<DeclaracionRecolectorModel> getById(Long id){
        Optional<DeclaracionRecolectorModel> opt = declaracionRecolectorRepository.findById(id);
        opt.ifPresent(this::populateBuzos);
        return opt;
    }

    public DeclaracionRecolectorModel updateById(DeclaracionRecolectorModel request, Long id){
        if (request.getFechaDeclaracion() != null) {
            if (request.getFechaExtraccion() != null && request.getFechaExtraccion().after(request.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de extracción no puede ser posterior a la fecha de declaración.");
            }
            if (request.getPeriodoExtraccionInicio() != null && request.getPeriodoExtraccionInicio().after(request.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período de inicio de extracción no puede ser posterior a la fecha de declaración.");
            }
            if (request.getPeriodoExtraccionFin() != null && request.getPeriodoExtraccionFin().after(request.getFechaDeclaracion())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período de fin de extracción no puede ser posterior a la fecha de declaración.");
            }
        }
        DeclaracionRecolectorModel declaracionRecolectorModel = declaracionRecolectorRepository.findById(id).get();
        if (declaracionRecolectorModel.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
        resolveDependencies(request);
        declaracionRecolectorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionRecolectorModel.setFolioDesembarqueRo(request.getFolioDesembarqueRo());
        declaracionRecolectorModel.setFechaExtraccion(request.getFechaExtraccion());
        declaracionRecolectorModel.setPeriodoExtraccionInicio(request.getPeriodoExtraccionInicio());
        declaracionRecolectorModel.setPeriodoExtraccionFin(request.getPeriodoExtraccionFin());
        declaracionRecolectorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionRecolectorModel.setHora(request.getHora());
        declaracionRecolectorModel.setNombre(request.getNombre());
        declaracionRecolectorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionRecolectorModel.setVaradero(request.getVaradero());        
        declaracionRecolectorModel.setCaleta(request.getCaleta());
        //declaracionRecolectorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionRecolectorModel.setLatitud(request.getLatitud());
        declaracionRecolectorModel.setLongitud(request.getLongitud());
        declaracionRecolectorModel.setEspecie(request.getEspecie());
        declaracionRecolectorModel.setComuna(request.getComuna());
        declaracionRecolectorModel.setExtraccionTipo(request.getExtraccionTipo());
        declaracionRecolectorModel.setComposicion(request.getComposicion());
        declaracionRecolectorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionRecolectorModel.setHumedad(request.getHumedad());
        declaracionRecolectorModel.setDesembarque(request.getDesembarque());

        // Recalcular captura con autoridad del servidor
        CalculoCapturaResult capRes = capturaService.calcular(
                declaracionRecolectorModel.getEspecie() != null ? declaracionRecolectorModel.getEspecie().getId() : null,
                declaracionRecolectorModel.getHumedadEstado() != null ? declaracionRecolectorModel.getHumedadEstado().getId() : null,
                declaracionRecolectorModel.getFechaExtraccion(),
                declaracionRecolectorModel.getDesembarque());
        if (capRes.isExitoso()) {
            declaracionRecolectorModel.setCaptura(capRes.getCaptura());
            declaracionRecolectorModel.setFactorAplicado(capRes.getFactorAplicado());
            declaracionRecolectorModel.setFactorConversionId(capRes.getFactorConversionId());
        }

        declaracionRecolectorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        declaracionRecolectorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        
        sanearComposicion(declaracionRecolectorModel);
        calcularTasaDiaria(declaracionRecolectorModel);
        DeclaracionRecolectorModel updated = declaracionRecolectorRepository.save(declaracionRecolectorModel);

        // Actualizar buzos
        List<DeclaracionBuzosModel> anteriores = declaracionBuzosRepository.findByDeclaracionRecolectorId(id);
        declaracionBuzosRepository.deleteAll(anteriores);

        if (request.getBuzos() != null && !request.getBuzos().isEmpty()) {
            PerfilModel perfil = updated.getUsuario() != null ? updated.getUsuario().getPerfil() : null;
            for (BuzoModel buzoReq : request.getBuzos()) {
                if (buzoReq.getId() != null) {
                    buzoRepository.findById(buzoReq.getId()).ifPresent(buzo -> {
                        DeclaracionBuzosModel db = new DeclaracionBuzosModel();
                        db.setPerfil(perfil);
                        db.setBuzo(buzo);
                        db.setDeclaracionRecolector(updated);
                        declaracionBuzosRepository.save(db);
                    });
                }
            }
        }
        populateBuzos(updated);

        // Notificar al destinatario que la declaración fue modificada
        if (updated.getUsuarioDestinatario() != null && updated.getUsuarioDestinatario().getId() != null) {
            gestionMensajeService.notificarModificacion(
                "RECOLECTOR", updated.getId(),
                updated.getUsuario() != null ? updated.getUsuario().getId() : null,
                updated.getUsuarioDestinatario().getId(),
                updated.getFolioOrigen()
            );
        }

        return updated;
    }

    public Boolean deleteDeclaracionRecolector(Long id){
        DeclaracionRecolectorModel model = declaracionRecolectorRepository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try{
            List<DeclaracionBuzosModel> buzos = declaracionBuzosRepository.findByDeclaracionRecolectorId(id);
            declaracionBuzosRepository.deleteAll(buzos);
            declaracionRecolectorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }

    private void populateBuzos(DeclaracionRecolectorModel model) {
        if (model != null && model.getId() != null) {
            List<DeclaracionBuzosModel> dbList = declaracionBuzosRepository.findByDeclaracionRecolectorId(model.getId());
            List<BuzoModel> buzos = new ArrayList<>();
            for (DeclaracionBuzosModel db : dbList) {
                buzos.add(db.getBuzo());
            }
            model.setBuzos(buzos);
        }
    }

    public String getLastFolioOrigen() {
        List<String> folios = declaracionRecolectorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    public String getLastFolioDesembarqueRo() {
        List<String> folios = declaracionRecolectorRepository.findLastFolioDesembarqueRo();
        return folios.isEmpty() ? null : folios.getFirst();
    }


    /**
     * Calcula la tasa diaria de recolección y sincroniza fechaExtraccion.
     * Fórmula: tasa = desembarque / (DATEDIFF(fin, inicio) + 1)
     * Mantiene retrocompatibilidad: fechaExtraccion = periodoExtraccionFin
     */
    private void calcularTasaDiaria(DeclaracionRecolectorModel model) {
        if (model.getPeriodoExtraccionInicio() != null
                && model.getPeriodoExtraccionFin() != null
                && model.getDesembarque() != null) {

            long diffMs = model.getPeriodoExtraccionFin().getTime()
                        - model.getPeriodoExtraccionInicio().getTime();
            long dias = TimeUnit.DAYS.convert(diffMs, TimeUnit.MILLISECONDS) + 1;

            if (dias <= 0) {
                throw new IllegalArgumentException(
                    "Periodo de extracción inválido: la fecha de inicio no puede ser posterior a la fecha de fin.");
            }

            BigDecimal tasaDiaria = model.getDesembarque()
                .divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP);
            model.setTasaDiariaRecoleccion(tasaDiaria);

            // Retrocompatibilidad: fecha_extraccion = fecha fin del periodo
            model.setFechaExtraccion(model.getPeriodoExtraccionFin());
        }
    }

    private void sanearComposicion(DeclaracionRecolectorModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }

}
