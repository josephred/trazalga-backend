package com.trazalga.api.services;

import com.trazalga.api.dto.*;
import com.trazalga.api.dto.geo.GeoDeclaracionDTO;
import com.trazalga.api.dto.geo.PuntoReferencia;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.geo.ReferenciaOrigenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Servicio para la construcción y consolidación de la Ficha de Trazabilidad (T2.3 / TG.3).
 * Utiliza CadenaCustodiaService para el recorrido canónico del grafo de custodia y
 * ReferenciaOrigenService para la doble georreferenciación.
 */
@Service
@Slf4j
public class FichaTrazabilidadService {

    private final CadenaCustodiaService cadenaCustodiaService;
    private final DeclaracionMarcaService declaracionMarcaService;
    private final BloqueoCargaService bloqueoCargaService;

    private final IDeclaracionRecolectorRepository recolectorRepository;
    private final IDeclaracionArmadorRepository armadorRepository;
    private final IDeclaracionAreaRepository areaRepository;
    private final IDeclaracionComercializadorRepository comercializadorRepository;
    private final IDeclaracionPlantaAbastecimientoRepository plantaRepository;
    private final DeclaracionBuzosRepository buzosRepository;

    @Autowired(required = false)
    private FactorConversionService factorConversionService;

    @Autowired(required = false)
    private com.trazalga.api.services.indicadores.VariacionPesoService variacionPesoService;

    @Autowired(required = false)
    private ReferenciaOrigenService referenciaOrigenService;

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    public void setReferenciaOrigenService(ReferenciaOrigenService referenciaOrigenService) {
        this.referenciaOrigenService = referenciaOrigenService;
    }

    public void setConfigService(ConfiguracionGeneralService configService) {
        this.configService = configService;
    }

    @Autowired
    public FichaTrazabilidadService(
            CadenaCustodiaService cadenaCustodiaService,
            @Autowired(required = false) DeclaracionMarcaService declaracionMarcaService,
            @Autowired(required = false) BloqueoCargaService bloqueoCargaService,
            @Autowired(required = false) IDeclaracionRecolectorRepository recolectorRepository,
            @Autowired(required = false) IDeclaracionArmadorRepository armadorRepository,
            @Autowired(required = false) IDeclaracionAreaRepository areaRepository,
            @Autowired(required = false) IDeclaracionComercializadorRepository comercializadorRepository,
            @Autowired(required = false) IDeclaracionPlantaAbastecimientoRepository plantaRepository,
            @Autowired(required = false) DeclaracionBuzosRepository buzosRepository,
            @Autowired(required = false) FactorConversionService factorConversionService,
            @Autowired(required = false) com.trazalga.api.services.indicadores.VariacionPesoService variacionPesoService) {
        this.cadenaCustodiaService = cadenaCustodiaService;
        this.declaracionMarcaService = declaracionMarcaService;
        this.bloqueoCargaService = bloqueoCargaService;
        this.recolectorRepository = recolectorRepository;
        this.armadorRepository = armadorRepository;
        this.areaRepository = areaRepository;
        this.comercializadorRepository = comercializadorRepository;
        this.plantaRepository = plantaRepository;
        this.buzosRepository = buzosRepository;
        this.factorConversionService = factorConversionService;
        this.variacionPesoService = variacionPesoService;
    }

    private double getUmbralGeoKm() {
        return configService != null ? configService.getDouble("origen_geo_distancia_max_km", 30.0) : 30.0;
    }

    private double getPrecisionMaxM() {
        return configService != null ? configService.getDouble("origen_geo_precision_max_m", 500.0) : 500.0;
    }

    private com.trazalga.api.services.indicadores.CoherenciaHumedad.ParametrosCoherencia getParametrosCoherencia() {
        if (configService == null) {
            return com.trazalga.api.services.indicadores.CoherenciaHumedad.ParametrosCoherencia.porDefecto();
        }
        return new com.trazalga.api.services.parametros.ParametrosService(configService).coherenciaHumedad();
    }

