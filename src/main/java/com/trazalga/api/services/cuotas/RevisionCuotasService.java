package com.trazalga.api.services.cuotas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.AvisoEnviadoModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.NotificationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio transaccional para la revisión programada de cuotas diarias (TC.6 / K3).
 * Utiliza findActivasConRelaciones() para evitar LazyInitializationException con open-in-view=false.
 */
@Service
@Slf4j
@Transactional(readOnly = false)
public class RevisionCuotasService {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IAvisoEnviadoRepository avisoEnviadoRepository;

    @Autowired
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired
    private com.trazalga.api.repositories.ICuotaExtraccionEventoRepository cuotaEventoRepository;

    public int revisar(LocalDate hoyLocal, Date hoy) {
        log.info("Iniciando revisión transaccional de cuotas para fecha: {}", hoyLocal);
        double umbralRestantePct = parseDouble(configuracionGeneralService.getValor("cuota_umbral_restante_pct", "10.0"), 10.0);
        int diasPreviosExpiracion = parseInt(configuracionGeneralService.getValor("cuota_dias_previos_expiracion", "5"), 5);
        double desvioVelocidadPct = parseDouble(configuracionGeneralService.getValor("cuota_desvio_velocidad_pct", "25.0"), 25.0);

        List<CuotaExtraccionModel> cuotasActivas = cuotaRepository.findActivasConRelaciones();
        int procesadas = 0;

        for (CuotaExtraccionModel cuota : cuotasActivas) {
            if ("CERRADA".equalsIgnoreCase(cuota.getEstado())) {
                continue;
            }
            if (Boolean.TRUE.equals(cuota.getEsPlantilla()) && cuota.getUsuario() == null) {
                continue;
            }

            LocalDate cInicio = cuota.getFechaInicio() != null ? CuotaExtraccionService.toLocalDateSafe(cuota.getFechaInicio()) : null;
            LocalDate cFin = cuota.getFechaFin() != null ? CuotaExtraccionService.toLocalDateSafe(cuota.getFechaFin()) : null;

            if (cInicio != null && hoyLocal.isBefore(cInicio)) {
                continue;
            }
            if (cFin != null && hoyLocal.isAfter(cFin)) {
                // TC.3: Cierre automático por vencimiento
                boolean cierrePorVencimientoActivo = configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false);
                if (cierrePorVencimientoActivo && !"CERRADA".equalsIgnoreCase(cuota.getEstado())) {
                    cuota.cerrar("VENCIMIENTO", cuota.getFechaFin(), null);
                    cuotaRepository.save(cuota);

                    com.trazalga.api.models.CuotaExtraccionEventoModel evento = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                            .cuota(cuota)
                            .tipo("CERRADA")
                            .motivo("VENCIMIENTO")
                            .detalle("Cierre automático por vencimiento de vigencia al " + cFin)
                            .createdAt(new Date())
                            .build();
                    cuotaEventoRepository.save(evento);

                    log.info("TC.3: Cuota ID {} cerrada automáticamente por VENCIMIENTO (fecha término {}).", cuota.getId(), cFin);
                    procesadas++;
                }
                continue;
            }

            BigDecimal limite = cuotaExtraccionService.calcularLimiteEfectivo(cuota, hoy);
            if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal consumo = cuotaExtraccionService.calcularConsumoAcumulado(cuota, hoy);
            BigDecimal saldo = limite.subtract(consumo);

            String alcance = cuotaExtraccionService.describirAlcance(cuota);
            String especieNombre = (cuota.getEspecie() != null) ? cuota.getEspecie().getNombre() : "General";
            Long usuarioDestinatarioId = cuota.getUsuario() != null ? cuota.getUsuario().getId() : 0L;

            boolean alertaUmbralDisparada = false;

            // 1. Umbral de saldo restante / Agotamiento
            if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
                alertaUmbralDisparada = true;
                String claveAviso = String.format("CUOTA:%d:AGOTADA:%d", cuota.getId(), usuarioDestinatarioId);
                if (debeEnviarAviso(claveAviso)) {
                    String titulo = "Alerta Crítica: Cuota Agotada";
                    String mensaje = String.format("La cuota de %s (%s) ha alcanzado o superado su límite: %.2f kg de %.2f kg.",
                            alcance, especieNombre, consumo, limite);
                    notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
                    registrarAviso(claveAviso);
                }

                if ("BLOQUEO_DECLARACION".equalsIgnoreCase(cuota.getModoAccion()) && !"CERRADA".equalsIgnoreCase(cuota.getEstado())) {
                    cuota.cerrar("AGOTAMIENTO", hoy, null);
                    cuotaRepository.save(cuota);

                    com.trazalga.api.models.CuotaExtraccionEventoModel evento = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                            .cuota(cuota)
                            .tipo("CERRADA")
                            .motivo("AGOTAMIENTO")
                            .detalle("Cierre automático por agotamiento de cuota en modo BLOQUEO_DECLARACION")
                            .createdAt(new Date())
                            .build();
                    cuotaEventoRepository.save(evento);

                    log.info("Cuota ID {} cerrada automáticamente por AGOTAMIENTO en modo BLOQUEO_DECLARACION.", cuota.getId());
                }
            } else {
                double pctRestante = saldo.divide(limite, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                if (pctRestante <= umbralRestantePct) {
                    alertaUmbralDisparada = true;
                    String claveAviso = String.format("CUOTA:%d:UMBRAL:%d", cuota.getId(), usuarioDestinatarioId);
                    if (debeEnviarAviso(claveAviso)) {
                        String titulo = "Aviso de Cuota Próxima al Límite";
                        String mensaje = String.format("La cuota de %s (%s) tiene solo %.1f%% de saldo restante (%.2f kg disponibles de %.2f kg).",
                                alcance, especieNombre, pctRestante, saldo, limite);
                        notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
                        registrarAviso(claveAviso);
                    }
                }
            }

            // 2. Vigencia / Expiración próxima
            if (cuota.getFechaFin() != null) {
                LocalDate fechaFinLocal = CuotaExtraccionService.toLocalDateSafe(cuota.getFechaFin());
                long diasRestantes = ChronoUnit.DAYS.between(hoyLocal, fechaFinLocal);
                if (diasRestantes >= 0 && diasRestantes <= diasPreviosExpiracion) {
                    if (!tieneSucesoraContigua(cuota, fechaFinLocal, cuotasActivas)) {
                        String claveAviso = String.format("CUOTA:%d:VENCIMIENTO:%d", cuota.getId(), usuarioDestinatarioId);
                        if (debeEnviarAviso(claveAviso)) {
                            String titulo = "Aviso de Expiración de Cuota";
                            String mensaje = String.format("La cuota de %s (%s) vencerá en %d día(s) (fecha de término: %s).",
                                    alcance, especieNombre, diasRestantes, cuota.getFechaFin());
                            notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
                            registrarAviso(claveAviso);
                        }
                    }
                }
            }

            // 3. Velocidad / Ritmo de Consumo
            if (!alertaUmbralDisparada) {
                evaluarVelocidadConsumo(cuota, hoyLocal, limite, consumo, saldo, alcance, especieNombre, desvioVelocidadPct, usuarioDestinatarioId);
            }

            procesadas++;
        }

