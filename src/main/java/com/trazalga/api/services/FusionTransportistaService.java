package com.trazalga.api.services;

import com.trazalga.api.dto.CandidatoFusionDTO;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaFusionLogModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.repositories.*;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FusionTransportistaService {

    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();

    @Autowired
    private ITransportistaRepository transportistaRepository;

    @Autowired
    private ITransportistaAliasRepository aliasRepository;

    @Autowired
    private ITransportistaFusionLogRepository fusionLogRepository;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    /**
     * TT.4 · Busca candidatos a fusión según Jaro-Winkler >= 0.92, mismo nombre con RUT distinto,
     * o mismo nombre con y sin RUT. Nunca fusiona automáticamente.
     */
    @Transactional(readOnly = true)
    public List<CandidatoFusionDTO> obtenerCandidatos(Double similitudMinOverride) {
        double umbral = similitudMinOverride != null ? similitudMinOverride
                : (configService != null ? configService.getDouble("transportista_similitud_min", 0.92) : 0.92);

        List<TransportistaModel> activos = transportistaRepository.findAllActivos();
        List<CandidatoFusionDTO> candidatos = new ArrayList<>();
        Set<String> paresVistos = new HashSet<>();

        for (int i = 0; i < activos.size(); i++) {
            TransportistaModel t1 = activos.get(i);
            String norm1 = t1.getNombreNormalizado();

            for (int j = i + 1; j < activos.size(); j++) {
                TransportistaModel t2 = activos.get(j);
                String norm2 = t2.getNombreNormalizado();

                String pairKey = Math.min(t1.getId(), t2.getId()) + "-" + Math.max(t1.getId(), t2.getId());
                if (paresVistos.contains(pairKey)) continue;

                // 1. Mismo nombre normalizado con y sin RUT
                boolean unoSinRutYUnoConRut = (t1.getRut() == null || t1.getRut().isBlank()) != (t2.getRut() == null || t2.getRut().isBlank());
                if (norm1.equals(norm2) && unoSinRutYUnoConRut) {
                    paresVistos.add(pairKey);
                    candidatos.add(crearCandidato(t1, t2, 1.0, "MISMO_NOMBRE_CON_Y_SIN_RUT"));
                    continue;
                }

                // 2. Mismo nombre normalizado con RUT distinto
                if (norm1.equals(norm2) && t1.getRut() != null && t2.getRut() != null && !t1.getRut().equals(t2.getRut())) {
                    paresVistos.add(pairKey);
                    candidatos.add(crearCandidato(t1, t2, 1.0, "MISMO_NOMBRE_RUT_DISTINTO"));
                    continue;
                }

                // 3. Jaro-Winkler >= umbral
                Double score = JARO_WINKLER.apply(norm1, norm2);
                if (score != null && score >= umbral) {
                    paresVistos.add(pairKey);
                    candidatos.add(crearCandidato(t1, t2, score, "SIMILITUD_JARO_WINKLER"));
                }
            }
        }

        candidatos.sort((a, b) -> Double.compare(b.getSimilitud(), a.getSimilitud()));
        return candidatos;
    }

    private CandidatoFusionDTO crearCandidato(TransportistaModel a, TransportistaModel b, double sim, String motivo) {
        return CandidatoFusionDTO.builder()
                .idA(a.getId())
                .nombreCanonicoA(a.getNombreCanonico())
                .rutA(a.getRut())
                .estadoA(a.getEstado())
                .comercializadorRefA(a.getComercializadorRef())
                .idB(b.getId())
                .nombreCanonicoB(b.getNombreCanonico())
                .rutB(b.getRut())
                .estadoB(b.getEstado())
                .comercializadorRefB(b.getComercializadorRef())
                .similitud(Math.round(sim * 1000.0) / 1000.0)
                .motivoCandidatura(motivo)
                .build();
    }

    /**
     * TT.4 · Fusiona origen en destino. Solo ADMIN.
     */
    @Transactional
    public TransportistaModel fusionar(Long origenId, Long destinoId, String motivo, Long usuarioId) {
        if (origenId == null || destinoId == null || origenId.equals(destinoId)) {
            throw new IllegalArgumentException("Los IDs de origen y destino deben ser válidos y distintos.");
        }

        TransportistaModel origen = transportistaRepository.findById(origenId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el transportista origen #" + origenId));
        TransportistaModel destino = transportistaRepository.findById(destinoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el transportista destino #" + destinoId));

        if (TransportistaModel.ESTADO_FUSIONADO.equals(origen.getEstado())) {
            throw new IllegalStateException("El transportista origen ya se encuentra fusionado.");
        }

        Date ahora = new Date();

        // 1. Reasignar declaraciones de comercializador
        List<DeclaracionComercializadorModel> despachosOrigen = comercializadorRepository.findAll();
        for (DeclaracionComercializadorModel dc : despachosOrigen) {
            if (dc.getTransportista() != null && origenId.equals(dc.getTransportista().getId())) {
                dc.setTransportista(destino);
                comercializadorRepository.save(dc);
            }
        }

        // 2. Reasignar declaraciones de planta
        List<DeclaracionPlantaAbastecimientoModel> recepcionesOrigen = plantaRepository.findAll();
        for (DeclaracionPlantaAbastecimientoModel dpa : recepcionesOrigen) {
            if (dpa.getTransportista() != null && origenId.equals(dpa.getTransportista().getId())) {
                dpa.setTransportista(destino);
                plantaRepository.save(dpa);
            }
        }

        // 3. Reasignar alias de origen a destino
        List<TransportistaAliasModel> aliasOrigen = aliasRepository.findByTransportistaId(origenId);
        for (TransportistaAliasModel ao : aliasOrigen) {
            Optional<TransportistaAliasModel> adOpt = aliasRepository.findByTransportistaIdAndNombreNormalizado(
                    destinoId, ao.getNombreNormalizado());
            if (adOpt.isPresent()) {
                TransportistaAliasModel ad = adOpt.get();
                ad.setVeces(ad.getVeces() + ao.getVeces());
                if (ao.getPrimeraVez() != null && (ad.getPrimeraVez() == null || ao.getPrimeraVez().before(ad.getPrimeraVez()))) {
                    ad.setPrimeraVez(ao.getPrimeraVez());
                }
                if (ao.getUltimaVez() != null && (ad.getUltimaVez() == null || ao.getUltimaVez().after(ad.getUltimaVez()))) {
                    ad.setUltimaVez(ao.getUltimaVez());
                }
                aliasRepository.save(ad);
                aliasRepository.delete(ao);
            } else {
                ao.setTransportista(destino);
                aliasRepository.save(ao);
            }
        }

        // 4. Actualizar estado de origen y destino
        origen.setEstado(TransportistaModel.ESTADO_FUSIONADO);
        origen.setFusionadoEnId(destinoId);
        origen.setUpdatedAt(ahora);
        transportistaRepository.save(origen);

        destino.setUpdatedAt(ahora);
        // Si el destino no tenía RUT pero el origen sí tenía RUT válido, hereda el RUT
        if ((destino.getRut() == null || destino.getRut().isBlank()) && origen.getRut() != null && Boolean.TRUE.equals(origen.getRutValido())) {
            destino.setRut(origen.getRut());
            destino.setRutValido(true);
        }
        // Actualizar nombre canónico según el alias más frecuente
        List<TransportistaAliasModel> todosAlias = aliasRepository.findByTransportistaIdOrderByVecesDesc(destinoId);
        if (!todosAlias.isEmpty()) {
            destino.setNombreCanonico(todosAlias.get(0).getNombreDeclarado());
            destino.setNombreNormalizado(todosAlias.get(0).getNombreNormalizado());
        }
        TransportistaModel destinoActualizado = transportistaRepository.save(destino);

        // 5. Registrar log de fusión
        TransportistaFusionLogModel log = TransportistaFusionLogModel.builder()
                .origenId(origenId)
                .destinoId(destinoId)
                .usuarioId(usuarioId != null ? usuarioId : 0L)
                .motivo(motivo != null ? motivo.trim() : "Fusión manual autorizada por administrador")
                .createdAt(ahora)
                .build();
        fusionLogRepository.save(log);

        return destinoActualizado;
    }
}