    private PuntoReferencia resolverRefFallbackEntidad(Object entidad) {
        if (referenciaOrigenService != null) {
            if (entidad instanceof DeclaracionRecolectorModel dr) return referenciaOrigenService.resolverRecolector(dr);
            if (entidad instanceof DeclaracionArmadorModel da) return referenciaOrigenService.resolverArmador(da);
            if (entidad instanceof DeclaracionAreaModel dam) return referenciaOrigenService.resolverArea(dam);
        }
        // Fallback directo desde relaciones cargadas en la entidad (útil en tests sin ReferenciaOrigenService)
        if (entidad instanceof DeclaracionRecolectorModel dr) {
            if (dr.getVaraderoRelacion() != null && dr.getVaraderoRelacion().getLatitud() != null && dr.getVaraderoRelacion().getLongitud() != null) {
                VaraderoModel v = dr.getVaraderoRelacion();
                return new PuntoReferencia("VARADERO", v.getId(), v.getNombre(), v.getLatitud(), v.getLongitud(), "VARADERO_DECLARADO");
            }
            if (dr.getCaleta() != null && dr.getCaleta().getLatitud() != null && dr.getCaleta().getLongitud() != null) {
                CaletaModel c = dr.getCaleta();
                return new PuntoReferencia("CALETA", c.getId(), c.getNombre(), c.getLatitud(), c.getLongitud(), "CALETA");
            }
            if (dr.getCaleta() != null && dr.getCaleta().getVaradero() != null
                    && dr.getCaleta().getVaradero().getLatitud() != null && dr.getCaleta().getVaradero().getLongitud() != null) {
                VaraderoModel vc = dr.getCaleta().getVaradero();
                return new PuntoReferencia("VARADERO", vc.getId(), vc.getNombre(), vc.getLatitud(), vc.getLongitud(), "VARADERO_CALETA");
            }
        } else if (entidad instanceof DeclaracionArmadorModel da) {
            if (da.getCaleta() != null && da.getCaleta().getLatitud() != null && da.getCaleta().getLongitud() != null) {
                CaletaModel c = da.getCaleta();
                return new PuntoReferencia("CALETA", c.getId(), c.getNombre(), c.getLatitud(), c.getLongitud(), "CALETA");
            }
            if (da.getCaleta() != null && da.getCaleta().getVaradero() != null
                    && da.getCaleta().getVaradero().getLatitud() != null && da.getCaleta().getVaradero().getLongitud() != null) {
                VaraderoModel vc = da.getCaleta().getVaradero();
                return new PuntoReferencia("VARADERO", vc.getId(), vc.getNombre(), vc.getLatitud(), vc.getLongitud(), "VARADERO_CALETA");
            }
        } else if (entidad instanceof DeclaracionAreaModel dam) {
            if (dam.getAmerb() != null && dam.getAmerb().getLatitud() != null && dam.getAmerb().getLongitud() != null) {
                AmerbModel am = dam.getAmerb();
                return new PuntoReferencia("AMERB", am.getId(), am.getNombre(), am.getLatitud(), am.getLongitud(), "AMERB");
            }
            if (dam.getCaleta() != null && dam.getCaleta().getLatitud() != null && dam.getCaleta().getLongitud() != null) {
                CaletaModel c = dam.getCaleta();
                return new PuntoReferencia("CALETA", c.getId(), c.getNombre(), c.getLatitud(), c.getLongitud(), "CALETA");
            }
        }
        return null;
    }

    /**
     * Construye la ficha completa de trazabilidad a partir del tipo e ID de cualquier eslabón.
     *
     * @param tipo Tipo de declaración consultada (RECOLECTOR, ARMADOR, AREA, COMERCIALIZADOR, PLANTA_ABASTECIMIENTO)
     * @param id   ID de la declaración
     * @return DTO consolidado con Origen, Comercializador, Planta y Alertas
     */
    @Transactional(readOnly = true)
    public FichaTrazabilidadDTO obtenerFicha(String tipo, Long id) {
        if (tipo == null || id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo e ID son requeridos");
        }

        String tipoNorm = normalizarTipo(tipo);

        // 1. Resolver el grafo completo de la cadena de custodia
        CadenaCustodiaService.Cadena cadena = cadenaCustodiaService.resolver(tipoNorm, id);
        if (cadena == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró información de la cadena para " + tipoNorm + " #" + id);
        }

        // 2. Construir lista de orígenes enriquecida
        List<FichaOrigenDTO> origenesDTO = new ArrayList<>();
        BigDecimal totalKgOrigen = BigDecimal.ZERO;
        String especiePredominante = null;

        for (CadenaCustodiaService.OrigenRef ref : cadena.getOrigenes()) {
            FichaOrigenDTO oDTO = construirOrigenDTO(ref, tipoNorm, id);
            if (oDTO != null) {
                origenesDTO.add(oDTO);
                if (oDTO.getDesembarqueKg() != null) {
                    totalKgOrigen = totalKgOrigen.add(oDTO.getDesembarqueKg());
                }
                if (especiePredominante == null && oDTO.getEspecie() != null) {
                    especiePredominante = oDTO.getEspecie();
                }
            }
        }

        // 3. Construir lista de comercializadores por tramos/saltos
        Date maxFechaExtraccion = origenesDTO.stream()
                .map(FichaOrigenDTO::getFechaExtraccion)
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(null);

        List<FichaComercializadorDTO> comDTOList = new ArrayList<>();
        for (CadenaCustodiaService.ComercializadorTramo tramo : cadena.getComercializadoresTramos()) {
            FichaComercializadorDTO cDTO = construirComercializadorDTO(tramo, maxFechaExtraccion, cadena.getEstadoHumedadPredominante());
            if (cDTO != null) {
                comDTOList.add(cDTO);
            }
        }

        BigDecimal totalCapturaOrigen = BigDecimal.ZERO;
        for (FichaOrigenDTO o : origenesDTO) {
            BigDecimal cap = o.getCapturaKg() != null ? o.getCapturaKg() : (o.getDesembarqueKg() != null ? o.getDesembarqueKg() : BigDecimal.ZERO);
            totalCapturaOrigen = totalCapturaOrigen.add(cap);
        }

        // 4. Construir bloque de planta de abastecimiento (T3.3 / TH.3 / TH.5)
        FichaPlantaDTO plantaDTO = construirPlantaDTO(cadena.getPlantaAbastecimientoId(), totalKgOrigen, totalCapturaOrigen, maxFechaExtraccion);

        // 5. Construir resumen superior de alertas y retenciones de toda la cadena
        FichaAlertasDTO alertasDTO = construirAlertasDTO(origenesDTO, comDTOList, plantaDTO);

        return FichaTrazabilidadDTO.builder()
                .tipoConsulta(tipoNorm)
                .idConsulta(id)
                .fechaConsulta(new Date())
                .alertas(alertasDTO)
                .origenes(origenesDTO)
                .comercializadores(comDTOList)
                .planta(plantaDTO)
                .totalKgOrigen(totalKgOrigen)
                .especiePredominante(especiePredominante)
                .humedadPredominante(cadena.getEstadoHumedadPredominante())
                .build();
    }

    // =========================================================================
    // CONSTRUCCIÓN DE ORIGEN
    // =========================================================================

