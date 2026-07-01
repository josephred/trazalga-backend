package com.trazalga.api.services;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;

@Service
public class DeclaracionArmadorService {
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;
    
    @Autowired
    private IUsuarioRepository usuarioRepository;
    
    @Autowired
    private IEmbarcacionRepository embarcacionRepository;
    
    @Autowired
    private IBuzoRepository buzoRepository;
    
    @Autowired
    private ICaletaRepository caletaRepository;
    
    @Autowired
    private IEspecieRepository especieRepository;
    
    @Autowired
    private IComposicionRepository composicionRepository;
    
    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;
    
    @Autowired
    private IComunaRepository comunaRepository;

    @Autowired
    private DeclaracionBuzosRepository declaracionBuzosRepository;
    
    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmador() {
        ArrayList<DeclaracionArmadorModel> declaraciones = (ArrayList<DeclaracionArmadorModel>) declaracionArmadorRepository.findAll();
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmadorIdUsuario(Long id) {
        ArrayList<DeclaracionArmadorModel> declaraciones = (ArrayList<DeclaracionArmadorModel>) declaracionArmadorRepository.findAllByUsuarioId(id);
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    public List<DeclaracionArmadorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        List<DeclaracionArmadorModel> declaraciones = declaracionArmadorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        declaraciones.forEach(this::populateBuzos);
        return declaraciones;
    }

    @Autowired
    private AlertaTriggerService alertaTriggerService;

    public DeclaracionArmadorModel saveDeclaracionArmador(DeclaracionArmadorModel request) {
        DeclaracionArmadorModel declaracion = new DeclaracionArmadorModel();
        
        if (request.getUsuario() == null || request.getUsuario().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario es obligatorio.");
        }
        UsuarioModel usuario = usuarioRepository.findById(request.getUsuario().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario especificado no existe."));
        declaracion.setUsuario(usuario);
        
        declaracion.setFolioOrigen(request.getFolioOrigen());
        declaracion.setFolioDesembarqueDa(request.getFolioDesembarqueDa());
        declaracion.setFechaExtraccion(parseDate(request.getFechaExtraccion()));
        declaracion.setFechaDeclaracion(parseDate(request.getFechaDeclaracion()));
        declaracion.setHora(request.getHora());
        
        if (request.getEmbarcacion() == null || request.getEmbarcacion().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La embarcación es obligatoria.");
        }
        EmbarcacionModel embarcacion = embarcacionRepository.findById(request.getEmbarcacion().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La embarcación especificada no existe."));
        declaracion.setEmbarcacion(embarcacion);
        declaracion.setCodigoSernapescaEmbarcacion(embarcacion.getCodigo() != null ? embarcacion.getCodigo() : String.valueOf(request.getEmbarcacion().getId()));
        
        if (request.getBuzo() == null || request.getBuzo().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El buzo (patrón/tripulante principal) es obligatorio.");
        }
        BuzoModel buzo = buzoRepository.findById(request.getBuzo().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El buzo especificado no existe."));
        declaracion.setBuzo(buzo);
        declaracion.setCodigoSernapescaBuzo(buzo.getCodigo() != null ? buzo.getCodigo() : String.valueOf(request.getBuzo().getId()));
        
        declaracion.setDesembarque(parseBigDecimal(request.getDesembarque()));
        declaracion.setCaptura(request.getCaptura());
        declaracion.setTipoDestinatario(request.getTipoDestinatario() != null ? request.getTipoDestinatario() : "comercializador");
        
        String codigoDestinatario = null;
        if (request.getUsuarioDestinatario() != null && request.getUsuarioDestinatario().getRut() != null) {
            codigoDestinatario = request.getUsuarioDestinatario().getRut();
        }
        declaracion.setCodigoDestinatario(codigoDestinatario);
        
        if (request.getUsuarioDestinatario() == null || request.getUsuarioDestinatario().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario es obligatorio.");
        }
        UsuarioModel usuarioDestinatario = usuarioRepository.findById(request.getUsuarioDestinatario().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario especificado no existe."));
        declaracion.setUsuarioDestinatario(usuarioDestinatario);
        
        if (request.getCaleta() == null || request.getCaleta().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caleta es obligatoria.");
        }
        CaletaModel caleta = caletaRepository.findById(request.getCaleta().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caleta especificada no existe."));
        declaracion.setCaleta(caleta);
        
        if (request.getComuna() != null && request.getComuna().getId() != null) {
            Optional<ComunaModel> comuna = comunaRepository.findById(request.getComuna().getId());
            comuna.ifPresent(declaracion::setComuna);
        }
        
        if (request.getEspecie() == null || request.getEspecie().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La especie es obligatoria.");
        }
        EspecieModel especie = especieRepository.findById(request.getEspecie().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La especie especificada no existe."));
        declaracion.setEspecie(especie);
        
        if (request.getComposicion() != null && request.getComposicion().getId() != null) {
            Optional<ComposicionModel> composicion = composicionRepository.findById(request.getComposicion().getId());
            composicion.ifPresent(declaracion::setComposicion);
        }
        
        if (request.getHumedadEstado() == null || request.getHumedadEstado().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado de humedad es obligatorio.");
        }
        HumedadEstadoModel humedadEstado = humedadEstadoRepository.findById(request.getHumedadEstado().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado de humedad especificado no existe."));
        declaracion.setHumedadEstado(humedadEstado);
        
        declaracion.setLatitud(request.getLatitud());
        declaracion.setLongitud(request.getLongitud());
        
        DeclaracionArmadorModel savedDeclaracion = declaracionArmadorRepository.save(declaracion);

        // Guardar los buzos asociados en la tabla declaracion_buzos
        if (request.getBuzos() != null && !request.getBuzos().isEmpty()) {
            PerfilModel perfil = savedDeclaracion.getUsuario() != null ? savedDeclaracion.getUsuario().getPerfil() : null;
            for (BuzoModel buzoRequest : request.getBuzos()) {
                if (buzoRequest.getId() != null) {
                    Optional<BuzoModel> buzoOpt = buzoRepository.findById(buzoRequest.getId());
                    buzoOpt.ifPresent(b -> {
                        DeclaracionBuzosModel declaracionBuzo = new DeclaracionBuzosModel();
                        declaracionBuzo.setPerfil(perfil);
                        declaracionBuzo.setBuzo(b);
                        declaracionBuzo.setDeclaracionArmador(savedDeclaracion);
                        declaracionBuzosRepository.save(declaracionBuzo);
                    });
                }
            }
        }

        populateBuzos(savedDeclaracion);
        
        // Trigger Alertas
        alertaTriggerService.evaluarDeclaracion(
            savedDeclaracion.getEspecie() != null ? savedDeclaracion.getEspecie().getId() : null,
            savedDeclaracion.getUsuario() != null ? savedDeclaracion.getUsuario().getId() : null,
            savedDeclaracion.getDesembarque() != null ? savedDeclaracion.getDesembarque().doubleValue() : 0.0,
            "ARMADOR"
        );
        
        return savedDeclaracion;
    }

    public Optional<DeclaracionArmadorModel> getById(Long id) {
        Optional<DeclaracionArmadorModel> declaracion = declaracionArmadorRepository.findById(id);
        declaracion.ifPresent(this::populateBuzos);
        return declaracion;
    }

    public DeclaracionArmadorModel updateById(DeclaracionArmadorModel request, Long id) {
        DeclaracionArmadorModel declaracionArmadorModel = declaracionArmadorRepository.findById(id).orElse(null);
        if (declaracionArmadorModel == null) {
            return null;
        }
        if (declaracionArmadorModel.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }

        if (request.getUsuario() == null || request.getUsuario().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario es obligatorio.");
        }
        UsuarioModel usuario = usuarioRepository.findById(request.getUsuario().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario especificado no existe."));
        declaracionArmadorModel.setUsuario(usuario);

        declaracionArmadorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionArmadorModel.setFolioDesembarqueDa(request.getFolioDesembarqueDa());
        declaracionArmadorModel.setFechaExtraccion(parseDate(request.getFechaExtraccion()));
        declaracionArmadorModel.setFechaDeclaracion(parseDate(request.getFechaDeclaracion()));
        declaracionArmadorModel.setHora(request.getHora());
        
        if (request.getEmbarcacion() == null || request.getEmbarcacion().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La embarcación es obligatoria.");
        }
        EmbarcacionModel embarcacion = embarcacionRepository.findById(request.getEmbarcacion().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La embarcación especificada no existe."));
        declaracionArmadorModel.setEmbarcacion(embarcacion);
        
        if (request.getBuzo() == null || request.getBuzo().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El buzo (patrón/tripulante principal) es obligatorio.");
        }
        BuzoModel buzo = buzoRepository.findById(request.getBuzo().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El buzo especificado no existe."));
        declaracionArmadorModel.setBuzo(buzo);
        
        declaracionArmadorModel.setDesembarque(parseBigDecimal(request.getDesembarque()));
        declaracionArmadorModel.setCaptura(request.getCaptura());
        declaracionArmadorModel.setTipoDestinatario(request.getTipoDestinatario());
        
        if (request.getUsuarioDestinatario() == null || request.getUsuarioDestinatario().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario es obligatorio.");
        }
        UsuarioModel usuarioDestinatario = usuarioRepository.findById(request.getUsuarioDestinatario().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El destinatario especificado no existe."));
        declaracionArmadorModel.setUsuarioDestinatario(usuarioDestinatario);
        
        if (request.getCaleta() == null || request.getCaleta().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caleta es obligatoria.");
        }
        CaletaModel caleta = caletaRepository.findById(request.getCaleta().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La caleta especificada no existe."));
        declaracionArmadorModel.setCaleta(caleta);
        
        if (request.getComuna() != null && request.getComuna().getId() != null) {
            Optional<ComunaModel> comuna = comunaRepository.findById(request.getComuna().getId());
            comuna.ifPresent(declaracionArmadorModel::setComuna);
        }
        
        if (request.getEspecie() == null || request.getEspecie().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La especie es obligatoria.");
        }
        EspecieModel especie = especieRepository.findById(request.getEspecie().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "La especie especificada no existe."));
        declaracionArmadorModel.setEspecie(especie);
        
        if (request.getComposicion() != null && request.getComposicion().getId() != null) {
            Optional<ComposicionModel> composicion = composicionRepository.findById(request.getComposicion().getId());
            composicion.ifPresent(declaracionArmadorModel::setComposicion);
        }
        
        if (request.getHumedadEstado() == null || request.getHumedadEstado().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado de humedad es obligatorio.");
        }
        HumedadEstadoModel humedadEstado = humedadEstadoRepository.findById(request.getHumedadEstado().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado de humedad especificado no existe."));
        declaracionArmadorModel.setHumedadEstado(humedadEstado);
        
        declaracionArmadorModel.setLatitud(request.getLatitud());
        declaracionArmadorModel.setLongitud(request.getLongitud());

        DeclaracionArmadorModel updatedDeclaracion = declaracionArmadorRepository.save(declaracionArmadorModel);

        // Actualizar buzos: eliminar los anteriores y guardar los nuevos
        List<DeclaracionBuzosModel> buzosAnteriores = declaracionBuzosRepository.findByDeclaracionArmadorId(id);
        declaracionBuzosRepository.deleteAll(buzosAnteriores);

        if (request.getBuzos() != null && !request.getBuzos().isEmpty()) {
            PerfilModel perfil = updatedDeclaracion.getUsuario() != null ? updatedDeclaracion.getUsuario().getPerfil() : null;
            for (BuzoModel buzoRequest : request.getBuzos()) {
                if (buzoRequest.getId() != null) {
                    Optional<BuzoModel> buzoOpt = buzoRepository.findById(buzoRequest.getId());
                    buzoOpt.ifPresent(b -> {
                        DeclaracionBuzosModel declaracionBuzo = new DeclaracionBuzosModel();
                        declaracionBuzo.setPerfil(perfil);
                        declaracionBuzo.setBuzo(b);
                        declaracionBuzo.setDeclaracionArmador(updatedDeclaracion);
                        declaracionBuzosRepository.save(declaracionBuzo);
                    });
                }
            }
        }

        populateBuzos(updatedDeclaracion);
        return updatedDeclaracion;
    }

    public Boolean deleteDeclaracionArmador(Long id) {
        DeclaracionArmadorModel model = declaracionArmadorRepository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try {
            // Eliminar buzos asociados primero
            List<DeclaracionBuzosModel> buzos = declaracionBuzosRepository.findByDeclaracionArmadorId(id);
            declaracionBuzosRepository.deleteAll(buzos);
            
            declaracionArmadorRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void populateBuzos(DeclaracionArmadorModel declaracion) {
        if (declaracion != null && declaracion.getId() != null) {
            List<DeclaracionBuzosModel> declaracionBuzos = declaracionBuzosRepository.findByDeclaracionArmadorId(declaracion.getId());
            List<BuzoModel> buzos = new ArrayList<>();
            for (DeclaracionBuzosModel db : declaracionBuzos) {
                buzos.add(db.getBuzo());
            }
            declaracion.setBuzos(buzos);
        }
    }

    public String getLastFolioOrigen() {
        List<String> folios = declaracionArmadorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    public String getLastFolioDesembarqueDa() {
        List<String> folios = declaracionArmadorRepository.findLastFolioDesembarqueDa();
        return folios.isEmpty() ? null : folios.getFirst();
    }
    
    private Date parseDate(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        if (dateObj instanceof Date) {
            return (Date) dateObj;
        }
        try {
            return dateFormat.parse(dateObj.toString());
        } catch (ParseException e) {
            return null;
        }
    }
    
    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
