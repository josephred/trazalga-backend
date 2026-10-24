package com.trazalga.api.services;

import com.trazalga.api.dto.InformeReconstruccionDTO;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.VehiculoModel;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.TransporteResolverService.TransporteResolucionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReconstruccionTransporteService {

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Autowired
    private TransporteResolverService transporteResolverService;

    @Autowired
    private ITransportistaRepository transportistaRepository;

    @Autowired
    private ITransportistaAliasRepository aliasRepository;

    @Autowired
    private IVehiculoRepository vehiculoRepository;

    @Transactional
    public InformeReconstruccionDTO reconstruir() {
        LocalDateTime ahora = LocalDateTime.now();
        List<String> detalles = new ArrayList<>();

        List<DeclaracionComercializadorModel> despachos = new ArrayList<>(comercializadorRepository.findAll());
        despachos.sort(Comparator.comparing(DeclaracionComercializadorModel::getId));

        int procCom = 0;
        for (DeclaracionComercializadorModel dc : despachos) {
            try {
                Long comId = dc.getUsuario() != null ? dc.getUsuario().getId() : null;
                Date fMomento = dc.getFechaTraslado() != null ? dc.getFechaTraslado() : dc.getFechaDeclaracion();
                String patCamion = dc.getPlacaPatente() != null && !dc.getPlacaPatente().isBlank()
                        ? dc.getPlacaPatente()
                        : dc.getPatente();

                TransporteResolucionResult res = transporteResolverService.resolver(
                        comId,
                        dc.getChoferTransporte(),
                        dc.getRutChofer(),
                        patCamion,
                        dc.getPlacaPatenteCarro(),
                        dc.getVehiculoTransporte(),
                        fMomento
                );

                if (res != null) {
                    dc.setTransportista(res.getTransportista());
                    dc.setVehiculo(res.getVehiculo());
                    dc.setVehiculoCarro(res.getVehiculoCarro());
                    comercializadorRepository.save(dc);
                    procCom++;
                }
            } catch (Exception e) {
                detalles.add("Error procesando despacho #" + dc.getId() + ": " + e.getMessage());
            }
        }

        List<DeclaracionPlantaAbastecimientoModel> recepciones = new ArrayList<>(plantaRepository.findAll());
        recepciones.sort(Comparator.comparing(DeclaracionPlantaAbastecimientoModel::getId));

        int procPla = 0;
        for (DeclaracionPlantaAbastecimientoModel dpa : recepciones) {
            try {
                Long comId = dpa.getUsuario() != null ? dpa.getUsuario().getId() : null;
                Date fMomento = dpa.getFechaIngresoPlanta() != null ? dpa.getFechaIngresoPlanta() : dpa.getFechaTraslado();
                String patCamion = dpa.getPlacaPatente() != null && !dpa.getPlacaPatente().isBlank()
                        ? dpa.getPlacaPatente()
                        : dpa.getPatente();

                TransporteResolucionResult res = transporteResolverService.resolver(
                        comId,
                        dpa.getChoferTransporte(),
                        dpa.getRutChofer(),
                        patCamion,
                        dpa.getPlacaPatenteCarro(),
                        dpa.getVehiculoTransporte(),
                        fMomento
                );

                if (res != null) {
                    dpa.setTransportista(res.getTransportista());
                    dpa.setVehiculo(res.getVehiculo());
                    dpa.setVehiculoCarro(res.getVehiculoCarro());
                    plantaRepository.save(dpa);
                    procPla++;
                }
            } catch (Exception e) {
                detalles.add("Error procesando recepcion #" + dpa.getId() + ": " + e.getMessage());
            }
        }

        // Métricas de consolidación
        List<TransportistaModel> todosTransportistas = transportistaRepository.findAll();
        int conRut = 0;
        int sinRut = 0;
        int rutInvalidos = 0;
        int rutConMasDeUnNombre = 0;

        for (TransportistaModel t : todosTransportistas) {
            if (Boolean.TRUE.equals(t.getRutValido())) {
                conRut++;
            } else {
                sinRut++;
                if (t.getRut() != null && !t.getRut().isBlank()) {
                    rutInvalidos++;
                }
            }

            List<TransportistaAliasModel> aliases = aliasRepository.findByTransportistaId(t.getId());
            if (aliases.size() > 1) {
                rutConMasDeUnNombre++;
            }
        }

        // Nombres con más de un RUT
        Map<String, Set<String>> nombresToRuts = new HashMap<>();
        for (TransportistaModel t : todosTransportistas) {
            if (t.getRut() != null && !t.getRut().isBlank() && Boolean.TRUE.equals(t.getRutValido())) {
                nombresToRuts.computeIfAbsent(t.getNombreNormalizado(), k -> new HashSet<>()).add(t.getRut());
            }
        }
        int nombresConMasDeUnRut = 0;
        for (Set<String> ruts : nombresToRuts.values()) {
            if (ruts.size() > 1) {
                nombresConMasDeUnRut++;
            }
        }

        List<VehiculoModel> todosVehiculos = vehiculoRepository.findAll();
        int vehiculosTotal = todosVehiculos.size();
        int patInvalida = 0;
        int patCamionYRampla = 0;

        for (VehiculoModel v : todosVehiculos) {
            if (Boolean.FALSE.equals(v.getFormatoValido())) {
                patInvalida++;
            }
            if (Boolean.TRUE.equals(v.getUsadoComoCamion()) && Boolean.TRUE.equals(v.getUsadoComoRampla())) {
                patCamionYRampla++;
            }
        }

        return InformeReconstruccionDTO.builder()
                .fechaEjecucion(ahora)
                .declaracionesComercializadorProcesadas(procCom)
                .declaracionesPlantaProcesadas(procPla)
                .transportistasCreadosConRut(conRut)
                .transportistasCreadosSinRut(sinRut)
                .rutInvalidosDetectados(rutInvalidos)
                .rutConMasDeUnNombre(rutConMasDeUnNombre)
                .nombresConMasDeUnRut(nombresConMasDeUnRut)
                .vehiculosCreados(vehiculosTotal)
                .patentesFormatoInvalido(patInvalida)
                .patentesComoCamionYRampla(patCamionYRampla)
                .detallesObservaciones(detalles)
                .build();
    }
}
