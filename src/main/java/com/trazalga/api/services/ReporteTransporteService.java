package com.trazalga.api.services;

import com.trazalga.api.dto.MisFrecuentesDTO;
import com.trazalga.api.dto.MisFrecuentesDTO.ChoferFrecuenteDTO;
import com.trazalga.api.dto.MisFrecuentesDTO.VehiculoFrecuenteDTO;
import com.trazalga.api.dto.ReporteRelacionesTransporteDTO;
import com.trazalga.api.dto.ReporteRelacionesTransporteDTO.AvisoTransporteDTO;
import com.trazalga.api.dto.ReporteRelacionesTransporteDTO.FilaRelacionDTO;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.VehiculoModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class ReporteTransporteService {

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    @Transactional(readOnly = true)
    public ReporteRelacionesTransporteDTO generarReporteRelaciones(
            LocalDate desde,
            LocalDate hasta,
            Long comercializadorId,
            Long transportistaId,
            String patente) {

        int umbralMultiComercializador = configService != null
                ? configService.getInt("transporte_alerta_multi_comercializador", 3)
                : 3;

        String patNorm = ConsultaPatenteService.normalizarPatente(patente);

        // Agrupación por clave: transportistaId | vehiculoId | comercializadorId
        Map<String, AcumuladorRelacion> mapa = new LinkedHashMap<>();

        List<DeclaracionComercializadorModel> despachos = comercializadorRepository.findAll();
        for (DeclaracionComercializadorModel dc : despachos) {
            Date f = dc.getFechaTraslado() != null ? dc.getFechaTraslado() : dc.getFechaDeclaracion();
            if (!enRango(f, desde, hasta)) continue;

            Long cId = dc.getUsuario() != null ? dc.getUsuario().getId() : null;
            if (comercializadorId != null && !comercializadorId.equals(cId)) continue;

            Long tId = dc.getTransportista() != null ? dc.getTransportista().getId() : null;
            if (transportistaId != null && !transportistaId.equals(tId)) continue;

            String pCam = dc.getVehiculo() != null ? dc.getVehiculo().getPatente()
                    : ConsultaPatenteService.normalizarPatente(dc.getPlacaPatente() != null ? dc.getPlacaPatente() : dc.getPatente());
            if (!patNorm.isEmpty() && !patNorm.equals(pCam)) {
                String pCarro = dc.getVehiculoCarro() != null ? dc.getVehiculoCarro().getPatente()
                        : ConsultaPatenteService.normalizarPatente(dc.getPlacaPatenteCarro());
                if (!patNorm.equals(pCarro)) continue;
            }

            Long vId = dc.getVehiculo() != null ? dc.getVehiculo().getId() : null;
            String key = (tId != null ? tId : "0") + "|" + (vId != null ? vId : pCam) + "|" + (cId != null ? cId : "0");

            AcumuladorRelacion acum = mapa.computeIfAbsent(key, k -> new AcumuladorRelacion(dc.getTransportista(), dc.getVehiculo(), pCam, dc.getUsuario(), cId));
            double kg = dc.getCantidad() != null ? dc.getCantidad().doubleValue() : 0.0;
            String dest = dc.getNombreDestinatario() != null ? dc.getNombreDestinatario() : "Destino";
            acum.agregarViaje(kg, dest, f);
        }

        List<FilaRelacionDTO> filas = new ArrayList<>();
        Map<String, Set<Long>> vehiculoComercializadores = new HashMap<>();
        Map<Long, Set<String>> choferPatentes = new HashMap<>();

        for (AcumuladorRelacion acum : mapa.values()) {
            filas.add(acum.toDTO());

            if (acum.patente != null && !acum.patente.isBlank() && acum.comercializadorId != null) {
                vehiculoComercializadores.computeIfAbsent(acum.patente, k -> new HashSet<>()).add(acum.comercializadorId);
            }
            if (acum.transportistaId != null && acum.patente != null && !acum.patente.isBlank()) {
                choferPatentes.computeIfAbsent(acum.transportistaId, k -> new HashSet<>()).add(acum.patente);
            }
        }

        filas.sort((a, b) -> Integer.compare(b.getViajes(), a.getViajes()));

        // Construir avisos informativos (no crean hallazgos)
        List<AvisoTransporteDTO> avisos = new ArrayList<>();

        for (Map.Entry<String, Set<Long>> entry : vehiculoComercializadores.entrySet()) {
            if (entry.getValue().size() >= umbralMultiComercializador) {
                avisos.add(AvisoTransporteDTO.builder()
                        .tipo("MULTI_COMERCIALIZADOR")
                        .titulo("Vehículo con múltiples comercializadores")
                        .detalle("El vehículo con patente " + entry.getKey() + " sirvió a " + entry.getValue().size()
                                + " comercializadores distintos en el período.")
                        .entidadReferencia(entry.getKey())
                        .cantidadObservada(entry.getValue().size())
                        .build());
            }
        }

        for (Map.Entry<Long, Set<String>> entry : choferPatentes.entrySet()) {
            if (entry.getValue().size() > 3) {
                avisos.add(AvisoTransporteDTO.builder()
                        .tipo("MULTI_PATENTE")
                        .titulo("Chofer con múltiples vehículos")
                        .detalle("El chofer #" + entry.getKey() + " condujo " + entry.getValue().size()
                                + " patentes distintas (" + String.join(", ", entry.getValue()) + ") en el período.")
                        .entidadReferencia("Chofer #" + entry.getKey())
                        .cantidadObservada(entry.getValue().size())
                        .build());
            }
        }

        return ReporteRelacionesTransporteDTO.builder()
                .filas(filas)
                .avisos(avisos)
                .build();
    }

    /**
     * TT.7 · Devuelve solo los choferes y vehículos frecuentes utilizados por el usuario emisor.
     */
    @Transactional(readOnly = true)
    public MisFrecuentesDTO obtenerMisFrecuentes(String userRut) {
        if (userRut == null || userRut.isBlank()) {
            return new MisFrecuentesDTO();
        }

        Map<String, ChoferAcumulador> choferesMap = new LinkedHashMap<>();
        Map<String, VehiculoAcumulador> vehiculosMap = new LinkedHashMap<>();

        List<DeclaracionComercializadorModel> despachos = comercializadorRepository.findAll();
        for (DeclaracionComercializadorModel dc : despachos) {
            if (dc.getUsuario() == null || dc.getUsuario().getRut() == null) continue;
            if (!userRut.equalsIgnoreCase(dc.getUsuario().getRut().trim())) continue;

            // Chofer
            String nomChofer = dc.getChoferTransporte();
            String rutChofer = dc.getRutChofer();
            Long tId = dc.getTransportista() != null ? dc.getTransportista().getId() : null;
            if (nomChofer != null && !nomChofer.isBlank()) {
                String key = (rutChofer != null && !rutChofer.isBlank()) ? rutChofer.trim().toUpperCase() : nomChofer.trim().toUpperCase();
                choferesMap.computeIfAbsent(key, k -> new ChoferAcumulador(tId, nomChofer.trim(), rutChofer)).incrementar();
            }

            // Vehículo camión
            String patCamion = dc.getPlacaPatente() != null && !dc.getPlacaPatente().isBlank() ? dc.getPlacaPatente() : dc.getPatente();
            if (patCamion != null && !patCamion.isBlank()) {
                String pNorm = ConsultaPatenteService.normalizarPatente(patCamion);
                Long vId = dc.getVehiculo() != null ? dc.getVehiculo().getId() : null;
                vehiculosMap.computeIfAbsent(pNorm, k -> new VehiculoAcumulador(vId, pNorm, dc.getVehiculoTransporte(), false)).incrementar();
            }

            // Vehículo rampla
            if (dc.getPlacaPatenteCarro() != null && !dc.getPlacaPatenteCarro().isBlank()) {
                String pCarro = ConsultaPatenteService.normalizarPatente(dc.getPlacaPatenteCarro());
                Long vId = dc.getVehiculoCarro() != null ? dc.getVehiculoCarro().getId() : null;
                vehiculosMap.computeIfAbsent(pCarro, k -> new VehiculoAcumulador(vId, pCarro, "RAMPLA", true)).incrementar();
            }
        }

        List<ChoferFrecuenteDTO> choferesList = new ArrayList<>();
        for (ChoferAcumulador ca : choferesMap.values()) {
            choferesList.add(ca.toDTO());
        }
        choferesList.sort((a, b) -> Integer.compare(b.getVeces(), a.getVeces()));

        List<VehiculoFrecuenteDTO> vehiculosList = new ArrayList<>();
        for (VehiculoAcumulador va : vehiculosMap.values()) {
            vehiculosList.add(va.toDTO());
        }
        vehiculosList.sort((a, b) -> Integer.compare(b.getVeces(), a.getVeces()));

        return MisFrecuentesDTO.builder()
                .choferes(choferesList)
                .vehiculos(vehiculosList)
                .build();
    }

    private static class ChoferAcumulador {
        Long tId;
        String nombre;
        String rut;
        int veces;

        ChoferAcumulador(Long tId, String nombre, String rut) {
            this.tId = tId;
            this.nombre = nombre;
            this.rut = rut;
        }

        void incrementar() { veces++; }

        ChoferFrecuenteDTO toDTO() {
            return ChoferFrecuenteDTO.builder()
                    .transportistaId(tId)
                    .nombre(nombre)
                    .rut(rut)
                    .veces(veces)
                    .build();
        }
    }

    private static class VehiculoAcumulador {
        Long vId;
        String patente;
        String tipo;
        boolean esRampla;
        int veces;

        VehiculoAcumulador(Long vId, String patente, String tipo, boolean esRampla) {
            this.vId = vId;
            this.patente = patente;
            this.tipo = tipo;
            this.esRampla = esRampla;
        }

        void incrementar() { veces++; }

        VehiculoFrecuenteDTO toDTO() {
            return VehiculoFrecuenteDTO.builder()
                    .vehiculoId(vId)
                    .patente(patente)
                    .tipoVehiculo(tipo)
                    .esRampla(esRampla)
                    .veces(veces)
                    .build();
        }
    }

    private static class AcumuladorRelacion {
        Long transportistaId;
        String transportistaNombre;
        String transportistaRut;

        Long vehiculoId;
        String patente;
        String tipoVehiculo;

        Long comercializadorId;
        String comercializadorNombre;
        String comercializadorRut;

        int viajes = 0;
        double kilos = 0.0;
        Set<String> plantas = new LinkedHashSet<>();
        Date primera = null;
        Date ultima = null;

        AcumuladorRelacion(TransportistaModel t, VehiculoModel v, String pat, com.trazalga.api.models.UsuarioModel com, Long cId) {
            if (t != null) {
                this.transportistaId = t.getId();
                this.transportistaNombre = t.getNombreCanonico();
                this.transportistaRut = t.getRut();
            }
            if (v != null) {
                this.vehiculoId = v.getId();
                this.patente = v.getPatente();
                this.tipoVehiculo = v.getTipoVehiculo();
            } else {
                this.patente = pat;
            }
            if (com != null) {
                this.comercializadorId = com.getId();
                this.comercializadorNombre = (com.getNombres() != null ? com.getNombres() : "").trim();
                this.comercializadorRut = com.getRut();
            } else {
                this.comercializadorId = cId;
            }
        }

        void agregarViaje(double kg, String dest, Date f) {
            viajes++;
            kilos += kg;
            if (dest != null && !dest.isBlank()) plantas.add(dest.trim());
            if (f != null) {
                if (primera == null || f.before(primera)) primera = f;
                if (ultima == null || f.after(ultima)) ultima = f;
            }
        }

        FilaRelacionDTO toDTO() {
            return FilaRelacionDTO.builder()
                    .transportistaId(transportistaId)
                    .transportistaNombre(transportistaNombre != null ? transportistaNombre : "Sin chofer")
                    .transportistaRut(transportistaRut)
                    .vehiculoId(vehiculoId)
                    .vehiculoPatente(patente != null ? patente : "Sin patente")
                    .tipoVehiculo(tipoVehiculo)
                    .comercializadorId(comercializadorId)
                    .comercializadorNombre(comercializadorNombre != null ? comercializadorNombre : "Comercializador")
                    .comercializadorRut(comercializadorRut)
                    .viajes(viajes)
                    .kilos(Math.round(kilos * 10.0) / 10.0)
                    .plantasDestino(new ArrayList<>(plantas))
                    .primeraFecha(primera)
                    .ultimaFecha(ultima)
                    .build();
        }
    }

    private boolean enRango(Date f, LocalDate desde, LocalDate hasta) {
        if (f == null) return false;
        LocalDate ld;
        if (f instanceof java.sql.Date sqlDate) ld = sqlDate.toLocalDate();
        else ld = f.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        if (desde != null && ld.isBefore(desde)) return false;
        if (hasta != null && ld.isAfter(hasta)) return false;
        return true;
    }
}