    private FichaOrigenDTO construirOrigenDTO(CadenaCustodiaService.OrigenRef ref, String tipoConsulta, Long idConsulta) {
        String t = ref.getTipo();
        Long oId = ref.getId();
        boolean consultada = t.equalsIgnoreCase(tipoConsulta) && oId.equals(idConsulta);
        boolean mismaCarga = !consultada;

        double umbralKm = getUmbralGeoKm();
        double precisionMaxM = getPrecisionMaxM();

        FichaOrigenDTO.FichaOrigenDTOBuilder b = FichaOrigenDTO.builder()
                .id(oId)
                .tipo(t)
                .consultada(consultada)
                .mismaCarga(mismaCarga);

        if ("RECOLECTOR".equalsIgnoreCase(t)) {
            Optional<DeclaracionRecolectorModel> opt = recolectorRepository != null ? recolectorRepository.findById(oId) : Optional.empty();
            if (opt.isPresent()) {
                DeclaracionRecolectorModel r = opt.get();
                PuntoReferencia puntoRef = resolverRefFallbackEntidad(r);
                String capEn = r.getGpsCapturadoEn() != null ? r.getGpsCapturadoEn().toString() : null;
                GeoDeclaracionDTO geo = ReferenciaOrigenService.construirGeoDeclaracion(
                        "RECOLECTOR", r.getLatitud(), r.getLongitud(), r.getPrecisionGpsM(), capEn, r.getEnvioOffline(),
                        puntoRef, umbralKm, precisionMaxM);

                b.rut(r.getUsuario() != null ? r.getUsuario().getRut() : null)
                 .nombre(r.getUsuario() != null ? formatNombre(r.getUsuario()) : r.getNombre())
                 .perfil(r.getUsuario() != null && r.getUsuario().getPerfil() != null ? r.getUsuario().getPerfil().getNombre() : "Recolector de orilla")
                 .metodoExtraccion(r.getExtraccionTipo() != null ? r.getExtraccionTipo().getNombre() : null)
                 .especie(r.getEspecie() != null ? r.getEspecie().getNombre() : ref.getEspecieNombre())
                 .estadoHumedad(r.getHumedadEstado() != null ? r.getHumedadEstado().getNombre() : ref.getHumedadNombre())
                 .humedadHigrometro(r.getHumedad())
                 .caleta(r.getCaleta() != null ? r.getCaleta().getNombre() : null)
                 .comuna(r.getComuna() != null ? r.getComuna().getNombre() : (r.getCaleta() != null && r.getCaleta().getComuna() != null ? r.getCaleta().getComuna().getNombre() : null))
                 .region(r.getComuna() != null && r.getComuna().getRegion() != null ? r.getComuna().getRegion().getNombre() : (r.getCaleta() != null && r.getCaleta().getRegion() != null ? r.getCaleta().getRegion().getNombre() : null))
                 .varaderoOAmerb(r.getVaradero())
                 .fechaExtraccion(r.getFechaExtraccion())
                 .fechaDeclaracion(r.getFechaDeclaracion())
                 .hora(r.getHora())
                 .desembarqueKg(r.getDesembarque())
                 .capturaKg(r.getCaptura())
                 .folioOrigen(r.getFolioOrigen())
                 .folioDesembarque(r.getFolioDesembarqueRo())
                 .estado(r.getEstado() != null ? r.getEstado() : "ENVIADA")
                 .geo(geo);
            } else {
                b.desembarqueKg(ref.getCantidad())
                 .folioOrigen(ref.getFolio())
                 .fechaExtraccion(ref.getFechaExtraccion())
                 .fechaDeclaracion(ref.getFecha())
                 .hora(ref.getHora())
                 .especie(ref.getEspecieNombre())
                 .estadoHumedad(ref.getHumedadNombre())
                 .nombre(ref.getActor())
                 .rut(ref.getRut())
                 .geo(ReferenciaOrigenService.construirGeoDeclaracion("RECOLECTOR", null, null, null, null, false, null, umbralKm, precisionMaxM));
            }
        } else if ("ARMADOR".equalsIgnoreCase(t)) {
            Optional<DeclaracionArmadorModel> opt = armadorRepository != null ? armadorRepository.findById(oId) : Optional.empty();
            if (opt.isPresent()) {
                DeclaracionArmadorModel a = opt.get();
                PuntoReferencia puntoRef = resolverRefFallbackEntidad(a);
                String capEn = a.getGpsCapturadoEn() != null ? a.getGpsCapturadoEn().toString() : null;
                GeoDeclaracionDTO geo = ReferenciaOrigenService.construirGeoDeclaracion(
                        "ARMADOR", a.getLatitud(), a.getLongitud(), a.getPrecisionGpsM(), capEn, a.getEnvioOffline(),
                        puntoRef, umbralKm, precisionMaxM);

                String embNom = a.getEmbarcacion() != null ? a.getEmbarcacion().getNombre() : null;
                String embCod = a.getEmbarcacion() != null ? a.getEmbarcacion().getCodigo() : a.getCodigoSernapescaEmbarcacion();
                String buzoNom = a.getBuzo() != null ? a.getBuzo().getNombre() : null;
                String buzoCod = a.getBuzo() != null ? a.getBuzo().getCodigo() : a.getCodigoSernapescaBuzo();

                // Revisar también si hay buzos secundarios en declaracion_buzos
                if (buzosRepository != null && (buzoNom == null || buzoNom.isBlank())) {
                    List<DeclaracionBuzosModel> secondaryBuzos = buzosRepository.findByDeclaracionArmadorId(a.getId());
                    if (!secondaryBuzos.isEmpty() && secondaryBuzos.get(0).getBuzo() != null) {
                        buzoNom = secondaryBuzos.get(0).getBuzo().getNombre();
                        buzoCod = secondaryBuzos.get(0).getBuzo().getCodigo();
                    }
                }

                b.rut(a.getUsuario() != null ? a.getUsuario().getRut() : null)
                 .nombre(a.getUsuario() != null ? formatNombre(a.getUsuario()) : null)
                 .perfil(a.getUsuario() != null && a.getUsuario().getPerfil() != null ? a.getUsuario().getPerfil().getNombre() : "Armador artesanal")
                 .embarcacionNombre(embNom)
                 .embarcacionCodigo(embCod)
                 .buzoNombre(buzoNom)
                 .buzoCodigo(buzoCod)
                 .metodoExtraccion(a.getExtraccionTipo() != null ? a.getExtraccionTipo().getNombre() : null)
                 .especie(a.getEspecie() != null ? a.getEspecie().getNombre() : ref.getEspecieNombre())
                 .estadoHumedad(a.getHumedadEstado() != null ? a.getHumedadEstado().getNombre() : ref.getHumedadNombre())
                 .caleta(a.getCaleta() != null ? a.getCaleta().getNombre() : null)
                 .comuna(a.getComuna() != null ? a.getComuna().getNombre() : (a.getCaleta() != null && a.getCaleta().getComuna() != null ? a.getCaleta().getComuna().getNombre() : null))
                 .region(a.getComuna() != null && a.getComuna().getRegion() != null ? a.getComuna().getRegion().getNombre() : (a.getCaleta() != null && a.getCaleta().getRegion() != null ? a.getCaleta().getRegion().getNombre() : null))
                 .varaderoOAmerb(a.getCaleta() != null && a.getCaleta().getVaradero() != null ? a.getCaleta().getVaradero().getNombre() : null)
                 .fechaExtraccion(a.getFechaExtraccion())
                 .fechaDeclaracion(a.getFechaDeclaracion())
                 .hora(a.getHora())
                 .desembarqueKg(a.getDesembarque())
                 .capturaKg(a.getCaptura() != null ? BigDecimal.valueOf(a.getCaptura()) : null)
                 .folioOrigen(a.getFolioOrigen())
                 .folioDesembarque(a.getFolioDesembarqueDa())
                 .estado(a.getEstado() != null ? a.getEstado() : "ENVIADA")
                 .geo(geo);
            } else {
                b.desembarqueKg(ref.getCantidad())
                 .folioOrigen(ref.getFolio())
                 .fechaExtraccion(ref.getFechaExtraccion())
                 .fechaDeclaracion(ref.getFecha())
                 .hora(ref.getHora())
                 .especie(ref.getEspecieNombre())
                 .estadoHumedad(ref.getHumedadNombre())
                 .nombre(ref.getActor())
                 .rut(ref.getRut())
                 .geo(ReferenciaOrigenService.construirGeoDeclaracion("ARMADOR", null, null, null, null, false, null, umbralKm, precisionMaxM));
            }
        } else if ("AREA".equalsIgnoreCase(t)) {
            Optional<DeclaracionAreaModel> opt = areaRepository != null ? areaRepository.findById(oId) : Optional.empty();
            if (opt.isPresent()) {
                DeclaracionAreaModel ar = opt.get();
                PuntoReferencia puntoRef = resolverRefFallbackEntidad(ar);
                String capEn = ar.getGpsCapturadoEn() != null ? ar.getGpsCapturadoEn().toString() : null;
                GeoDeclaracionDTO geo = ReferenciaOrigenService.construirGeoDeclaracion(
                        "AREA", ar.getLatitud(), ar.getLongitud(), ar.getPrecisionGpsM(), capEn, ar.getEnvioOffline(),
                        puntoRef, umbralKm, precisionMaxM);

                String amerbNom = ar.getAmerb() != null ? ar.getAmerb().getNombre() : ar.getCodigoSernapescaAmerb();

                b.rut(ar.getUsuario() != null ? ar.getUsuario().getRut() : null)
                 .nombre(ar.getUsuario() != null ? formatNombre(ar.getUsuario()) : null)
                 .perfil(ar.getUsuario() != null && ar.getUsuario().getPerfil() != null ? ar.getUsuario().getPerfil().getNombre() : "Organización AMERB")
                 .especie(ar.getEspecie() != null ? ar.getEspecie().getNombre() : ref.getEspecieNombre())
                 .estadoHumedad(ar.getHumedadEstado() != null ? ar.getHumedadEstado().getNombre() : ref.getHumedadNombre())
                 .caleta(ar.getCaleta() != null ? ar.getCaleta().getNombre() : null)
                 .varaderoOAmerb(amerbNom)
                 .fechaExtraccion(ar.getFechaExtraccion())
                 .fechaDeclaracion(ar.getFechaDeclaracion())
                 .hora(ar.getHora())
                 .desembarqueKg(ar.getDesembarque() != null ? BigDecimal.valueOf(ar.getDesembarque()) : null)
                 .capturaKg(ar.getCaptura() != null ? BigDecimal.valueOf(ar.getCaptura()) : null)
                 .folioOrigen(ar.getFolioOrigen())
                 .folioDesembarque(ar.getFolioDesembarqueAmerb())
                 .estado("ENVIADA")
                 .geo(geo);
            } else {
                b.desembarqueKg(ref.getCantidad())
                 .folioOrigen(ref.getFolio())
                 .fechaExtraccion(ref.getFechaExtraccion())
                 .fechaDeclaracion(ref.getFecha())
                 .hora(ref.getHora())
                 .especie(ref.getEspecieNombre())
                 .estadoHumedad(ref.getHumedadNombre())
                 .nombre(ref.getActor())
                 .rut(ref.getRut())
                 .geo(ReferenciaOrigenService.construirGeoDeclaracion("AREA", null, null, null, null, false, null, umbralKm, precisionMaxM));
            }
        }

        // Marcas y retenciones del origen
        List<FichaMarcaDTO> marcas = obtenerMarcasDTO(t, oId);
        b.marcas(marcas);

        MotivoBloqueoDTO bloqueo = obtenerBloqueo(t, oId);
        if (bloqueo != null) {
            b.retenida(true);
            b.motivoBloqueo("Retenida por " + bloqueo.getMarca() + " (hallazgo #" + bloqueo.getMarcaId() + ")");
        } else {
            b.retenida(false);
        }

        FichaOrigenDTO built = b.build();

        // TH.5: Calcular coherencia de humedad del origen
        LocalDateTime momentoOrigen = ReportService.parseTimestamp(built.getFechaDeclaracion(), built.getHora());
        com.trazalga.api.services.indicadores.CoherenciaHumedad.ResultadoCoherencia resCoh =
                com.trazalga.api.services.indicadores.CoherenciaHumedad.evaluar(
                        built.getEstadoHumedad(), built.getFechaExtraccion(), momentoOrigen, getParametrosCoherencia());
        built.setEdadExtraccionHoras(resCoh.getHMin());
        built.setEstadoEsperadoHumedad(resCoh.getTramoMinimo());
        built.setResultadoCoherenciaHumedad(resCoh.getResultado());

        return built;
    }

