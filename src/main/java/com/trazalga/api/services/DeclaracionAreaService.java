package com.trazalga.api.services;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.models.PerfilModel;
import java.util.ArrayList;

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
    private DeclaracionBuzosRepository declaracionBuzosRepository;

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

    public DeclaracionAreaModel saveDeclaracion(DeclaracionAreaModel declaracion) {
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
        return savedDeclaracion;
    }

    public Optional<DeclaracionAreaModel> getById(Long id) {
        Optional<DeclaracionAreaModel> declaracion = declaracionAreaRepository.findById(id);
        declaracion.ifPresent(this::populateBuzos);
        return declaracion;
    }

    public DeclaracionAreaModel updateDeclaracion(Long id, DeclaracionAreaModel request) {
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
        declaracion.setCaptura(request.getCaptura());
        declaracion.setDesembarque(request.getDesembarque());
        declaracion.setTipoDestinatario(request.getTipoDestinatario());
        declaracion.setUsuarioDestinatario(request.getUsuarioDestinatario());
        declaracion.setComposicion(request.getComposicion());
        declaracion.setHumedadEstado(request.getHumedadEstado());
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
}