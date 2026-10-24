package com.trazalga.api.services;

import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.VehiculoModel;
import com.trazalga.api.repositories.ITransportistaAliasRepository;
import com.trazalga.api.repositories.ITransportistaRepository;
import com.trazalga.api.repositories.IVehiculoRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * TT.2 · Servicio de resolución y normalización de choferes y vehículos.
 * Se llama al crear, editar y sincronizar declaraciones de comercializador y planta.
 */
@Service
public class TransporteResolverService {

    private static final Pattern PATENTE_CHILENA = Pattern.compile("^[A-Z]{2}\\d{4}$|^[A-Z]{4}\\d{2}$");

    @Autowired
    private ITransportistaRepository transportistaRepository;

    @Autowired
    private ITransportistaAliasRepository aliasRepository;

    @Autowired
    private IVehiculoRepository vehiculoRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransporteResolucionResult {
        private TransportistaModel transportista;
        private VehiculoModel vehiculo;
        private VehiculoModel vehiculoCarro;
        private boolean rutValido;
        private boolean patenteCamionValida;
        private boolean patenteCarroValida;
    }

    /**
     * Resuelve chofer y vehículos en un solo paso.
     */
    @Transactional
    public TransporteResolucionResult resolver(
            Long comercializadorId,
            String nombreChofer,
            String rutChofer,
            String patenteCamion,
            String patenteCarro,
            String tipoVehiculo,
            Date momento) {

        Date fechaMomento = momento != null ? momento : new Date();

        boolean rutVal = validarRut(rutChofer);
        TransportistaModel transportista = resolverTransportista(comercializadorId, nombreChofer, rutChofer, fechaMomento);

        VehiculoModel vehiculo = resolverVehiculo(patenteCamion, false, tipoVehiculo, fechaMomento);
        VehiculoModel vehiculoCarro = resolverVehiculo(patenteCarro, true, "RAMPLA", fechaMomento);

        boolean patVal = vehiculo != null && Boolean.TRUE.equals(vehiculo.getFormatoValido());
        boolean patCarroVal = vehiculoCarro != null && Boolean.TRUE.equals(vehiculoCarro.getFormatoValido());

        return TransporteResolucionResult.builder()
                .transportista(transportista)
                .vehiculo(vehiculo)
                .vehiculoCarro(vehiculoCarro)
                .rutValido(rutVal)
                .patenteCamionValida(patVal)
                .patenteCarroValida(patCarroVal)
                .build();
    }

    /**
     * Resuelve o crea un transportista según las reglas de TT.2.
     */
    @Transactional
    public TransportistaModel resolverTransportista(Long comercializadorId, String nombreChofer, String rutChofer, Date momento) {
        String rutLimpio = normalizarRut(rutChofer);
        String nomNorm = normalizarNombre(nombreChofer);
        String nomDeclarado = (nombreChofer != null && !nombreChofer.isBlank()) ? nombreChofer.trim() : null;
        Date fecha = momento != null ? momento : new Date();

        if ((rutLimpio == null || rutLimpio.isBlank()) && (nomNorm == null || nomNorm.isBlank())) {
            return null;
        }

        boolean rutValido = validarRut(rutChofer);

        if (rutValido && rutLimpio != null) {
            // Caso con RUT válido: buscar por RUT único canónico
            Optional<TransportistaModel> opt = transportistaRepository.findByRut(rutLimpio);
            TransportistaModel t;
            if (opt.isPresent()) {
                t = opt.get();
                t.setUpdatedAt(fecha);
            } else {
                String nombreBase = nomDeclarado != null ? nomDeclarado : rutLimpio;
                t = TransportistaModel.builder()
                        .rut(rutLimpio)
                        .rutValido(true)
                        .nombreCanonico(nombreBase)
                        .nombreNormalizado(normalizarNombre(nombreBase))
                        .estado(TransportistaModel.ESTADO_ACTIVO)
                        .createdAt(fecha)
                        .updatedAt(fecha)
                        .build();
                t = transportistaRepository.save(t);
            }

            if (nomDeclarado != null && !nomNorm.isBlank()) {
                actualizarAlias(t, nomDeclarado, nomNorm, fecha);
            }
            return t;
        }

        // Sin RUT o con RUT inválido: ámbito del calce por nombre en el comercializador
        if (nomNorm != null && !nomNorm.isBlank()) {
            List<TransportistaModel> candidatos = transportistaRepository.findByNombreNormalizadoAndComercializadorRef(nomNorm, comercializadorId);
            TransportistaModel t = null;
            for (TransportistaModel c : candidatos) {
                if (!TransportistaModel.ESTADO_FUSIONADO.equals(c.getEstado())) {
                    t = c;
                    break;
                }
            }

            if (t != null) {
                t.setUpdatedAt(fecha);
                actualizarAlias(t, nomDeclarado != null ? nomDeclarado : t.getNombreCanonico(), nomNorm, fecha);
                return transportistaRepository.save(t);
            } else {
                // Nuevo transportista pendiente de revisión
                t = TransportistaModel.builder()
                        .rut(rutLimpio)
                        .rutValido(false)
                        .nombreCanonico(nomDeclarado != null ? nomDeclarado : nomNorm)
                        .nombreNormalizado(nomNorm)
                        .comercializadorRef(comercializadorId)
                        .estado(TransportistaModel.ESTADO_REVISAR)
                        .createdAt(fecha)
                        .updatedAt(fecha)
                        .build();
                t = transportistaRepository.save(t);
                actualizarAlias(t, nomDeclarado != null ? nomDeclarado : nomNorm, nomNorm, fecha);
                return t;
            }
        }

        return null;
    }