    // =========================================================================
    // CONSTRUCCIÓN DE COMERCIALIZADOR
    // =========================================================================

    private FichaComercializadorDTO construirComercializadorDTO(
            CadenaCustodiaService.ComercializadorTramo tramo,
            Date fechaExtraccionOrigen,
            String humedadPredominante) {
        double umbralKm = getUmbralGeoKm();
        double precisionMaxM = getPrecisionMaxM();
        String estadoHumedadDecl = humedadPredominante;

        FichaComercializadorDTO.FichaComercializadorDTOBuilder b = FichaComercializadorDTO.builder()
                .id(tramo.getId())
                .salto(tramo.getSalto())
                .despachado(tramo.isDespachado())
                .fechaRecepcion(toDate(tramo.getEntrada()))
                .horaRecepcion(formatHora(tramo.getEntrada()))
                .horasEnBodega(tramo.getHorasEnBodega())
                .semaforo(tramo.getSemaforo());

        if (tramo.isDespachado() && tramo.getId() != null) {
            b.notaRecepcion("fecha de la declaración de origen que lo nombra destinatario");

            Optional<DeclaracionComercializadorModel> opt = comercializadorRepository != null
                    ? comercializadorRepository.findById(tramo.getId())
                    : Optional.empty();

            if (opt.isPresent()) {
                DeclaracionComercializadorModel c = opt.get();
                if (c.getHumedadEstado() != null && c.getHumedadEstado().getNombre() != null) {
                    estadoHumedadDecl = c.getHumedadEstado().getNombre();
                }
                String capEn = c.getGpsCapturadoEn() != null ? c.getGpsCapturadoEn().toString() : null;
                GeoDeclaracionDTO geo = ReferenciaOrigenService.construirGeoDeclaracion(
                        "COMERCIALIZADOR", c.getLatitud(), c.getLongitud(), c.getPrecisionGpsM(), capEn, c.getEnvioOffline(),
                        null, umbralKm, precisionMaxM);

                b.rut(c.getUsuario() != null ? c.getUsuario().getRut() : tramo.getRut())
                 .nombre(c.getUsuario() != null ? formatNombre(c.getUsuario()) : (c.getNombreComercializador() != null ? c.getNombreComercializador() : tramo.getActor()))
                 .fechaDespacho(c.getFechaDeclaracion())
                 .horaDespacho(c.getHora())
                 .fechaTraslado(c.getFechaTraslado())
                 .cantidadKg(c.getCantidad())
                 .patenteCamion(c.getPlacaPatente() != null && !c.getPlacaPatente().isBlank() ? c.getPlacaPatente() : c.getPatente())
                 .patenteCarro(c.getPlacaPatenteCarro())
                 .chofer(c.getChoferTransporte())
                 .rutChofer(c.getRutChofer())
                 .docOrigenTipo(c.getDocumentoTributarioOrigenTipo())
                 .docOrigenNumero(c.getDocumentoTributarioOrigenNumero())
                 .docOrigenFecha(c.getDocumentoTributarioOrigenFecha())
                 .docDestinoTipo(c.getDocumentoTributarioDestinoTipo())
                 .docDestinoNumero(c.getDocumentoTributarioDestinoNumero())
                 .docDestinoFecha(c.getDocumentoTributarioDestinoFecha())
                 .folioOrigen(c.getFolioOrigen())
                 .folioDesembarqueAc(c.getFolioDesembarqueAc())
                 .estado(c.getEstado() != null ? c.getEstado() : "ENVIADA")
                 .geo(geo);

                // Marcas y bloqueo del comercializador
                b.marcas(obtenerMarcasDTO("COMERCIALIZADOR", c.getId()));
                MotivoBloqueoDTO bloqueo = obtenerBloqueo("COMERCIALIZADOR", c.getId());
                if (bloqueo != null) {
                    b.retenida(true);
                    b.motivoBloqueo("Retenida por " + bloqueo.getMarca() + " (hallazgo #" + bloqueo.getMarcaId() + ")");
                } else {
                    b.retenida(false);
                }
            } else {
                b.rut(tramo.getRut())
                 .nombre(tramo.getActor())
                 .cantidadKg(tramo.getCantidad())
                 .fechaDespacho(tramo.getFechaDeclaracion())
                 .horaDespacho(tramo.getHora())
                 .fechaTraslado(tramo.getFechaTraslado())
                 .geo(ReferenciaOrigenService.construirGeoDeclaracion("COMERCIALIZADOR", null, null, null, null, false, null, umbralKm, precisionMaxM));
            }
        } else {
            // Carga aún sin despachar en bodega virtual
            b.rut(tramo.getRut())
             .nombre(tramo.getActor())
             .cantidadKg(tramo.getCantidad())
             .notaRecepcion("Carga en bodega virtual; aún sin declaración de despacho")
             .geo(ReferenciaOrigenService.construirGeoDeclaracion("COMERCIALIZADOR", null, null, null, null, false, null, umbralKm, precisionMaxM));
        }

        FichaComercializadorDTO built = b.build();
        built.setEstadoHumedadDeclarado(estadoHumedadDecl);

        // TH.4 / TH.5: Estado esperado al despacho (informativo, sin crear hallazgo)
        if (built.isDespachado()) {
            Date fEvento = built.getFechaTraslado() != null ? built.getFechaTraslado() : built.getFechaDespacho();
            LocalDateTime mDespacho = ReportService.parseTimestamp(fEvento, built.getHoraDespacho());
            com.trazalga.api.services.indicadores.CoherenciaHumedad.ResultadoCoherencia resCoh =
                    com.trazalga.api.services.indicadores.CoherenciaHumedad.evaluar(
                            estadoHumedadDecl, fechaExtraccionOrigen, mDespacho, getParametrosCoherencia());
            built.setEdadExtraccionAlDespachoHoras(resCoh.getHMin());
            built.setEstadoEsperadoAlDespacho(resCoh.getTramoMinimo());
            built.setResultadoCoherenciaAlDespacho(resCoh.getResultado());
        }

        return built;
    }

