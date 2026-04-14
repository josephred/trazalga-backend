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

    public List<DeclaracionAreaModel> getAllDeclaraciones() {
        return declaracionAreaRepository.findAll();
    }

    public List<DeclaracionAreaModel> getDeclaracionesByUsuario(Long usuarioId) {
        return declaracionAreaRepository.findAllByUsuarioIdOrderByFechaDeclaracionDesc(usuarioId);
    }

    // NUEVO MÉTODO AÑADIDO
    public List<DeclaracionAreaModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionAreaRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
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
                embarcacionEncontrada = embarcacionRepository.findByCodigo(declaracion.getEmbarcacion().getCodigo());
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
                buzoEncontrado = buzoRepository.findByCodigo(declaracion.getBuzo().getCodigo());
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

        return declaracionAreaRepository.save(declaracion);
    }

    public Optional<DeclaracionAreaModel> getById(Long id) {
        return declaracionAreaRepository.findById(id);
    }

    public DeclaracionAreaModel updateDeclaracion(Long id, DeclaracionAreaModel request) {
        DeclaracionAreaModel declaracion = declaracionAreaRepository.findById(id).orElseThrow();
        
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

        return declaracionAreaRepository.save(declaracion);
    }

    public boolean deleteDeclaracion(Long id) {
        try {
            declaracionAreaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
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