    /**
     * Resuelve o crea un vehículo.
     */
    @Transactional
    public VehiculoModel resolverVehiculo(String patenteInput, boolean esRampla, String tipoVehiculo, Date momento) {
        String pat = ConsultaPatenteService.normalizarPatente(patenteInput);
        if (pat.isEmpty()) {
            return null;
        }
        Date fecha = momento != null ? momento : new Date();
        boolean formatoValido = esFormatoPatenteValido(pat);

        Optional<VehiculoModel> opt = vehiculoRepository.findByPatente(pat);
        VehiculoModel v;
        if (opt.isPresent()) {
            v = opt.get();
            if (esRampla) {
                v.setUsadoComoRampla(true);
            } else {
                v.setUsadoComoCamion(true);
            }
            if (tipoVehiculo != null && !tipoVehiculo.isBlank() && (v.getTipoVehiculo() == null || v.getTipoVehiculo().isBlank())) {
                v.setTipoVehiculo(tipoVehiculo.trim());
            }
            if (v.getUltimaVez() == null || fecha.after(v.getUltimaVez())) {
                v.setUltimaVez(fecha);
            }
            if (v.getPrimeraVez() == null || fecha.before(v.getPrimeraVez())) {
                v.setPrimeraVez(fecha);
            }
            return vehiculoRepository.save(v);
        } else {
            v = VehiculoModel.builder()
                    .patente(pat)
                    .formatoValido(formatoValido)
                    .usadoComoCamion(!esRampla)
                    .usadoComoRampla(esRampla)
                    .tipoVehiculo(tipoVehiculo != null && !tipoVehiculo.isBlank() ? tipoVehiculo.trim() : null)
                    .primeraVez(fecha)
                    .ultimaVez(fecha)
                    .build();
            return vehiculoRepository.save(v);
        }
    }

    private void actualizarAlias(TransportistaModel transportista, String nombreDeclarado, String nombreNorm, Date fecha) {
        if (transportista == null || transportista.getId() == null || nombreNorm.isBlank()) return;

        Optional<TransportistaAliasModel> opt = aliasRepository.findByTransportistaIdAndNombreNormalizado(transportista.getId(), nombreNorm);
        if (opt.isPresent()) {
            TransportistaAliasModel a = opt.get();
            a.setVeces(a.getVeces() + 1);
            if (a.getUltimaVez() == null || fecha.after(a.getUltimaVez())) {
                a.setUltimaVez(fecha);
            }
            aliasRepository.save(a);
        } else {
            TransportistaAliasModel nuevo = TransportistaAliasModel.builder()
                    .transportista(transportista)
                    .nombreDeclarado(nombreDeclarado != null ? nombreDeclarado.trim() : nombreNorm)
                    .nombreNormalizado(nombreNorm)
                    .veces(1)
                    .primeraVez(fecha)
                    .ultimaVez(fecha)
                    .build();
            aliasRepository.save(nuevo);
        }

        // El nombre canónico corresponde al alias más frecuente (a menos que haya sido editado manualmente)
        List<TransportistaAliasModel> todos = aliasRepository.findByTransportistaIdOrderByVecesDesc(transportista.getId());
        if (!todos.isEmpty()) {
            TransportistaAliasModel top = todos.get(0);
            if (transportista.getNombreCanonico() == null || transportista.getNombreCanonico().isBlank()
                    || transportista.getNombreCanonico().equals(transportista.getRut())) {
                transportista.setNombreCanonico(top.getNombreDeclarado());
                transportista.setNombreNormalizado(top.getNombreNormalizado());
                transportistaRepository.save(transportista);
            }
        }
    }

    public static String normalizarNombre(String nombre) {
        if (nombre == null) return "";
        String s = Normalizer.normalize(nombre, Normalizer.Form.NFD);
        s = s.replaceAll("\\p{M}", "");
        s = s.toUpperCase().replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return s;
    }

    public static boolean esFormatoPatenteValido(String patente) {
        if (patente == null) return false;
        String norm = ConsultaPatenteService.normalizarPatente(patente);
        return PATENTE_CHILENA.matcher(norm).matches();
    }

    /**
     * Valida un RUT chileno usando módulo 11.
     */
    public static boolean validarRut(String rutRaw) {
        if (rutRaw == null) return false;
        String rut = rutRaw.trim().toUpperCase().replaceAll("[^0-9K]", "");
        if (rut.length() < 2 || rut.length() > 9) return false;

        String cuerpo = rut.substring(0, rut.length() - 1);
        char dv = rut.charAt(rut.length() - 1);

        try {
            int suma = 0;
            int multiplo = 2;
            for (int i = cuerpo.length() - 1; i >= 0; i--) {
                suma += (cuerpo.charAt(i) - '0') * multiplo;
                multiplo = (multiplo == 7) ? 2 : multiplo + 1;
            }
            int resto = 11 - (suma % 11);
            char dvEsperado;
            if (resto == 11) dvEsperado = '0';
            else if (resto == 10) dvEsperado = 'K';
            else dvEsperado = (char) ('0' + resto);

            return dv == dvEsperado;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normaliza un RUT al formato canónico 12345678-5 (o cuerpo-dv si es inválido).
     */
    public static String normalizarRut(String rutRaw) {
        if (rutRaw == null) return null;
        String r = rutRaw.trim().toUpperCase().replaceAll("[^0-9K]", "");
        if (r.length() < 2) return null;
        String cuerpo = r.substring(0, r.length() - 1);
        char dv = r.charAt(r.length() - 1);
        return cuerpo + "-" + dv;
    }
}