    // =========================================================================
    // CONSTRUCCIÓN DE PLANTA
    // =========================================================================

    private FichaPlantaDTO construirPlantaDTO(
            Long plantaId,
            BigDecimal totalKgOrigen,
            BigDecimal totalCapturaOrigen,
            Date fechaExtraccionOrigen) {
        double umbralKm = getUmbralGeoKm();
        double precisionMaxM = getPrecisionMaxM();

        if (plantaId == null) {
            return FichaPlantaDTO.builder()
                    .recepcionada(false)
                    .mensajeEstado("Aún no recepcionada en planta")
                    .build();
        }

        Optional<DeclaracionPlantaAbastecimientoModel> opt = plantaRepository != null
                ? plantaRepository.findById(plantaId)
                : Optional.empty();

        if (opt.isEmpty()) {
            return FichaPlantaDTO.builder()
                    .id(plantaId)
                    .recepcionada(true)
                    .mensajeEstado("Recepcionada en planta")
                    .rotuloVariacion("Variación de la recepción completa")
                    .geo(ReferenciaOrigenService.construirGeoDeclaracion("PLANTA_ABASTECIMIENTO", null, null, null, null, false, null, umbralKm, precisionMaxM))
                    .build();
        }

        DeclaracionPlantaAbastecimientoModel p = opt.get();
        GeoDeclaracionDTO geo = ReferenciaOrigenService.construirGeoDeclaracion(
                "PLANTA_ABASTECIMIENTO", p.getLatitud(), p.getLongitud(), null, null, false, null, umbralKm, precisionMaxM);

        boolean conRomana = p.getPesoRomanaKg() != null && p.getPesoRomanaKg().compareTo(BigDecimal.ZERO) > 0;
        double pesoPlanta = conRomana
                ? p.getPesoRomanaKg().doubleValue()
                : (p.getCantidad() != null ? p.getCantidad().doubleValue() : 0.0);

        Double variacionPct = null;
        Double variacionEqPct = null;
        BigDecimal capturaPlantaTotal = null;
        BigDecimal capturaOrigenTotal = null;
        String rotuloVariacion = "Variación respecto del despacho del comercializador (recepción completa)";

        if (conRomana && variacionPesoService != null) {
            com.trazalga.api.dto.ResultadoVariacionPesoDTO res = variacionPesoService.evaluarRecepcion(p);
            if (res != null) {
                variacionPct = res.getDeltaPct();
                variacionEqPct = res.getDeltaEqPct();
                if (res.getCapturaDespachoEq() != null) {
                    capturaOrigenTotal = BigDecimal.valueOf(res.getCapturaDespachoEq()).setScale(2, java.math.RoundingMode.HALF_UP);
                }
                if (res.getCapturaRecepcionEq() != null) {
                    capturaPlantaTotal = BigDecimal.valueOf(res.getCapturaRecepcionEq()).setScale(2, java.math.RoundingMode.HALF_UP);
                }
            } else if (totalKgOrigen != null && totalKgOrigen.compareTo(BigDecimal.ZERO) > 0) {
                double pesoOrigen = totalKgOrigen.doubleValue();
                variacionPct = Math.round(((pesoPlanta - pesoOrigen) / pesoOrigen * 100.0) * 10.0) / 10.0;
            }
        } else if (conRomana && totalKgOrigen != null && totalKgOrigen.compareTo(BigDecimal.ZERO) > 0) {
            double pesoOrigen = totalKgOrigen.doubleValue();
            variacionPct = Math.round(((pesoPlanta - pesoOrigen) / pesoOrigen * 100.0) * 10.0) / 10.0;
        }

        // T3.3: Ambos estados de humedad y factor de recepción
        String humOrigen = p.getHumedadEstado() != null ? p.getHumedadEstado().getNombre() : null;
        String humRecepcion = p.getHumedadEstadoRecepcion() != null
                ? p.getHumedadEstadoRecepcion().getNombre()
                : humOrigen;

        Long humIdRecepcion = p.getHumedadEstadoRecepcion() != null
                ? p.getHumedadEstadoRecepcion().getId()
                : (p.getHumedadEstado() != null ? p.getHumedadEstado().getId() : null);

        Long espId = p.getEspecie() != null ? p.getEspecie().getId() : null;

        BigDecimal factor = null;
        if (factorConversionService != null && espId != null && humIdRecepcion != null) {
            try {
                factor = factorConversionService.findFactorVigente(espId, humIdRecepcion, p.getFechaIngresoPlanta())
                        .map(FactorConversionModel::getFactor)
                        .orElse(null);
            } catch (Exception e) {
                factor = null;
            }
        }

        double factorD = factor != null ? factor.doubleValue() : 1.0;
        double capturaPlanta = pesoPlanta * factorD;
        if (capturaPlantaTotal == null) {
            capturaPlantaTotal = BigDecimal.valueOf(capturaPlanta).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (capturaOrigenTotal == null && totalCapturaOrigen != null && totalCapturaOrigen.compareTo(BigDecimal.ZERO) > 0) {
            capturaOrigenTotal = totalCapturaOrigen.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (variacionEqPct == null && totalCapturaOrigen != null && totalCapturaOrigen.compareTo(BigDecimal.ZERO) > 0) {
            double capOrig = totalCapturaOrigen.doubleValue();
            variacionEqPct = Math.round(((capturaPlanta - capOrig) / capOrig * 100.0) * 10.0) / 10.0;
        }

        FichaPlantaDTO.FichaPlantaDTOBuilder b = FichaPlantaDTO.builder()
                .id(p.getId())
                .recepcionada(true)
                .mensajeEstado("Recepcionada en planta")
                .nombrePlanta(p.getNombrePlanta())
                .codigoSernapesca(p.getCodigoSernapesca())
                .comuna(p.getUsuario() != null && p.getUsuario().getComuna() != null ? p.getUsuario().getComuna().getNombre() : null)
                .region(p.getUsuario() != null && p.getUsuario().getComuna() != null && p.getUsuario().getComuna().getRegion() != null ? p.getUsuario().getComuna().getRegion().getNombre() : null)
                .fechaLlegada(p.getFechaIngresoPlanta())
                .hora(p.getHora())
                .fechaTraslado(p.getFechaTraslado())
                .conRomana(conRomana)
                .pesoRomanaKg(p.getPesoRomanaKg())
                .numeroVoucherRomana(p.getVoucherRomanaNumero())
                .fechaPesajeRomana(p.getFechaPesaje())
                .cantidadDeclarada(p.getCantidad())
                .rotuloPesaje(conRomana ? "Pesaje en romana" : "Sin pesaje en romana")
                .humedadEstadoOrigen(humOrigen)
                .humedadEstadoRecepcion(humRecepcion)
                .humedadHigrometro(p.getHumedadHigrometro())
                .variacionPct(variacionPct)
                .variacionEqPct(variacionEqPct)
                .capturaOrigenTotal(capturaOrigenTotal)
                .capturaPlantaTotal(capturaPlantaTotal)
                .factorConversionRecepcion(factor)
                .rotuloVariacion(rotuloVariacion)
                .docOrigenTipo(p.getDocumentoTributarioOrigenTipo())
                .docOrigenNumero(p.getDocumentoTributarioOrigenNumero())
                .docOrigenFecha(p.getDocumentoTributarioOrigenFecha())
                .docDestinoTipo(p.getDocumentoTributarioDestinoTipo())
                .docDestinoNumero(p.getDocumentoTributarioDestinoNumero())
                .docDestinoFecha(p.getDocumentoTributarioDestinoFecha())
                .docNumero(p.getDocumentoTributarioNumero())
                .docTipo(p.getDocumentoTributarioTipo())
                .docFecha(p.getDocumentoTributarioFecha())
                .patenteCamion(p.getPlacaPatente() != null && !p.getPlacaPatente().isBlank() ? p.getPlacaPatente() : p.getPatente())
                .patenteCarro(p.getPlacaPatenteCarro())
                .folioOrigen(p.getFolioOrigen())
                .folioDeclaracionAPla(p.getFolioDeclaracionAPla())
                .estado(p.getEstado() != null ? p.getEstado() : "ENVIADA")
                .geo(geo);

        // Marcas y retención de planta
        b.marcas(obtenerMarcasDTO("PLANTA_ABASTECIMIENTO", p.getId()));
        MotivoBloqueoDTO bloqueo = obtenerBloqueo("PLANTA_ABASTECIMIENTO", p.getId());
        if (bloqueo != null) {
            b.retenida(true);
            b.motivoBloqueo("Retenida por " + bloqueo.getMarca() + " (hallazgo #" + bloqueo.getMarcaId() + ")");
        } else {
            b.retenida(false);
        }

        FichaPlantaDTO builtPlanta = b.build();

        // TH.3 / TH.5: Coherencia de humedad en recepción en planta
        LocalDateTime momentoPlanta = ReportService.parseTimestamp(p.getFechaIngresoPlanta(), p.getHora());
        com.trazalga.api.services.indicadores.CoherenciaHumedad.ResultadoCoherencia resCohPlanta =
                com.trazalga.api.services.indicadores.CoherenciaHumedad.evaluar(
                        humRecepcion, fechaExtraccionOrigen, momentoPlanta, getParametrosCoherencia());
        builtPlanta.setEdadExtraccionEnPlantaHoras(resCohPlanta.getHMin());
        builtPlanta.setEstadoEsperadoEnPlanta(resCohPlanta.getTramoMinimo());
        builtPlanta.setResultadoCoherenciaEnPlanta(resCohPlanta.getResultado());

        return builtPlanta;
    }

    // =========================================================================
    // RESUMEN SUPERIOR DE ALERTAS
    // =========================================================================

    private FichaAlertasDTO construirAlertasDTO(
            List<FichaOrigenDTO> origenes,
            List<FichaComercializadorDTO> coms,
            FichaPlantaDTO planta) {

        List<FichaMarcaDTO> marcasActivas = new ArrayList<>();
        List<String> motivosRetencion = new ArrayList<>();
        boolean hayRetenciones = false;

        // Recolectar de orígenes
        for (FichaOrigenDTO o : origenes) {
            for (FichaMarcaDTO m : o.getMarcas()) {
                if (!Boolean.TRUE.equals(m.getResuelta())) {
                    marcasActivas.add(m);
                }
            }
            if (o.isRetenida() && o.getMotivoBloqueo() != null) {
                hayRetenciones = true;
                motivosRetencion.add(o.getTipo() + " #" + o.getId() + ": " + o.getMotivoBloqueo());
            }
        }

        // Recolectar de comercializadores
        for (FichaComercializadorDTO c : coms) {
            for (FichaMarcaDTO m : c.getMarcas()) {
                if (!Boolean.TRUE.equals(m.getResuelta())) {
                    marcasActivas.add(m);
                }
            }
            if (c.isRetenida() && c.getMotivoBloqueo() != null) {
                hayRetenciones = true;
                motivosRetencion.add("Comercializador #" + c.getId() + ": " + c.getMotivoBloqueo());
            }
        }

        // Recolectar de planta
        if (planta != null && planta.isRecepcionada()) {
            for (FichaMarcaDTO m : planta.getMarcas()) {
                if (!Boolean.TRUE.equals(m.getResuelta())) {
                    marcasActivas.add(m);
                }
            }
            if (planta.isRetenida() && planta.getMotivoBloqueo() != null) {
                hayRetenciones = true;
                motivosRetencion.add("Planta #" + planta.getId() + ": " + planta.getMotivoBloqueo());
            }
        }

        return FichaAlertasDTO.builder()
                .totalMarcasActivas(marcasActivas.size())
                .hayCargasRetenidas(hayRetenciones)
                .marcasActivas(marcasActivas)
                .motivosRetencion(motivosRetencion)
                .build();
    }

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    private List<FichaMarcaDTO> obtenerMarcasDTO(String tipo, Long declaracionId) {
        if (declaracionMarcaService == null || tipo == null || declaracionId == null) {
            return Collections.emptyList();
        }
        try {
            List<DeclaracionMarcaModel> marcas = declaracionMarcaService.getByDeclaracion(tipo, declaracionId);
            List<FichaMarcaDTO> out = new ArrayList<>();
            for (DeclaracionMarcaModel m : marcas) {
                out.add(FichaMarcaDTO.builder()
                        .id(m.getId())
                        .declaracionTipo(m.getDeclaracionTipo())
                        .declaracionId(m.getDeclaracionId())
                        .marca(m.getMarca())
                        .detalle(m.getDetalle())
                        .fechaDeteccion(m.getCreatedAt())
                        .resuelta(m.getResuelta())
                        .estadoGestion(m.getEstadoGestion())
                        .resolucionTipo(m.getResolucionTipo())
                        .observacionResolucion(m.getObservacionResolucion())
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("Error al obtener marcas para {} #{}: {}", tipo, declaracionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private MotivoBloqueoDTO obtenerBloqueo(String tipo, Long declaracionId) {
        if (bloqueoCargaService == null || tipo == null || declaracionId == null) {
            return null;
        }
        try {
            Map<Long, MotivoBloqueoDTO> map = bloqueoCargaService.bloqueosPorTipoEIds(tipo, Collections.singletonList(declaracionId));
            return map.get(declaracionId);
        } catch (Exception e) {
            log.warn("Error al consultar bloqueos para {} #{}: {}", tipo, declaracionId, e.getMessage());
            return null;
        }
    }

    private String normalizarTipo(String tipo) {
        if (tipo == null) return "DESCONOCIDO";
        String t = tipo.trim().toUpperCase();
        if (t.startsWith("REC") || t.equals("1")) return "RECOLECTOR";
        if (t.startsWith("ARM") || t.equals("2")) return "ARMADOR";
        if (t.startsWith("ARE") || t.startsWith("AME") || t.equals("3")) return "AREA";
        if (t.startsWith("COM") || t.equals("4")) return "COMERCIALIZADOR";
        if (t.startsWith("PLA") || t.equals("5")) return "PLANTA_ABASTECIMIENTO";
        return t;
    }

    private String formatNombre(UsuarioModel u) {
        if (u == null) return "";
        StringBuilder sb = new StringBuilder();
        if (u.getNombres() != null) sb.append(u.getNombres().trim());
        if (u.getApellidop() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(u.getApellidop().trim());
        }
        if (u.getApellidom() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(u.getApellidom().trim());
        }
        return sb.toString();
    }

    private Date toDate(LocalDateTime ldt) {
        if (ldt == null) return null;
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private String formatHora(LocalDateTime ldt) {
        if (ldt == null) return null;
        return String.format("%02d:%02d:00", ldt.getHour(), ldt.getMinute());
    }
}
