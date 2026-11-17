package com.trazalga.api.services.declaraciones;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.models.DeclaracionFechaAjusteLogModel;
import com.trazalga.api.repositories.IDeclaracionFechaAjusteLogRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.fechas.FechaSql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * ZT.2 · Al crear una declaración, detecta fechas futuras causadas por el desfase
 * UTC de versiones previas de la app móvil (declaraciones nocturnas en Chile que
 * traen la fecha de mañana con la hora local de hoy).
 * <p>
 * Si restar exactamente un día deja la marca temporal dentro de la tolerancia
 * respecto al reloj del servidor, ajusta {@code fecha_declaracion} (y las fechas
 * acompañantes que traían el mismo día) en un día cuando
 * {@code declaracion_corrige_fecha_futura = true}. Si aun restando un día sigue
 * en el futuro, no la corrige y registra {@code RELOJ_INCONSISTENTE}.
 */
@Service
public class PoliticaFechaDeclaracion {

    public static final String MOTIVO_AJUSTADO = "DESFASE_UTC_AJUSTADO";
    public static final String MOTIVO_SIN_AJUSTE = "DESFASE_UTC_SIN_AJUSTE";
    public static final String MOTIVO_RELOJ_INCONSISTENTE = "RELOJ_INCONSISTENTE";

    private static final DateTimeFormatter HORA_SS = DateTimeFormatter.ofPattern("H:mm:ss");
    private static final DateTimeFormatter HORA_MM = DateTimeFormatter.ofPattern("H:mm");

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    @Autowired(required = false)
    private IDeclaracionFechaAjusteLogRepository logRepository;

    public void setConfigService(ConfiguracionGeneralService configService) {
        this.configService = configService;
    }

    public void setLogRepository(IDeclaracionFechaAjusteLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public record FechaAcompanante(Date valor, Consumer<Date> setter) {
    }

    public record AjustePendiente(
            String tipo,
            LocalDate fechaOriginal,
            LocalDate fechaGuardada,
            String horaDeclarada,
            String motivo,
            LocalDateTime fechaServidor) {
    }

    public AjustePendiente ajustarSiFutura(
            String tipo,
            Date fechaPrincipal,
            String hora,
            Consumer<Date> setFechaPrincipal,
            List<FechaAcompanante> acompanantes) {
        return ajustarSiFutura(
                tipo,
                fechaPrincipal,
                hora,
                setFechaPrincipal,
                acompanantes,
                LocalDateTime.now(ApiApplication.ZONA_CHILE));
    }

    public AjustePendiente ajustarSiFutura(
            String tipo,
            Date fechaPrincipal,
            String hora,
            Consumer<Date> setFechaPrincipal,
            List<FechaAcompanante> acompanantes,
            LocalDateTime ahoraServidor) {
        if (fechaPrincipal == null || hora == null || hora.isBlank() || ahoraServidor == null) {
            return null;
        }
        LocalTime horaParsed = parsearHora(hora);
        if (horaParsed == null) {
            return null;
        }

        LocalDate fechaOrig = FechaSql.diaLocal(fechaPrincipal);
        LocalDateTime tsDeclarado = LocalDateTime.of(fechaOrig, horaParsed);

        int toleranciaMin = configService != null
                ? configService.getInt("declaracion_tolerancia_reloj_min", 15)
                : 15;
        LocalDateTime limite = ahoraServidor.plusMinutes(Math.max(0, toleranciaMin));

        if (!tsDeclarado.isAfter(limite)) {
            return null;
        }

        LocalDateTime tsMenosUno = tsDeclarado.minusDays(1);
        if (tsMenosUno.isAfter(limite)) {
            return new AjustePendiente(
                    tipo,
                    fechaOrig,
                    fechaOrig,
                    hora.trim(),
                    MOTIVO_RELOJ_INCONSISTENTE,
                    ahoraServidor);
        }

        boolean corrige = configService == null
                || configService.getBoolean("declaracion_corrige_fecha_futura", true);
        if (!corrige) {
            return new AjustePendiente(
                    tipo,
                    fechaOrig,
                    fechaOrig,
                    hora.trim(),
                    MOTIVO_SIN_AJUSTE,
                    ahoraServidor);
        }

        LocalDate nuevaFecha = fechaOrig.minusDays(1);
        Date nuevaDate = Date.from(nuevaFecha.atStartOfDay(ApiApplication.ZONA_CHILE).toInstant());
        if (setFechaPrincipal != null) {
            setFechaPrincipal.accept(nuevaDate);
        }
        if (acompanantes != null) {
            for (FechaAcompanante comp : acompanantes) {
                if (comp != null && comp.valor() != null && comp.setter() != null
                        && FechaSql.diaLocal(comp.valor()).equals(fechaOrig)) {
                    comp.setter().accept(nuevaDate);
                }
            }
        }

        return new AjustePendiente(
                tipo,
                fechaOrig,
                nuevaFecha,
                hora.trim(),
                MOTIVO_AJUSTADO,
                ahoraServidor);
    }

    public void registrar(AjustePendiente ajuste, Long declaracionId) {
        if (ajuste == null || logRepository == null) {
            return;
        }
        String horaTrim = ajuste.horaDeclarada();
        if (horaTrim != null && horaTrim.length() > 16) {
            horaTrim = horaTrim.substring(0, 16);
        }
        DeclaracionFechaAjusteLogModel entry = DeclaracionFechaAjusteLogModel.builder()
                .declaracionTipo(ajuste.tipo())
                .declaracionId(declaracionId)
                .fechaOriginal(java.sql.Date.valueOf(ajuste.fechaOriginal()))
                .fechaGuardada(java.sql.Date.valueOf(ajuste.fechaGuardada()))
                .horaDeclarada(horaTrim)
                .motivo(ajuste.motivo())
                .fechaServidor(ajuste.fechaServidor())
                .creadoEn(LocalDateTime.now(ApiApplication.ZONA_CHILE))
                .build();
        logRepository.save(entry);
    }

    static LocalTime parsearHora(String hora) {
        if (hora == null || hora.isBlank()) {
            return null;
        }
        String clean = hora.trim();
        try {
            return LocalTime.parse(clean, HORA_SS);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalTime.parse(clean, HORA_MM);
            } catch (DateTimeParseException ignored2) {
                return null;
            }
        }
    }
}
