package com.trazalga.api.services.geo;

import com.trazalga.api.dto.geo.*;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarCoordenadasService {

    private final ICaletaRepository caletaRepository;
    private final IVaraderoRepository varaderoRepository;
    private final IAmerbRepository amerbRepository;
    private final IRegionRepository regionRepository;
    private final ICoordenadaCambioLogRepository cambioLogRepository;
    private final ReferenciaOrigenService referenciaOrigenService;

    private static final double TOLERANCIA_DIFERENCIA_COORD = 0.0001; // ~11 metros

    /**
     * Procesa la planilla CSV de coordenadas (vista previa o aplicación transaccional).
     * Formato esperado: tipo;codigo;nombre;comuna;region;latitud;longitud;datum;fuente
     */
    @Transactional
    public ResumenImportacionDto procesarPlanilla(
            InputStream csvStream,
            boolean aplicar,
            boolean forzar,
            String usuarioActual
    ) {
        String fuenteDefault = "PLANILLA_SERNAPESCA_" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String usuario = (usuarioActual != null && !usuarioActual.isBlank()) ? usuarioActual : "ADMIN";

        // 1. Cargar maestros en memoria para un procesamiento ultrarrápido y consistente
        List<CaletaModel> todasCaletas = caletaRepository.findAll();
        List<VaraderoModel> todosVaraderos = varaderoRepository.findAll();
        List<AmerbModel> todasAmerb = amerbRepository.findAll();

        List<FilaResultadoImportacionDto> resultados = new ArrayList<>();
        List<CoordenadaCambioLogModel> logsToSave = new ArrayList<>();

        int contCalzadas = 0;
        int contAmbiguas = 0;
        int contSinCalce = 0;
        int contFueraDeRango = 0;
        int contEnConflicto = 0;
        int contAplicadas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            Integer idxTipo = null;
            Integer idxCodigo = null;
            Integer idxNombre = null;
            Integer idxComuna = null;
            Integer idxRegion = null;
            Integer idxLat = null;
            Integer idxLon = null;
            Integer idxDatum = null;
            Integer idxFuente = null;
            String separador = ";";

            while ((line = reader.readLine()) != null) {
                // Eliminar BOM UTF-8 si existe
                if (lineNumber == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                lineNumber++;

                // Detectar separador en el encabezado
                if (lineNumber == 1) {
                    if (!line.contains(";") && line.contains(",")) {
                        separador = ",";
                    }
                    String[] headers = splitCsvLine(line, separador);
                    for (int i = 0; i < headers.length; i++) {
                        String h = CoordenadaNormalizer.normalizarTexto(headers[i]);
                        if (h.equals("TIPO")) idxTipo = i;
                        else if (h.equals("CODIGO") || h.equals("COD")) idxCodigo = i;
                        else if (h.equals("NOMBRE") || h.equals("CALETA") || h.equals("VARADERO") || h.equals("AMERB")) idxNombre = i;
                        else if (h.equals("COMUNA")) idxComuna = i;
                        else if (h.equals("REGION")) idxRegion = i;
                        else if (h.startsWith("LAT")) idxLat = i;
                        else if (h.startsWith("LON") || h.startsWith("LNG")) idxLon = i;
                        else if (h.equals("DATUM")) idxDatum = i;
                        else if (h.equals("FUENTE")) idxFuente = i;
                    }
                    continue; // Fin de la línea de encabezado
                }

                // Línea de datos
                String[] tokens = splitCsvLine(line, separador);
                String tipo = getVal(tokens, idxTipo, 0);
                String codigo = getVal(tokens, idxCodigo, 1);
                String nombre = getVal(tokens, idxNombre, 2);
                String comuna = getVal(tokens, idxComuna, 3);
                String region = getVal(tokens, idxRegion, 4);
                String latRaw = getVal(tokens, idxLat, 5);
                String lonRaw = getVal(tokens, idxLon, 6);
                String datum = getVal(tokens, idxDatum, 7);
                String fuenteFila = getVal(tokens, idxFuente, 8);

                String tipoNorm = CoordenadaNormalizer.normalizarTexto(tipo);
                String nombreNorm = CoordenadaNormalizer.normalizarTexto(nombre);
                String comunaNorm = CoordenadaNormalizer.normalizarTexto(comuna);
                String codigoNorm = (codigo != null) ? codigo.trim().toUpperCase(Locale.ROOT) : "";

                Double latParsed = CoordenadaNormalizer.parsearCoordenada(latRaw, true);
                Double lonParsed = CoordenadaNormalizer.parsearCoordenada(lonRaw, false);
                boolean datumOk = CoordenadaNormalizer.esDatumWgs84(datum);
                boolean rangoOk = datumOk && CoordenadaNormalizer.estaEnRangoChile(latParsed, lonParsed);

                FilaResultadoImportacionDto filaRes = FilaResultadoImportacionDto.builder()
                        .numeroFila(lineNumber)
                        .tipo(tipoNorm)
                        .codigo(codigo)
                        .nombre(nombre)
                        .comuna(comuna)
                        .region(region)
                        .latitudRaw(latRaw)
                        .longitudRaw(lonRaw)
                        .datum(datum)
                        .fuente(fuenteFila)
                        .latitudParsed(latParsed)
                        .longitudParsed(lonParsed)
                        .aplicada(false)
                        .build();

                // 2. Validación de Coordenadas y Datum
                if (!datumOk) {
                    filaRes.setEstado(EstadoFilaCoordenada.FUERA_DE_RANGO);
                    filaRes.setDetalleEstado("Datum '" + datum + "' no soportado. Se exige WGS84.");
                    contFueraDeRango++;
                    resultados.add(filaRes);
                    continue;
                }

                if (latParsed == null || lonParsed == null || !rangoOk) {
                    filaRes.setEstado(EstadoFilaCoordenada.FUERA_DE_RANGO);
                    filaRes.setDetalleEstado(String.format(
                            "Coordenadas fuera de rango continental Chile [-56.0 a -17.4 lat, -76.0 a -66.4 lon]: lat=%s, lon=%s",
                            latParsed != null ? latParsed : "inválida",
                            lonParsed != null ? lonParsed : "inválida"
                    ));
                    contFueraDeRango++;
                    resultados.add(filaRes);
                    continue;
                }

                // 3. Calce con el maestro según tipo
                if (tipoNorm.equals("AMERB")) {
                    evaluarAmerb(filaRes, todasAmerb, codigoNorm, nombreNorm, comunaNorm);
                } else if (tipoNorm.equals("CALETA")) {
                    evaluarCaleta(filaRes, todasCaletas, nombreNorm, comunaNorm);
                } else if (tipoNorm.equals("VARADERO")) {
                    evaluarVaradero(filaRes, todosVaraderos, nombreNorm, comunaNorm);
                } else {
                    filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
                    filaRes.setDetalleEstado("Tipo de entidad '" + tipo + "' desconocido (debe ser CALETA, VARADERO o AMERB).");
                }

                // 4. Conteo de estados
                switch (filaRes.getEstado()) {
                    case CALZADA -> contCalzadas++;
                    case AMBIGUA -> contAmbiguas++;
                    case SIN_CALCE -> contSinCalce++;
                    case FUERA_DE_RANGO -> contFueraDeRango++;
                    case EN_CONFLICTO -> contEnConflicto++;
                }

                // 5. Aplicar si corresponde
                if (aplicar) {
                    boolean procede = (filaRes.getEstado() == EstadoFilaCoordenada.CALZADA)
                            || (filaRes.getEstado() == EstadoFilaCoordenada.EN_CONFLICTO && forzar);

                    if (procede && filaRes.getEntidadId() != null) {
                        String fuenteFinal = (filaRes.getFuente() != null && !filaRes.getFuente().isBlank())
                                ? filaRes.getFuente().trim()
                                : fuenteDefault;

                        aplicarCambio(filaRes, todasCaletas, todosVaraderos, todasAmerb, fuenteFinal, usuario, logsToSave);
                        filaRes.setAplicada(true);
                        contAplicadas++;
                    }
                }

                resultados.add(filaRes);
            }
        } catch (Exception e) {
            log.error("Error al procesar planilla de coordenadas", e);
            throw new RuntimeException("Error al procesar archivo CSV: " + e.getMessage(), e);
        }

        // Persistir cambios y logs en caso de aplicar
        if (aplicar) {
            caletaRepository.saveAll(todasCaletas);
            varaderoRepository.saveAll(todosVaraderos);
            amerbRepository.saveAll(todasAmerb);
            if (!logsToSave.isEmpty()) {
                cambioLogRepository.saveAll(logsToSave);
            }
            if (referenciaOrigenService != null) {
                referenciaOrigenService.invalidarCache();
            }
        }

        return ResumenImportacionDto.builder()
                .aplicado(aplicar)
                .forzado(forzar)
                .fuentePorDefecto(fuenteDefault)
                .totalFilas(resultados.size())
                .calzadas(contCalzadas)
                .ambiguas(contAmbiguas)
                .sinCalce(contSinCalce)
                .fueraDeRango(contFueraDeRango)
                .enConflicto(contEnConflicto)
                .aplicadas(contAplicadas)
                .filas(resultados)
                .build();
    }

    private void evaluarCaleta(FilaResultadoImportacionDto filaRes, List<CaletaModel> todas, String nombreNorm, String comunaNorm) {
        if (nombreNorm.isEmpty()) {
            filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
            filaRes.setDetalleEstado("Nombre de caleta no informado.");
            return;
        }

        // Calce por nombre
        List<CaletaModel> coincidenNombre = todas.stream()
                .filter(c -> CoordenadaNormalizer.normalizarTexto(c.getNombre()).equals(nombreNorm))
                .collect(Collectors.toList());

        if (coincidenNombre.isEmpty()) {
            filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
            filaRes.setDetalleEstado("No se encontró caleta con nombre '" + filaRes.getNombre() + "'.");
            return;
        }

        CaletaModel match;
        if (coincidenNombre.size() == 1) {
            match = coincidenNombre.get(0);
        } else {
            // Múltiples caletas con el mismo nombre (homónimas)
            if (comunaNorm.isEmpty()) {
                filaRes.setEstado(EstadoFilaCoordenada.AMBIGUA);
                filaRes.setDetalleEstado("Caleta homónima encontrada en múltiples comunas (" + coincidenNombre.size() + "). Se requiere especificar comuna para desambiguar.");
                return;
            }
            List<CaletaModel> coincidenComuna = coincidenNombre.stream()
                    .filter(c -> c.getComuna() != null && CoordenadaNormalizer.normalizarTexto(c.getComuna().getNombre()).equals(comunaNorm))
                    .collect(Collectors.toList());

            if (coincidenComuna.size() == 1) {
                match = coincidenComuna.get(0);
            } else if (coincidenComuna.isEmpty()) {
                filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
                filaRes.setDetalleEstado("Caleta '" + filaRes.getNombre() + "' existe pero no en comuna '" + filaRes.getComuna() + "'.");
                return;
            } else {
                filaRes.setEstado(EstadoFilaCoordenada.AMBIGUA);
                filaRes.setDetalleEstado("Múltiples registros de caleta '" + filaRes.getNombre() + "' en comuna '" + filaRes.getComuna() + "'.");
                return;
            }
        }

        vincularRegistroCaleta(filaRes, match);
    }

    private void evaluarVaradero(FilaResultadoImportacionDto filaRes, List<VaraderoModel> todos, String nombreNorm, String comunaNorm) {
        if (nombreNorm.isEmpty()) {
            filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
            filaRes.setDetalleEstado("Nombre de varadero no informado.");
            return;
        }

        List<VaraderoModel> coincidenNombre = todos.stream()
                .filter(v -> CoordenadaNormalizer.normalizarTexto(v.getNombre()).equals(nombreNorm))
                .collect(Collectors.toList());

        if (coincidenNombre.isEmpty()) {
            filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
            filaRes.setDetalleEstado("No se encontró varadero con nombre '" + filaRes.getNombre() + "'.");
            return;
        }

        VaraderoModel match;
        if (coincidenNombre.size() == 1) {
            match = coincidenNombre.get(0);
        } else {
            if (comunaNorm.isEmpty()) {
                filaRes.setEstado(EstadoFilaCoordenada.AMBIGUA);
                filaRes.setDetalleEstado("Varadero homónimo encontrado en múltiples comunas (" + coincidenNombre.size() + "). Se requiere comuna.");
                return;
            }
            List<VaraderoModel> coincidenComuna = coincidenNombre.stream()
                    .filter(v -> v.getComuna() != null && CoordenadaNormalizer.normalizarTexto(v.getComuna().getNombre()).equals(comunaNorm))
                    .collect(Collectors.toList());

            if (coincidenComuna.size() == 1) {
                match = coincidenComuna.get(0);
            } else if (coincidenComuna.isEmpty()) {
                filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
                filaRes.setDetalleEstado("Varadero '" + filaRes.getNombre() + "' existe pero no en comuna '" + filaRes.getComuna() + "'.");
                return;
            } else {
                filaRes.setEstado(EstadoFilaCoordenada.AMBIGUA);
                filaRes.setDetalleEstado("Múltiples registros de varadero '" + filaRes.getNombre() + "' en la misma comuna.");
                return;
            }
        }

        vincularRegistroVaradero(filaRes, match);
    }

    private void evaluarAmerb(FilaResultadoImportacionDto filaRes, List<AmerbModel> todas, String codigoNorm, String nombreNorm, String comunaNorm) {
        AmerbModel match = null;

        // 1. Intento por código Sernapesca
        if (!codigoNorm.isEmpty()) {
            List<AmerbModel> porCodigo = todas.stream()
                    .filter(a -> a.getCodigoSernapesca() != null && a.getCodigoSernapesca().trim().equalsIgnoreCase(codigoNorm))
                    .collect(Collectors.toList());
            if (porCodigo.size() == 1) {
                match = porCodigo.get(0);
            }
        }

        // 2. Si no por código, intentar por nombre y comuna
        if (match == null && !nombreNorm.isEmpty()) {
            List<AmerbModel> porNombre = todas.stream()
                    .filter(a -> CoordenadaNormalizer.normalizarTexto(a.getNombre()).equals(nombreNorm))
                    .collect(Collectors.toList());

            if (porNombre.size() == 1) {
                match = porNombre.get(0);
            } else if (porNombre.size() > 1) {
                if (comunaNorm.isEmpty()) {
                    filaRes.setEstado(EstadoFilaCoordenada.AMBIGUA);
                    filaRes.setDetalleEstado("AMERB homónima encontrada en múltiples ubicaciones (" + porNombre.size() + "). Falta comuna o código.");
                    return;
                }
                List<AmerbModel> porComuna = porNombre.stream()
                        .filter(a -> a.getComuna() != null && CoordenadaNormalizer.normalizarTexto(a.getComuna().getNombre()).equals(comunaNorm))
                        .collect(Collectors.toList());

                if (porComuna.size() == 1) {
                    match = porComuna.get(0);
                } else if (porComuna.isEmpty()) {
                    filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
                    filaRes.setDetalleEstado("AMERB '" + filaRes.getNombre() + "' no coincide con comuna '" + filaRes.getComuna() + "'.");
                    return;
                } else {
                    filaRes.setEstado(EstadoFilaCoordenada.AMBIGUA);
                    filaRes.setDetalleEstado("Múltiples registros AMERB con nombre y comuna coincidentes.");
                    return;
                }
            }
        }

        if (match == null) {
            filaRes.setEstado(EstadoFilaCoordenada.SIN_CALCE);
            filaRes.setDetalleEstado("No se encontró AMERB para código '" + filaRes.getCodigo() + "' o nombre '" + filaRes.getNombre() + "'.");
            return;
        }

        vincularRegistroAmerb(filaRes, match);
    }

    private void vincularRegistroCaleta(FilaResultadoImportacionDto filaRes, CaletaModel caleta) {
        filaRes.setEntidadId(caleta.getId());
        filaRes.setEntidadNombre(caleta.getNombre());
        filaRes.setEntidadComuna(caleta.getComuna() != null ? caleta.getComuna().getNombre() : null);
        filaRes.setEntidadRegion(caleta.getRegion() != null ? caleta.getRegion().getNombre() : null);
        filaRes.setLatitudActual(caleta.getLatitud());
        filaRes.setLongitudActual(caleta.getLongitud());
        filaRes.setFuenteActual(caleta.getFuenteCoordenada());

        verificarConflictoOExito(filaRes, caleta.getLatitud(), caleta.getLongitud(), caleta.getFuenteCoordenada());
    }

    private void vincularRegistroVaradero(FilaResultadoImportacionDto filaRes, VaraderoModel varadero) {
        filaRes.setEntidadId(varadero.getId());
        filaRes.setEntidadNombre(varadero.getNombre());
        filaRes.setEntidadComuna(varadero.getComuna() != null ? varadero.getComuna().getNombre() : null);
        filaRes.setLatitudActual(varadero.getLatitud());
        filaRes.setLongitudActual(varadero.getLongitud());
        filaRes.setFuenteActual(varadero.getFuenteCoordenada());

        verificarConflictoOExito(filaRes, varadero.getLatitud(), varadero.getLongitud(), varadero.getFuenteCoordenada());
    }

    private void vincularRegistroAmerb(FilaResultadoImportacionDto filaRes, AmerbModel amerb) {
        filaRes.setEntidadId(amerb.getId());
        filaRes.setEntidadNombre(amerb.getNombre());
        filaRes.setEntidadComuna(amerb.getComuna() != null ? amerb.getComuna().getNombre() : null);
        filaRes.setEntidadRegion(amerb.getRegionModel() != null ? amerb.getRegionModel().getNombre() : amerb.getRegion());
        filaRes.setLatitudActual(amerb.getLatitud());
        filaRes.setLongitudActual(amerb.getLongitud());
        filaRes.setFuenteActual(amerb.getFuenteCoordenada());

        verificarConflictoOExito(filaRes, amerb.getLatitud(), amerb.getLongitud(), amerb.getFuenteCoordenada());
    }

    private void verificarConflictoOExito(FilaResultadoImportacionDto filaRes, Double latAct, Double lonAct, String fuenteAct) {
        boolean esManual = fuenteAct != null && fuenteAct.trim().equalsIgnoreCase("MANUAL");
        boolean tieneCoordsActuales = latAct != null && lonAct != null;

        if (esManual && tieneCoordsActuales) {
            double difLat = Math.abs(latAct - filaRes.getLatitudParsed());
            double difLon = Math.abs(lonAct - filaRes.getLongitudParsed());
            if (difLat > TOLERANCIA_DIFERENCIA_COORD || difLon > TOLERANCIA_DIFERENCIA_COORD) {
                filaRes.setEstado(EstadoFilaCoordenada.EN_CONFLICTO);
                filaRes.setDetalleEstado("Conflicto con coordenada ingresada manualmente (dif: " +
                        String.format(Locale.US, "%.5f, %.5f", difLat, difLon) + "°). Requiere forzar=true para sobreescribir.");
                return;
            }
        }

        filaRes.setEstado(EstadoFilaCoordenada.CALZADA);
        filaRes.setDetalleEstado("Calce unívoco. Lista para actualizar.");
    }

    private void aplicarCambio(
            FilaResultadoImportacionDto filaRes,
            List<CaletaModel> caletas,
            List<VaraderoModel> varaderos,
            List<AmerbModel> amerbs,
            String fuenteFinal,
            String usuario,
            List<CoordenadaCambioLogModel> logs
    ) {
        LocalDateTime now = LocalDateTime.now();

        if (filaRes.getTipo().equalsIgnoreCase("CALETA")) {
            caletas.stream()
                    .filter(c -> c.getId().equals(filaRes.getEntidadId()))
                    .findFirst()
                    .ifPresent(c -> {
                        logs.add(CoordenadaCambioLogModel.builder()
                                .entidadTipo("CALETA")
                                .entidadId(c.getId())
                                .entidadNombre(c.getNombre())
                                .latitudAnterior(c.getLatitud())
                                .longitudAnterior(c.getLongitud())
                                .fuenteAnterior(c.getFuenteCoordenada())
                                .latitudNueva(filaRes.getLatitudParsed())
                                .longitudNueva(filaRes.getLongitudParsed())
                                .fuenteNueva(fuenteFinal)
                                .actualizadoEn(now)
                                .actualizadoPor(usuario)
                                .build());

                        c.setLatitud(filaRes.getLatitudParsed());
                        c.setLongitud(filaRes.getLongitudParsed());
                        c.setFuenteCoordenada(fuenteFinal);
                        c.setCoordenadaActualizadaEn(now);
                        c.setCoordenadaActualizadaPor(usuario);
                    });
        } else if (filaRes.getTipo().equalsIgnoreCase("VARADERO")) {
            varaderos.stream()
                    .filter(v -> v.getId().equals(filaRes.getEntidadId()))
                    .findFirst()
                    .ifPresent(v -> {
                        logs.add(CoordenadaCambioLogModel.builder()
                                .entidadTipo("VARADERO")
                                .entidadId(v.getId())
                                .entidadNombre(v.getNombre())
                                .latitudAnterior(v.getLatitud())
                                .longitudAnterior(v.getLongitud())
                                .fuenteAnterior(v.getFuenteCoordenada())
                                .latitudNueva(filaRes.getLatitudParsed())
                                .longitudNueva(filaRes.getLongitudParsed())
                                .fuenteNueva(fuenteFinal)
                                .actualizadoEn(now)
                                .actualizadoPor(usuario)
                                .build());

                        v.setLatitud(filaRes.getLatitudParsed());
                        v.setLongitud(filaRes.getLongitudParsed());
                        v.setFuenteCoordenada(fuenteFinal);
                        v.setCoordenadaActualizadaEn(now);
                        v.setCoordenadaActualizadaPor(usuario);
                    });
        } else if (filaRes.getTipo().equalsIgnoreCase("AMERB")) {
            amerbs.stream()
                    .filter(a -> a.getId().equals(filaRes.getEntidadId()))
                    .findFirst()
                    .ifPresent(a -> {
                        logs.add(CoordenadaCambioLogModel.builder()
                                .entidadTipo("AMERB")
                                .entidadId(a.getId())
                                .entidadNombre(a.getNombre())
                                .latitudAnterior(a.getLatitud())
                                .longitudAnterior(a.getLongitud())
                                .fuenteAnterior(a.getFuenteCoordenada())
                                .latitudNueva(filaRes.getLatitudParsed())
                                .longitudNueva(filaRes.getLongitudParsed())
                                .fuenteNueva(fuenteFinal)
                                .actualizadoEn(now)
                                .actualizadoPor(usuario)
                                .build());

                        a.setLatitud(filaRes.getLatitudParsed());
                        a.setLongitud(filaRes.getLongitudParsed());
                        a.setFuenteCoordenada(fuenteFinal);
                        a.setCoordenadaActualizadaEn(now);
                        a.setCoordenadaActualizadaPor(usuario);
                    });
        }
    }

    /**
     * Calcula la cobertura de georreferenciación global y desglosada por región.
     */
    @Transactional(readOnly = true)
    public CoberturaCoordenadasDto obtenerCobertura() {
        List<CaletaModel> caletas = caletaRepository.findAll();
        List<VaraderoModel> varaderos = varaderoRepository.findAll();
        List<AmerbModel> amerbs = amerbRepository.findAll();
        List<RegionModel> regiones = regionRepository.findAll();

        long totCal = caletas.size();
        long calConGps = caletas.stream().filter(c -> c.getLatitud() != null && c.getLongitud() != null).count();

        long totVar = varaderos.size();
        long varConGps = varaderos.stream().filter(v -> v.getLatitud() != null && v.getLongitud() != null).count();

        long totAmerb = amerbs.size();
        long amerbConGps = amerbs.stream().filter(a -> a.getLatitud() != null && a.getLongitud() != null).count();

        long totalEntidades = totCal + totVar + totAmerb;
        long totalConGps = calConGps + varConGps + amerbConGps;

        List<CoberturaCoordenadasDto.CoberturaRegionDto> porRegion = new ArrayList<>();

        for (RegionModel reg : regiones) {
            Long rId = reg.getId();
            String rNom = reg.getNombre();

            long rCalTot = caletas.stream().filter(c -> c.getRegion() != null && rId.equals(c.getRegion().getId())).count();
            long rCalGps = caletas.stream().filter(c -> c.getRegion() != null && rId.equals(c.getRegion().getId()) && c.getLatitud() != null && c.getLongitud() != null).count();

            // Varaderos: asociados por comuna o región si aplica
            long rVarTot = varaderos.stream().filter(v -> v.getComuna() != null && v.getComuna().getRegion() != null && rId.equals(v.getComuna().getRegion().getId())).count();
            long rVarGps = varaderos.stream().filter(v -> v.getComuna() != null && v.getComuna().getRegion() != null && rId.equals(v.getComuna().getRegion().getId()) && v.getLatitud() != null && v.getLongitud() != null).count();

            // AMERB: asociados por regionModel o region de la comuna
            long rAmerbTot = amerbs.stream().filter(a -> {
                if (a.getRegionModel() != null) return rId.equals(a.getRegionModel().getId());
                if (a.getComuna() != null && a.getComuna().getRegion() != null) return rId.equals(a.getComuna().getRegion().getId());
                return false;
            }).count();

            long rAmerbGps = amerbs.stream().filter(a -> {
                boolean matchReg = false;
                if (a.getRegionModel() != null) matchReg = rId.equals(a.getRegionModel().getId());
                else if (a.getComuna() != null && a.getComuna().getRegion() != null) matchReg = rId.equals(a.getComuna().getRegion().getId());
                return matchReg && a.getLatitud() != null && a.getLongitud() != null;
            }).count();

            long rTotEnt = rCalTot + rVarTot + rAmerbTot;
            long rTotGps = rCalGps + rVarGps + rAmerbGps;

            porRegion.add(CoberturaCoordenadasDto.CoberturaRegionDto.builder()
                    .regionId(rId)
                    .regionNombre(rNom)
                    .caletasTotal(rCalTot)
                    .caletasConGps(rCalGps)
                    .porcentajeCaletas(calcPct(rCalGps, rCalTot))
                    .varaderosTotal(rVarTot)
                    .varaderosConGps(rVarGps)
                    .porcentajeVaraderos(calcPct(rVarGps, rVarTot))
                    .amerbTotal(rAmerbTot)
                    .amerbConGps(rAmerbGps)
                    .porcentajeAmerb(calcPct(rAmerbGps, rAmerbTot))
                    .totalEntidades(rTotEnt)
                    .totalConGps(rTotGps)
                    .porcentajeTotal(calcPct(rTotGps, rTotEnt))
                    .build());
        }

        // Ordenar por ID de región
        porRegion.sort(Comparator.comparing(CoberturaCoordenadasDto.CoberturaRegionDto::getRegionId, Comparator.nullsLast(Long::compareTo)));

        return CoberturaCoordenadasDto.builder()
                .totalCaletas(totCal)
                .caletasConGps(calConGps)
                .porcentajeCaletas(calcPct(calConGps, totCal))
                .totalVaraderos(totVar)
                .varaderosConGps(varConGps)
                .porcentajeVaraderos(calcPct(varConGps, totVar))
                .totalAmerb(totAmerb)
                .amerbConGps(amerbConGps)
                .porcentajeAmerb(calcPct(amerbConGps, totAmerb))
                .totalEntidades(totalEntidades)
                .totalConGps(totalConGps)
                .porcentajeTotal(calcPct(totalConGps, totalEntidades))
                .porRegion(porRegion)
                .build();
    }

    private double calcPct(long part, long total) {
        if (total <= 0) return 0.0;
        double pct = ((double) part / (double) total) * 100.0;
        return Math.round(pct * 10.0) / 10.0;
    }

    private String[] splitCsvLine(String line, String separador) {
        // Soporta comillas y separador
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char sep = separador.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == sep && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        return list.toArray(new String[0]);
    }

    private String getVal(String[] tokens, Integer index, int fallbackIndex) {
        int idx = (index != null) ? index : fallbackIndex;
        if (idx >= 0 && idx < tokens.length) {
            String val = tokens[idx];
            if (val != null) {
                val = val.replaceAll("^\"|\"$", "").trim();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }
}