        log.info("Finalizada revisión transaccional de cuotas. Procesadas: {}", procesadas);
        return procesadas;
    }

    private boolean debeEnviarAviso(String clave) {
        return !avisoEnviadoRepository.existsById(clave);
    }

    private void registrarAviso(String clave) {
        avisoEnviadoRepository.save(new AvisoEnviadoModel(clave, new Date()));
    }

    private void evaluarVelocidadConsumo(CuotaExtraccionModel cuota, LocalDate hoyLocal, BigDecimal limite, BigDecimal consumo,
                                         BigDecimal saldo, String alcance, String especieNombre, double desvioVelocidadTolerancia,
                                         Long usuarioDestinatarioId) {
        String periodo = cuota.getPeriodo() != null ? cuota.getPeriodo().trim().toUpperCase() : "";
        if (!periodo.equals("MENSUAL") && !periodo.equals("ANUAL") && !periodo.equals("BIANUAL")) {
            return;
        }

        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = CuotaExtraccionService.toLocalDateSafe(cuota.getFechaInicio());
            end = CuotaExtraccionService.toLocalDateSafe(cuota.getFechaFin());
        } else if (periodo.equals("MENSUAL")) {
            start = hoyLocal.withDayOfMonth(1);
            end = hoyLocal.withDayOfMonth(hoyLocal.lengthOfMonth());
        } else if (periodo.equals("ANUAL")) {
            start = hoyLocal.withDayOfYear(1);
            end = hoyLocal.withDayOfYear(hoyLocal.lengthOfYear());
        } else {
            return;
        }

        if (end.isBefore(start) || hoyLocal.isBefore(start)) {
            return;
        }

        long diasTotales = ChronoUnit.DAYS.between(start, end) + 1;
        if (diasTotales <= 0) return;

        long diasTranscurridos = ChronoUnit.DAYS.between(start, hoyLocal) + 1;
        if (diasTranscurridos > diasTotales) diasTranscurridos = diasTotales;

        double pctTiempo = ((double) diasTranscurridos / (double) diasTotales) * 100.0;
        if (pctTiempo < 10.0) return;

        double pctConsumo = (consumo.doubleValue() / limite.doubleValue()) * 100.0;
        double desvio = pctConsumo - pctTiempo;

        if (desvio > desvioVelocidadTolerancia) {
            String claveAviso = String.format("CUOTA:%d:RITMO:%d", cuota.getId(), usuarioDestinatarioId);
            if (debeEnviarAviso(claveAviso)) {
                double consumoPorDia = consumo.doubleValue() / (double) diasTranscurridos;
                String estimacionAgotamiento = (consumoPorDia > 0 && saldo.doubleValue() > 0)
                        ? "el " + hoyLocal.plusDays((long) Math.ceil(saldo.doubleValue() / consumoPorDia))
                        : "en los próximos días";

                String titulo = "Alerta de Ritmo de Consumo: Cuota Acelerada";
                String mensaje = String.format("La cuota de %s (%s) va al %.0f%% de consumo con sólo el %.0f%% del periodo transcurrido (desvío de %.0f puntos, tolerancia %.0f). A este ritmo se agotará %s, antes del fin del período (%s).",
                        alcance, especieNombre, pctConsumo, pctTiempo, desvio, desvioVelocidadTolerancia, estimacionAgotamiento, end);

                notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
                registrarAviso(claveAviso);
            }
        }
    }

    private void notificar(Long usuarioId, String titulo, String mensaje) {
        if (usuarioId != null && usuarioId > 0) {
            notificationService.sendPushNotificationToUser(usuarioId, titulo, mensaje);
        }
        notificarSoloAdmins(titulo, mensaje);
    }

    private void notificarSoloAdmins(String titulo, String mensaje) {
        try {
            List<UsuarioModel> admins = usuarioRepository.findByPerfilId(1L);
            if (admins != null) {
                for (UsuarioModel admin : admins) {
                    notificationService.sendPushNotificationToUser(admin.getId(), titulo, mensaje);
                }
            }
        } catch (Exception e) {
            log.error("Error despachando notificación programada a administradores: {}", e.getMessage());
        }
    }

    boolean tieneSucesoraContigua(CuotaExtraccionModel cuota, LocalDate fechaFinLocal, List<CuotaExtraccionModel> todasCuotas) {
        if (fechaFinLocal == null || todasCuotas == null) return false;
        LocalDate diaSiguiente = fechaFinLocal.plusDays(1);
        String ambitoA = cuota.getAmbito() != null ? cuota.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        Long espA = cuota.getEspecie() != null ? cuota.getEspecie().getId() : null;
        Long metA = cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getId() : null;
        Set<Long> comA = cuotaExtraccionService.idsComunas(cuota);
        Long regA = cuota.getRegion() != null ? cuota.getRegion().getId() : null;

        for (CuotaExtraccionModel s : todasCuotas) {
            if (s.getId() != null && s.getId().equals(cuota.getId())) continue;
            if (!Boolean.TRUE.equals(s.getActivo())) continue;
            if (s.getFechaInicio() == null) continue;

            LocalDate sInicio = CuotaExtraccionService.toLocalDateSafe(s.getFechaInicio());
            if (!diaSiguiente.equals(sInicio)) continue;

            String ambitoS = s.getAmbito() != null ? s.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
            if (!ambitoA.equals(ambitoS)) continue;

            Long espS = s.getEspecie() != null ? s.getEspecie().getId() : null;
            if (!Objects.equals(espA, espS)) continue;

            Long metS = s.getExtraccionTipo() != null ? s.getExtraccionTipo().getId() : null;
            if (!Objects.equals(metA, metS)) continue;

            Set<Long> comS = cuotaExtraccionService.idsComunas(s);
            if (!comA.equals(comS)) continue;

            Long regS = s.getRegion() != null ? s.getRegion().getId() : null;
            if (!Objects.equals(regA, regS)) continue;

            return true;
        }
        return false;
    }

    private double parseDouble(String str, double def) {
        try {
            return str != null ? Double.parseDouble(str.trim()) : def;
        } catch (Exception e) {
            return def;
        }
    }

    private int parseInt(String str, int def) {
        try {
            return str != null ? Integer.parseInt(str.trim()) : def;
        } catch (Exception e) {
            return def;
        }
    }
}
