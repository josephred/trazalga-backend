package com.trazalga.api.services;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    
    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmador() {
        return (ArrayList<DeclaracionArmadorModel>) declaracionArmadorRepository.findAll();
    }

    public ArrayList<DeclaracionArmadorModel> getDeclaracionesArmadorIdUsuario(Long id) {
        return (ArrayList<DeclaracionArmadorModel>) declaracionArmadorRepository.findAllByUsuarioId(id);
    }

    public List<DeclaracionArmadorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionArmadorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }

    public DeclaracionArmadorModel saveDeclaracionArmador(DeclaracionArmadorModel request) {
        DeclaracionArmadorModel declaracion = new DeclaracionArmadorModel();
        
        if (request.getUsuario() != null && request.getUsuario().getId() != null) {
            Optional<UsuarioModel> usuario = usuarioRepository.findById(request.getUsuario().getId());
            usuario.ifPresent(declaracion::setUsuario);
        }
        
        declaracion.setFolioOrigen(request.getFolioOrigen());
        declaracion.setFolioDesembarqueDa(request.getFolioDesembarqueDa());
        declaracion.setFechaExtraccion(parseDate(request.getFechaExtraccion()));
        declaracion.setFechaDeclaracion(parseDate(request.getFechaDeclaracion()));
        declaracion.setHora(request.getHora());
        
        if (request.getEmbarcacion() != null && request.getEmbarcacion().getId() != null) {
            Optional<EmbarcacionModel> embarcacion = embarcacionRepository.findById(request.getEmbarcacion().getId());
            embarcacion.ifPresent(e -> {
                declaracion.setEmbarcacion(e);
                declaracion.setCodigoSernapescaEmbarcacion(e.getCodigo() != null ? e.getCodigo() : String.valueOf(request.getEmbarcacion().getId()));
            });
        }
        
        if (request.getBuzo() != null && request.getBuzo().getId() != null) {
            Optional<BuzoModel> buzo = buzoRepository.findById(request.getBuzo().getId());
            buzo.ifPresent(b -> {
                declaracion.setBuzo(b);
                declaracion.setCodigoSernapescaBuzo(b.getCodigo() != null ? b.getCodigo() : String.valueOf(request.getBuzo().getId()));
            });
        }
        
        declaracion.setDesembarque(parseBigDecimal(request.getDesembarque()));
        declaracion.setCaptura(request.getCaptura());
        declaracion.setTipoDestinatario(request.getTipoDestinatario() != null ? request.getTipoDestinatario() : "comercializador");
        
        String codigoDestinatario = null;
        if (request.getUsuarioDestinatario() != null && request.getUsuarioDestinatario().getRut() != null) {
            codigoDestinatario = request.getUsuarioDestinatario().getRut();
        }
        declaracion.setCodigoDestinatario(codigoDestinatario);
        
        if (request.getUsuarioDestinatario() != null && request.getUsuarioDestinatario().getId() != null) {
            Optional<UsuarioModel> destinatario = usuarioRepository.findById(request.getUsuarioDestinatario().getId());
            destinatario.ifPresent(declaracion::setUsuarioDestinatario);
        }
        
        if (request.getCaleta() != null && request.getCaleta().getId() != null) {
            Optional<CaletaModel> caleta = caletaRepository.findById(request.getCaleta().getId());
            caleta.ifPresent(declaracion::setCaleta);
        }
        
        if (request.getComuna() != null && request.getComuna().getId() != null) {
            Optional<ComunaModel> comuna = comunaRepository.findById(request.getComuna().getId());
            comuna.ifPresent(declaracion::setComuna);
        }
        
        if (request.getEspecie() != null && request.getEspecie().getId() != null) {
            Optional<EspecieModel> especie = especieRepository.findById(request.getEspecie().getId());
            especie.ifPresent(declaracion::setEspecie);
        }
        
        if (request.getComposicion() != null && request.getComposicion().getId() != null) {
            Optional<ComposicionModel> composicion = composicionRepository.findById(request.getComposicion().getId());
            composicion.ifPresent(declaracion::setComposicion);
        }
        
        if (request.getHumedadEstado() != null && request.getHumedadEstado().getId() != null) {
            Optional<HumedadEstadoModel> humedadEstado = humedadEstadoRepository.findById(request.getHumedadEstado().getId());
            humedadEstado.ifPresent(declaracion::setHumedadEstado);
        }
        
        declaracion.setLatitud(request.getLatitud());
        declaracion.setLongitud(request.getLongitud());
        
        return declaracionArmadorRepository.save(declaracion);
    }

    public Optional<DeclaracionArmadorModel> getById(Long id) {
        return declaracionArmadorRepository.findById(id);
    }

    public DeclaracionArmadorModel updateById(DeclaracionArmadorModel request, Long id) {
        DeclaracionArmadorModel declaracionArmadorModel = declaracionArmadorRepository.findById(id).orElse(null);
        if (declaracionArmadorModel == null) {
            return null;
        }

        if (request.getUsuario() != null && request.getUsuario().getId() != null) {
            Optional<UsuarioModel> usuario = usuarioRepository.findById(request.getUsuario().getId());
            usuario.ifPresent(declaracionArmadorModel::setUsuario);
        }

        declaracionArmadorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionArmadorModel.setFolioDesembarqueDa(request.getFolioDesembarqueDa());
        declaracionArmadorModel.setFechaExtraccion(parseDate(request.getFechaExtraccion()));
        declaracionArmadorModel.setFechaDeclaracion(parseDate(request.getFechaDeclaracion()));
        declaracionArmadorModel.setHora(request.getHora());
        
        if (request.getEmbarcacion() != null && request.getEmbarcacion().getId() != null) {
            Optional<EmbarcacionModel> embarcacion = embarcacionRepository.findById(request.getEmbarcacion().getId());
            embarcacion.ifPresent(declaracionArmadorModel::setEmbarcacion);
        }
        
        if (request.getBuzo() != null && request.getBuzo().getId() != null) {
            Optional<BuzoModel> buzo = buzoRepository.findById(request.getBuzo().getId());
            buzo.ifPresent(declaracionArmadorModel::setBuzo);
        }
        
        declaracionArmadorModel.setDesembarque(parseBigDecimal(request.getDesembarque()));
        declaracionArmadorModel.setCaptura(request.getCaptura());
        declaracionArmadorModel.setTipoDestinatario(request.getTipoDestinatario());
        
        if (request.getUsuarioDestinatario() != null && request.getUsuarioDestinatario().getId() != null) {
            Optional<UsuarioModel> destinatario = usuarioRepository.findById(request.getUsuarioDestinatario().getId());
            destinatario.ifPresent(declaracionArmadorModel::setUsuarioDestinatario);
        }
        
        if (request.getCaleta() != null && request.getCaleta().getId() != null) {
            Optional<CaletaModel> caleta = caletaRepository.findById(request.getCaleta().getId());
            caleta.ifPresent(declaracionArmadorModel::setCaleta);
        }
        
        if (request.getComuna() != null && request.getComuna().getId() != null) {
            Optional<ComunaModel> comuna = comunaRepository.findById(request.getComuna().getId());
            comuna.ifPresent(declaracionArmadorModel::setComuna);
        }
        
        if (request.getEspecie() != null && request.getEspecie().getId() != null) {
            Optional<EspecieModel> especie = especieRepository.findById(request.getEspecie().getId());
            especie.ifPresent(declaracionArmadorModel::setEspecie);
        }
        
        if (request.getComposicion() != null && request.getComposicion().getId() != null) {
            Optional<ComposicionModel> composicion = composicionRepository.findById(request.getComposicion().getId());
            composicion.ifPresent(declaracionArmadorModel::setComposicion);
        }
        
        if (request.getHumedadEstado() != null && request.getHumedadEstado().getId() != null) {
            Optional<HumedadEstadoModel> humedadEstado = humedadEstadoRepository.findById(request.getHumedadEstado().getId());
            humedadEstado.ifPresent(declaracionArmadorModel::setHumedadEstado);
        }
        
        declaracionArmadorModel.setLatitud(request.getLatitud());
        declaracionArmadorModel.setLongitud(request.getLongitud());

        return declaracionArmadorRepository.save(declaracionArmadorModel);
    }

    public Boolean deleteDeclaracionArmador(Long id) {
        try {
            declaracionArmadorRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
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
