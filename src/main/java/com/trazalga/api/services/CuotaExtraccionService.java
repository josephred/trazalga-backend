package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.dto.CuotaListadoDTO;
import com.trazalga.api.dto.CuotaLoteDTO;
import com.trazalga.api.dto.ResumenPlantillaDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.MacrozonaModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.cuotas.AlcanceCuota;
import com.trazalga.api.services.cuotas.ReglaImputacionTerritorial;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IMacrozonaRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Service
@Slf4j
@Transactional(readOnly = true)
public class CuotaExtraccionService {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IRegionRepository regionRepository;

    @Autowired
    private IMacrozonaRepository macrozonaRepository;

    @Autowired
    private MacrozonaService macrozonaService;

    @Autowired
    private IProvinciaRepository provinciaRepository;

    @Autowired
    private IComunaRepository comunaRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;

    @Autowired
    private IAmerbRepository amerbRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired
    private AmerbEspecieHabilitadaService amerbEspecieHabilitadaService;

    @Autowired
    private com.trazalga.api.services.cuotas.CierreCuotaService cierreCuotaService;

    @Autowired
    private FactorConversionService factorConversionService;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private com.trazalga.api.repositories.ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Autowired
    private com.trazalga.api.repositories.IDeclaracionMarcaRepository declaracionMarcaRepository;

    @Autowired
    private com.trazalga.api.repositories.IAvisoEnviadoRepository avisoEnviadoRepository;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private com.trazalga.api.services.cuotas.AlcanceCuotaRegistry alcanceCuotaRegistry;

    @Autowired(required = false)
    private com.trazalga.api.services.cuotas.ConsumoIndividualQuery consumoIndividualQuery;

    @Autowired(required = false)
    private com.trazalga.api.services.cuotas.ResolutorImputacionTerritorial resolutorImputacion;

    public com.trazalga.api.services.cuotas.AlcanceCuotaRegistry getAlcanceRegistry() {
        if (alcanceCuotaRegistry != null) {
            return alcanceCuotaRegistry;
        }
        return com.trazalga.api.services.cuotas.AlcanceCuotaRegistry.crearPorDefecto(
                comunaRepository, provinciaRepository, usuarioRepository);
    }

    public com.trazalga.api.services.cuotas.ResolutorImputacionTerritorial getResolutorImputacion() {
        if (resolutorImputacion != null) {
            return resolutorImputacion;
        }
        return new com.trazalga.api.services.cuotas.ResolutorImputacionTerritorial(
                new com.trazalga.api.services.cuotas.ReglaImputacionInscripcion(),
                new com.trazalga.api.services.cuotas.ReglaImputacionCaletaDesembarque(),
                null);
    }

    public List<CuotaExtraccionModel> getAll() {
        return cuotaRepository.findAll();
    }

    public List<com.trazalga.api.models.CuotaExtraccionEventoModel> getEventos(Long cuotaId) {
        if (cuotaEventoRepository == null || cuotaId == null) {
            return java.util.Collections.emptyList();
        }
        return cuotaEventoRepository.findByCuotaIdOrderByCreatedAtDesc(cuotaId);
    }

    @Transactional
    public CuotaExtraccionModel cerrarCuota(Long id, String motivo, String observacion) {
        return cierreCuotaService.cerrar(id, motivo, observacion, null);
    }

    @Transactional
    public CuotaExtraccionModel cerrarCuota(Long id) {
        return cerrarCuota(id, "ADMINISTRATIVO", "Cierre administrativo");
    }

    @Transactional
    public CuotaExtraccionModel reabrirCuota(Long id, String motivo) {
        return cierreCuotaService.reabrir(id, motivo, null);
    }

    public Optional<CuotaExtraccionModel> getById(Long id) {
        return cuotaRepository.findById(id);
    }

    public String getModoImputacion() {
        if (configuracionGeneralService == null) {
            return "EXTRACCION";
        }
        String modo = configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION");
        return (modo != null && "DECLARACION".equalsIgnoreCase(modo.trim())) ? "DECLARACION" : "EXTRACCION";
    }

    @Transactional
    public CuotaExtraccionModel save(com.trazalga.api.dto.CuotaRequestDTO dto) {
        CuotaExtraccionModel cuota = mapearDesdeDTO(dto, new CuotaExtraccionModel());
        validarDatosBasicos(cuota);
        validarSolapamiento(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        CuotaExtraccionModel guardada = cuotaRepository.save(cuota);

        // Registrar evento CREADA (TC.2)
        if (cuotaEventoRepository != null) {
            String detalle = String.format("Cuota creada: especie=%s, limite=%.2f kg, periodo=%s, nivel=%s",
                    guardada.getEspecie() != null ? guardada.getEspecie().getNombre() : "N/A",
                    guardada.getLimiteKg(), guardada.getPeriodo(), guardada.getNivelAgregacion());
            com.trazalga.api.models.CuotaExtraccionEventoModel ev = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                    .cuota(guardada)
                    .tipo("CREADA")
                    .detalle(detalle)
                    .usuarioId(dto.getUsuarioId())
                    .createdAt(new Date())
                    .build();
            cuotaEventoRepository.save(ev);
        }
        return guardada;
    }

    @Transactional
    public CuotaExtraccionModel save(CuotaExtraccionModel cuota) {
        resolverReferencias(cuota);
        validarDatosBasicos(cuota);
        validarSolapamiento(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        CuotaExtraccionModel guardada = cuotaRepository.save(cuota);

        if (cuotaEventoRepository != null) {
            String detalle = String.format("Cuota creada: especie=%s, limite=%.2f kg, periodo=%s, nivel=%s",
                    guardada.getEspecie() != null ? guardada.getEspecie().getNombre() : "N/A",
                    guardada.getLimiteKg(), guardada.getPeriodo(), guardada.getNivelAgregacion());
            com.trazalga.api.models.CuotaExtraccionEventoModel ev = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                    .cuota(guardada)
                    .tipo("CREADA")
                    .detalle(detalle)
                    .usuarioId(guardada.getUsuario() != null ? guardada.getUsuario().getId() : null)
                    .createdAt(new Date())
                    .build();
            cuotaEventoRepository.save(ev);
        }
        return guardada;
    }

    /**
     * T1.8: Alta transaccional de cuotas por lote.
     * Si cualquier cuota no supera las validaciones o solapa con cuotas existentes,
     * no se guarda ninguna y se indica el periodo que causó el conflicto.
     */
    @Transactional(rollbackFor = Exception.class)
    public List<CuotaExtraccionModel> saveLote(List<CuotaExtraccionModel> cuotas) {
        if (cuotas == null || cuotas.isEmpty()) {
            throw new IllegalArgumentException("La lista de cuotas a guardar no puede estar vacía.");
        }

        // 1. Validar solapamiento interno en el lote
        validarSolapamientoInternoLote(cuotas);

        // 2. Validar cada cuota contra la base de datos
        for (CuotaExtraccionModel c : cuotas) {
            try {
                resolverReferencias(c);
                validarDatosBasicos(c);
                validarSolapamiento(c);
                validarJerarquia(c);
            } catch (IllegalArgumentException ex) {
                String mesNombre = "";
                if (c.getFechaInicio() != null) {
                    mesNombre = toLocalDateSafe(c.getFechaInicio()).getMonth()
                            .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
                    mesNombre = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1);
                }
                String contexto = !mesNombre.isEmpty() ? ("mes " + mesNombre) : ("cuota " + c.getPeriodo());
                throw new IllegalArgumentException("Error en " + contexto + ": " + ex.getMessage(), ex);
            }
        }

        // 3. Persistir todas las cuotas atómicamente
        List<CuotaExtraccionModel> guardadas = cuotaRepository.saveAll(cuotas);
        invalidarCacheConsumo();
        return guardadas;
    }

    /**
     * T1.8: Asistente de Alta Anual (genera cuotas mensuales consecutivas).
     */
    @Transactional(rollbackFor = Exception.class)
    public List<CuotaExtraccionModel> saveLote(CuotaLoteDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud no puede estar vacío.");
        }
        if (dto.getAnio() == null || dto.getAnio() < 2000 || dto.getAnio() > 2100) {
            throw new IllegalArgumentException("El año de vigencia es obligatorio y debe ser válido.");
        }
        if (dto.getEspecieId() == null) {
            throw new IllegalArgumentException("La especie objetivo es obligatoria.");
        }
        if (dto.getExtraccionTipoId() == null) {
            throw new IllegalArgumentException("El método de extracción es obligatorio.");
        }
        String nivel = dto.getNivelAgregacion() != null ? dto.getNivelAgregacion().trim().toUpperCase() : "COMUNA";
        if ("COMUNA".equals(nivel) && (dto.getComunaIds() == null || dto.getComunaIds().isEmpty())) {
            throw new IllegalArgumentException("Debe seleccionar al menos una comuna para el nivel Comunal.");
        }
        if ("PROVINCIA".equals(nivel) && dto.getProvinciaId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una provincia para el nivel Provincial.");
        }
        if ("REGION".equals(nivel) && dto.getRegionId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una región para el nivel Regional.");
        }
        if ("INDIVIDUAL".equals(nivel) && !Boolean.TRUE.equals(dto.getEsPlantilla()) && dto.getUsuarioId() == null) {
            throw new IllegalArgumentException("Debe seleccionar un usuario o indicar que es plantilla para el nivel Individual.");
        }
        if (dto.getMeses() == null || dto.getMeses().isEmpty()) {
            throw new IllegalArgumentException("Debe incluir la grilla de meses.");
        }

        EspecieModel especie = especieRepository.findById(dto.getEspecieId())
                .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada con ID: " + dto.getEspecieId()));
        ExtraccionTipoModel metodo = extraccionTipoRepository.findById(dto.getExtraccionTipoId())
                .orElseThrow(() -> new IllegalArgumentException("Método no encontrado con ID: " + dto.getExtraccionTipoId()));
        RegionModel region = dto.getRegionId() != null ? regionRepository.findById(dto.getRegionId()).orElse(null) : null;
        ProvinciaModel provincia = dto.getProvinciaId() != null ? provinciaRepository.findById(dto.getProvinciaId()).orElse(null) : null;
        if (provincia != null && region == null && provincia.getRegion() != null) {
            region = provincia.getRegion();
        }
        UsuarioModel usuarioLote = dto.getUsuarioId() != null ? usuarioRepository.findById(dto.getUsuarioId()).orElse(null) : null;
        HumedadEstadoModel humedad = dto.getHumedadEstadoId() != null
                ? humedadEstadoRepository.findById(dto.getHumedadEstadoId()).orElse(null)
                : null;

        Set<ComunaModel> comunas = new LinkedHashSet<>();
        ComunaModel primeraComuna = null;
        if ("COMUNA".equals(nivel) && dto.getComunaIds() != null) {
            for (Long cId : dto.getComunaIds()) {
                if (cId != null) {
                    ComunaModel cm = comunaRepository.findById(cId)
                            .orElseThrow(() -> new IllegalArgumentException("Comuna no encontrada con ID: " + cId));
                    comunas.add(cm);
                    if (primeraComuna == null) {
                        primeraComuna = cm;
                    }
                    if (region == null && cm.getRegion() != null) {
                        region = cm.getRegion();
                    }
                }
            }
        }

        String metrica = dto.getMetrica() != null ? dto.getMetrica() : "CAPTURA";
        if ("DESEMBARQUE".equalsIgnoreCase(metrica) && (dto.getResolucion() == null || dto.getResolucion().trim().isEmpty())) {
            throw new IllegalArgumentException("La métrica DESEMBARQUE exige número de resolución técnica.");
        }

        List<CuotaExtraccionModel> cuotas = new ArrayList<>();
        for (CuotaLoteDTO.CuotaMesItemDTO item : dto.getMeses()) {
            if (item == null || item.getMes() == null) continue;
            int m = item.getMes();
            if (m < 1 || m > 12) {
                throw new IllegalArgumentException("Número de mes inválido: " + m);
            }
            if (item.getLimiteKg() == null || item.getLimiteKg() <= 0) {
                throw new IllegalArgumentException(String.format("El límite para el mes %d debe ser mayor que cero.", m));
            }

            LocalDate ini = LocalDate.of(dto.getAnio(), m, 1);
            LocalDate fin = ini.withDayOfMonth(ini.lengthOfMonth());

            CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                    .ambito(dto.getAmbito() != null ? dto.getAmbito() : "AREA_LIBRE")
                    .nivelAgregacion(nivel)
                    .perfil("RECOLECTOR")
                    .region(region)
                    .provincia(provincia)
                    .comuna(primeraComuna)
                    .comunas(new LinkedHashSet<>(comunas))
                    .usuario(usuarioLote)
                    .esPlantilla(Boolean.TRUE.equals(dto.getEsPlantilla()))
                    .especie(especie)
                    .extraccionTipo(metodo)
                    .humedadEstado(humedad)
                    .periodo("MENSUAL")
                    .fechaInicio(java.sql.Date.valueOf(ini))
                    .fechaFin(java.sql.Date.valueOf(fin))
                    .limiteKg(item.getLimiteKg())
                    .metrica(metrica)
                    .modoAccion(dto.getModoAccion() != null ? dto.getModoAccion() : "SOLO_ALERTA")
                    .resolucion(dto.getResolucion() != null ? dto.getResolucion().trim() : null)
                    .estado("ABIERTA")
                    .activo(item.getActivo() != null ? item.getActivo() : true)
                    .build();

            cuotas.add(cuota);
        }

        return saveLote(cuotas);
    }

    private void validarSolapamientoInternoLote(List<CuotaExtraccionModel> cuotas) {
        for (int i = 0; i < cuotas.size(); i++) {
            CuotaExtraccionModel a = cuotas.get(i);
            if (!Boolean.TRUE.equals(a.getActivo())) continue;
            for (int j = i + 1; j < cuotas.size(); j++) {
                CuotaExtraccionModel b = cuotas.get(j);
                if (!Boolean.TRUE.equals(b.getActivo())) continue;

                String ambitoA = a.getAmbito() != null ? a.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
                String ambitoB = b.getAmbito() != null ? b.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
                if (!ambitoA.equals(ambitoB)) continue;

                String nivelA = a.getNivelAgregacion() != null ? a.getNivelAgregacion().trim().toUpperCase() : "";
                String nivelB = b.getNivelAgregacion() != null ? b.getNivelAgregacion().trim().toUpperCase() : "";
                if (!nivelA.equals(nivelB)) continue;

                if (!seSolapan(a, b)) continue;
                if (!especiesComparables(a, b)) continue;

                AlcanceCuota estrategia = getAlcanceRegistry().resolver(a);
                if (estrategia != null && estrategia.mismoAlcance(a, b)) {
                    throw new IllegalArgumentException(String.format(
                        "Conflicto interno en el lote: las cuotas para %s presentan fechas solapadas.",
                        estrategia.describir(a)
                    ));
                }
            }
        }
    }

    @Transactional
    public CuotaExtraccionModel update(Long id, com.trazalga.api.dto.CuotaRequestDTO dto) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada con ID: " + id));

        Double limitePrev = cuota.getLimiteKg();
        Date inicioPrev = cuota.getFechaInicio();
        Date finPrev = cuota.getFechaFin();

        // Mapear campos editables sin tocar estado ni fechas de cierre (TC.2)
        mapearDesdeDTO(dto, cuota);

        validarDatosBasicos(cuota);
        validarSolapamiento(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        CuotaExtraccionModel guardada = cuotaRepository.save(cuota);

        // Registrar evento EDITADA (TC.2)
        if (cuotaEventoRepository != null) {
            String detalle = String.format("{\"limite_anterior\": %s, \"limite_nuevo\": %s, \"inicio_anterior\": \"%s\", \"inicio_nuevo\": \"%s\", \"fin_anterior\": \"%s\", \"fin_nuevo\": \"%s\"}",
                    limitePrev, guardada.getLimiteKg(),
                    inicioPrev != null ? inicioPrev : "", guardada.getFechaInicio() != null ? guardada.getFechaInicio() : "",
                    finPrev != null ? finPrev : "", guardada.getFechaFin() != null ? guardada.getFechaFin() : "");
            com.trazalga.api.models.CuotaExtraccionEventoModel ev = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                    .cuota(guardada)
                    .tipo("EDITADA")
                    .detalle(detalle)
                    .usuarioId(dto.getUsuarioId())
                    .createdAt(new Date())
                    .build();
            cuotaEventoRepository.save(ev);
        }

        return guardada;
    }

    @Transactional
    public CuotaExtraccionModel update(Long id, CuotaExtraccionModel request) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada con ID: " + id));

        Double limitePrev = cuota.getLimiteKg();

        resolverReferencias(request);
        cuota.setPerfil(request.getPerfil());
        if (request.getAmbito() != null) cuota.setAmbito(request.getAmbito());
        cuota.setEspecie(request.getEspecie());
        cuota.setRegion(request.getRegion());
        cuota.setMacrozona(request.getMacrozona());
        cuota.setProvincia(request.getProvincia());
        cuota.setComuna(request.getComuna());
        if (request.getComunas() != null) {
            cuota.getComunas().clear();
            cuota.getComunas().addAll(request.getComunas());
        }
        cuota.setUsuario(request.getUsuario());
        cuota.setAmerb(request.getAmerb());
        cuota.setExtraccionTipo(request.getExtraccionTipo());
        cuota.setHumedadEstado(request.getHumedadEstado());
        if (request.getNivelAgregacion() != null) cuota.setNivelAgregacion(request.getNivelAgregacion());
        if (request.getMetrica() != null) cuota.setMetrica(request.getMetrica());
        if (request.getEsPlantilla() != null) cuota.setEsPlantilla(request.getEsPlantilla());
        if (request.getModoAccion() != null) cuota.setModoAccion(request.getModoAccion());
        cuota.setPeriodo(request.getPeriodo());
        cuota.setLimiteKg(request.getLimiteKg());
        cuota.setFechaInicio(request.getFechaInicio());
        cuota.setFechaFin(request.getFechaFin());
        cuota.setResolucion(request.getResolucion());
        // Invariante TC.2: No sobreescribir estado ni fechaCierre desde request
        if (request.getActivo() != null) cuota.setActivo(request.getActivo());

        validarDatosBasicos(cuota);
        validarSolapamiento(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        CuotaExtraccionModel guardada = cuotaRepository.save(cuota);

        if (cuotaEventoRepository != null) {
            String detalle = String.format("{\"limite_anterior\": %s, \"limite_nuevo\": %s}", limitePrev, guardada.getLimiteKg());
            com.trazalga.api.models.CuotaExtraccionEventoModel ev = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                    .cuota(guardada)
                    .tipo("EDITADA")
                    .detalle(detalle)
                    .createdAt(new Date())
                    .build();
            cuotaEventoRepository.save(ev);
        }

        return guardada;
    }

    @Transactional
    public boolean delete(Long id) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id).orElse(null);
        if (cuota == null) {
            return false;
        }

        long mMarcas = declaracionMarcaRepository != null ? declaracionMarcaRepository.countByReglaId(id) : 0L;
        long nDeclaraciones = contarDeclaracionesAsociadas(cuota);

        if (nDeclaraciones > 0 || mMarcas > 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    String.format("Tiene %d declaraciones y %d hallazgos asociados: desactívela.", nDeclaraciones, mMarcas)
            );
        }

        cuotaRepository.deleteById(id);
        invalidarCacheConsumo();
        return true;
    }

    public long contarDeclaracionesAsociadas(CuotaExtraccionModel cuota) {
        if (cuota == null || cuota.getId() == null) return 0L;
        String ambito = cuota.getAmbito() != null ? cuota.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        java.sql.Date[] rango = calcularRangoFechas(cuota, new Date());
        long total = 0L;

        if ("AMERB".equals(ambito)) {
            FiltroTerritorialCuota f = construirFiltroTerritorial(cuota, "declaracion_area", null);
            total += contarEnTabla("declaracion_area", f, cuota, rango);
        } else {
            FiltroTerritorialCuota fRec = construirFiltroTerritorial(cuota, "declaracion_recolector", null);
            total += contarEnTabla("declaracion_recolector", fRec, cuota, rango);

            FiltroTerritorialCuota fArm = construirFiltroTerritorial(cuota, "declaracion_armador", null);
            total += contarEnTabla("declaracion_armador", fArm, cuota, rango);
        }
        return total;
    }

    private long contarEnTabla(String tableName, FiltroTerritorialCuota filtro, CuotaExtraccionModel cuota, java.sql.Date[] rango) {
        try {
            String modo = getModoImputacion();
            String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

            StringBuilder sql = new StringBuilder("SELECT COUNT(d.id) FROM " + tableName + " d ");
            sql.append(filtro.getSqlFragment());
            if (cuota.getEspecie() != null) {
                sql.append("AND d.especie_id = :especieId ");
            }
            if (cuota.getExtraccionTipo() != null) {
                sql.append("AND d.extraccion_tipo_id = :extraccionTipoId ");
            }
            sql.append("AND d.").append(colFecha).append(" BETWEEN :startDate AND :endDate");

            Query q = entityManager.createNativeQuery(sql.toString());
            q.setParameter("startDate", rango[0]);
            q.setParameter("endDate", rango[1]);
            if (cuota.getEspecie() != null) q.setParameter("especieId", cuota.getEspecie().getId());
            if (cuota.getExtraccionTipo() != null) q.setParameter("extraccionTipoId", cuota.getExtraccionTipo().getId());
            for (Map.Entry<String, Object> entry : filtro.getParametros().entrySet()) {
                q.setParameter(entry.getKey(), entry.getValue());
            }

            Object result = q.getSingleResult();
            if (result instanceof Number n) {
                return n.longValue();
            }
        } catch (Exception e) {
            log.warn("Error contando declaraciones asociadas a cuota ID {}: {}", cuota.getId(), e.getMessage());
        }
        return 0L;
    }

    private CuotaExtraccionModel mapearDesdeDTO(com.trazalga.api.dto.CuotaRequestDTO dto, CuotaExtraccionModel cuota) {
        if (dto.getPerfil() != null) cuota.setPerfil(dto.getPerfil());
        if (dto.getAmbito() != null) cuota.setAmbito(dto.getAmbito());

        if (dto.getEspecieId() != null) {
            cuota.setEspecie(especieRepository.findById(dto.getEspecieId())
                    .orElseThrow(() -> new IllegalArgumentException("La especie indicada no existe.")));
        }
        if (dto.getExtraccionTipoId() != null) {
            cuota.setExtraccionTipo(extraccionTipoRepository.findById(dto.getExtraccionTipoId())
                    .orElseThrow(() -> new IllegalArgumentException("El método de extracción indicado no existe.")));
        }
        if (dto.getRegionId() != null) {
            cuota.setRegion(regionRepository.findById(dto.getRegionId())
                    .orElseThrow(() -> new IllegalArgumentException("La región indicada no existe.")));
        } else {
            cuota.setRegion(null);
        }
        if (dto.getMacrozonaId() != null) {
            cuota.setMacrozona(macrozonaRepository.findById(dto.getMacrozonaId())
                    .orElseThrow(() -> new IllegalArgumentException("La macrozona indicada no existe.")));
        } else {
            cuota.setMacrozona(null);
        }
        if (dto.getProvinciaId() != null) {
            cuota.setProvincia(provinciaRepository.findById(dto.getProvinciaId())
                    .orElseThrow(() -> new IllegalArgumentException("La provincia indicada no existe.")));
        } else {
            cuota.setProvincia(null);
        }
        if (dto.getComunaIds() != null && !dto.getComunaIds().isEmpty()) {
            Set<ComunaModel> coms = new LinkedHashSet<>();
            for (Long cId : dto.getComunaIds()) {
                if (cId != null) {
                    coms.add(comunaRepository.findById(cId)
                            .orElseThrow(() -> new IllegalArgumentException("La comuna indicada con ID " + cId + " no existe.")));
                }
            }
            cuota.setComunas(coms);
            if (!coms.isEmpty()) {
                cuota.setComuna(coms.iterator().next());
            }
        } else if (dto.getComunaId() != null) {
            ComunaModel com = comunaRepository.findById(dto.getComunaId())
                    .orElseThrow(() -> new IllegalArgumentException("La comuna indicada no existe."));
            cuota.setComuna(com);
            cuota.setComunas(new LinkedHashSet<>(java.util.Collections.singletonList(com)));
        } else {
            cuota.setComuna(null);
            cuota.getComunas().clear();
        }

        if (dto.getUsuarioId() != null) {
            cuota.setUsuario(usuarioRepository.findById(dto.getUsuarioId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario indicado no existe.")));
        } else {
            cuota.setUsuario(null);
        }
        if (dto.getAmerbId() != null) {
            cuota.setAmerb(amerbRepository.findById(dto.getAmerbId())
                    .orElseThrow(() -> new IllegalArgumentException("El área de manejo indicada no existe.")));
        } else {
            cuota.setAmerb(null);
        }
        if (dto.getHumedadEstadoId() != null) {
            cuota.setHumedadEstado(humedadEstadoRepository.findById(dto.getHumedadEstadoId())
                    .orElseThrow(() -> new IllegalArgumentException("El estado de humedad indicado no existe.")));
        } else {
            cuota.setHumedadEstado(null);
        }

        if (dto.getNivelAgregacion() != null) cuota.setNivelAgregacion(dto.getNivelAgregacion());
        if (dto.getMetrica() != null) cuota.setMetrica(dto.getMetrica());
        if (dto.getEsPlantilla() != null) cuota.setEsPlantilla(dto.getEsPlantilla());
        if (dto.getModoAccion() != null) cuota.setModoAccion(dto.getModoAccion());
        if (dto.getPeriodo() != null) cuota.setPeriodo(dto.getPeriodo());
        if (dto.getLimiteKg() != null) cuota.setLimiteKg(dto.getLimiteKg());
        if (dto.getFechaInicio() != null) cuota.setFechaInicio(dto.getFechaInicio());
        if (dto.getFechaFin() != null) cuota.setFechaFin(dto.getFechaFin());
        if (dto.getResolucion() != null) cuota.setResolucion(dto.getResolucion());
        if (dto.getActivo() != null) cuota.setActivo(dto.getActivo());

        return cuota;
    }

    // =========================================================================
    // EVALUACIÓN DE CUOTA EN VALIDACIÓN DE DECLARACIÓN (FASE 1)
    // =========================================================================

    public static class EvaluacionCuotaResult {
        private final boolean permite;
        private final boolean posteriorCierre;
        private final boolean declaracionExtemporanea;
        private final boolean excedeLimite;
        private final boolean bloquear;
        private final String marca; // CUOTA_EXCEDIDA | POSTERIOR_CIERRE | DECLARACION_EXTEMPORANEA
        private final List<com.trazalga.api.dto.ResultadoValidacion.MarcaItem> marcas;
        private final BigDecimal totalAcumulado;
        private final BigDecimal limiteEfectivo;
        private final String mensaje;
        private final CuotaExtraccionModel cuotaAplicada;

        public EvaluacionCuotaResult(boolean permite, boolean posteriorCierre, boolean declaracionExtemporanea,
                                     boolean excedeLimite, boolean bloquear, String marca,
                                     List<com.trazalga.api.dto.ResultadoValidacion.MarcaItem> marcas,
                                     BigDecimal totalAcumulado, BigDecimal limiteEfectivo,
                                     String mensaje, CuotaExtraccionModel cuotaAplicada) {
            this.permite = permite;
            this.posteriorCierre = posteriorCierre;
            this.declaracionExtemporanea = declaracionExtemporanea;
            this.excedeLimite = excedeLimite;
            this.bloquear = bloquear;
            this.marca = marca;
            this.marcas = marcas != null ? marcas : java.util.Collections.emptyList();
            this.totalAcumulado = totalAcumulado;
            this.limiteEfectivo = limiteEfectivo;
            this.mensaje = mensaje;
            this.cuotaAplicada = cuotaAplicada;
        }

        public EvaluacionCuotaResult(boolean permite, boolean posteriorCierre, boolean excedeLimite,
                                     boolean bloquear, String marca, BigDecimal totalAcumulado,
                                     BigDecimal limiteEfectivo, String mensaje, CuotaExtraccionModel cuotaAplicada) {
            this(permite, posteriorCierre, false, excedeLimite, bloquear, marca,
                 marca != null ? java.util.Collections.singletonList(com.trazalga.api.dto.ResultadoValidacion.MarcaItem.builder()
                         .marca(marca)
                         .detalle(mensaje)
                         .reglaId(cuotaAplicada != null ? cuotaAplicada.getId() : null)
                         .build()) : java.util.Collections.emptyList(),
                 totalAcumulado, limiteEfectivo, mensaje, cuotaAplicada);
        }

        public boolean isPermite() { return permite; }
        public boolean isPosteriorCierre() { return posteriorCierre; }
        public boolean isDeclaracionExtemporanea() { return declaracionExtemporanea; }
        public boolean isExcedeLimite() { return excedeLimite; }
        public boolean isBloquear() { return bloquear; }
        public String getMarca() { return marca; }
        public List<com.trazalga.api.dto.ResultadoValidacion.MarcaItem> getMarcas() { return marcas; }
        public BigDecimal getTotalAcumulado() { return totalAcumulado; }
        public BigDecimal getLimiteEfectivo() { return limiteEfectivo; }
        public String getMensaje() { return mensaje; }
        public CuotaExtraccionModel getCuotaAplicada() { return cuotaAplicada; }
    }

    private boolean ambitoAplica(CuotaExtraccionModel c, String perfil) {
        String ambito = c.getAmbito() != null ? c.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        if ("AREA_LIBRE".equals(ambito)) {
            return "RECOLECTOR".equalsIgnoreCase(perfil) || "ARMADOR".equalsIgnoreCase(perfil);
        } else if ("AMERB".equals(ambito)) {
            return "AREA".equalsIgnoreCase(perfil) || "ÁREA DE MANEJO".equalsIgnoreCase(perfil);
        }
        return c.getPerfil() == null || c.getPerfil().equalsIgnoreCase(perfil);
    }

    private boolean especieAplica(CuotaExtraccionModel c, Long especieId) {
        return c.getEspecie() == null || (especieId != null && c.getEspecie().getId().equals(especieId));
    }

    private boolean metodoAplica(CuotaExtraccionModel c, Long extraccionTipoId) {
        return c.getExtraccionTipo() == null || (extraccionTipoId != null && c.getExtraccionTipo().getId().equals(extraccionTipoId));
    }

    private boolean vigenciaContiene(CuotaExtraccionModel c, java.time.LocalDate fecha) {
        return vigenciaContiene(c, fecha, null);
    }

    private boolean vigenciaContiene(CuotaExtraccionModel c, java.time.LocalDate fecha, java.time.LocalDate cierre) {
        if (fecha == null) return true;
        if (c.getFechaInicio() != null && fecha.isBefore(toLocalDateSafe(c.getFechaInicio()))) {
            return false;
        }
        if (cierre != null) {
            // Si la cuota cerró, aplica para evaluar POSTERIOR_CIERRE si la extracción ocurrió
            // posterior al cierre dentro de la misma temporada o período anual
            if (fecha.isAfter(cierre)) {
                if (c.getFechaInicio() != null && toLocalDateSafe(c.getFechaInicio()).getYear() != fecha.getYear()) {
                    return false;
                }
                return true;
            }
            return true;
        }
        if (c.getFechaFin() != null && fecha.isAfter(toLocalDateSafe(c.getFechaFin()))) {
            return false;
        }
        return true;
    }

    private boolean amerbAplica(CuotaExtraccionModel c, Long amerbId) {
        return c.getAmerb() == null || (amerbId != null && c.getAmerb().getId().equals(amerbId));
    }

    private boolean usuarioAplica(CuotaExtraccionModel c, Long usuarioId) {
        return c.getUsuario() == null || (usuarioId != null && c.getUsuario().getId().equals(usuarioId));
    }

    /**
     * Evalúa el consumo de cuotas para una declaración según todas las dimensiones parametrizadas (TC.4).
     */
    public EvaluacionCuotaResult evaluarCuotaDeclaracion(
            String perfil,
            Long usuarioId,
            Long amerbId,
            Long especieId,
            Long extraccionTipoId,
            Long comunaImputacionId,
            Date fechaExtraccion,
            Date fechaDeclaracion,
            BigDecimal desembarqueKg,
            BigDecimal capturaKg) {
        return evaluarCuotaDeclaracion(perfil, usuarioId, amerbId, especieId, extraccionTipoId,
                comunaImputacionId, fechaExtraccion, fechaDeclaracion, desembarqueKg, capturaKg, false);
    }

    public EvaluacionCuotaResult evaluarCuotaDeclaracion(
            String perfil,
            Long usuarioId,
            Long amerbId,
            Long especieId,
            Long extraccionTipoId,
            Long comunaImputacionId,
            Date fechaExtraccion,
            Date fechaDeclaracion,
            BigDecimal desembarqueKg,
            BigDecimal capturaKg,
            boolean esEdicion) {

        String modo = getModoImputacion();
        Date fechaEval;
        if ("DECLARACION".equalsIgnoreCase(modo)) {
            fechaEval = (fechaDeclaracion != null) ? fechaDeclaracion : (fechaExtraccion != null ? fechaExtraccion : new Date());
        } else {
            fechaEval = (fechaExtraccion != null) ? fechaExtraccion : (fechaDeclaracion != null ? fechaDeclaracion : new Date());
        }

        java.time.LocalDate f_imp = toLocalDateSafe(fechaEval);
        java.time.LocalDate f_ing = toLocalDateSafe(fechaDeclaracion != null ? fechaDeclaracion : (fechaExtraccion != null ? fechaExtraccion : new Date()));

        // 1. Si es AMERB, validar primero si la especie está habilitada por resolución
        if (amerbId != null && especieId != null) {
            if (!amerbEspecieHabilitadaService.isEspecieHabilitada(amerbId, especieId)) {
                return new EvaluacionCuotaResult(false, false, false, true, null,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        "La especie indicada no está habilitada por resolución para esta Área de Manejo (AMERB).", null);
            }
        }

        // 2. Buscar cuotas activas
        List<CuotaExtraccionModel> todasCuotas = cuotaRepository.findByActivoTrue();

        boolean cierrePorVencimientoActivo = (configuracionGeneralService != null)
                && configuracionGeneralService.getBoolean("cuota_cierre_automatico_vencimiento", false);
        int diasGracia = (configuracionGeneralService != null)
                ? configuracionGeneralService.getInt("cuota_dias_gracia_declaracion", 0) : 0;
        java.time.LocalDate hoyParaVencimiento = f_ing != null ? f_ing : java.time.LocalDate.now();

        // 3. Filtrar cuotas aplicables mediante especificaciones combinables
        List<CuotaExtraccionModel> aplicables = new ArrayList<>();
        for (CuotaExtraccionModel c : todasCuotas) {
            if (!ambitoAplica(c, perfil)) continue;
            if (!especieAplica(c, especieId)) continue;
            if (!metodoAplica(c, extraccionTipoId)) continue;
            java.time.LocalDate cierre = c.fechaCierreEfectiva(hoyParaVencimiento, cierrePorVencimientoActivo);
            if (!vigenciaContiene(c, f_imp, cierre)) continue;
            if (!amerbAplica(c, amerbId)) continue;
            if (!usuarioAplica(c, usuarioId)) continue;
            if (comunaImputacionId != null && !contieneComuna(c, comunaImputacionId)) continue;

            aplicables.add(c);
        }

        // D6: Persona específica sobre plantilla (la cuota individual específica reemplaza a la general)
        boolean tieneCuotaIndividualEspecifica = aplicables.stream()
                .anyMatch(c -> c.getUsuario() != null && usuarioId != null && c.getUsuario().getId().equals(usuarioId));
        if (tieneCuotaIndividualEspecifica) {
            aplicables.removeIf(c -> Boolean.TRUE.equals(c.getEsPlantilla()));
        }

        if (aplicables.isEmpty()) {
            return new EvaluacionCuotaResult(true, false, false, false, false, null, java.util.Collections.emptyList(),
                    BigDecimal.ZERO, BigDecimal.ZERO, "No aplica cuota de extracción.", null);
        }

        // 4. EVALUACIÓN CONCURRENTE ACUMULADA DE TODAS LAS CUOTAS APLICABLES (TC.4)
        CuotaExtraccionModel cuotaCuelloBotella = null;
        double maxPctConsumo = -1.0;
        BigDecimal totalCuello = BigDecimal.ZERO;
        BigDecimal limiteCuello = BigDecimal.ZERO;

        List<com.trazalga.api.dto.ResultadoValidacion.MarcaItem> marcasGeneradas = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        boolean algunBloqueo = false;
        String motivoPrimerBloqueo = null;
        boolean posteriorCierreFlag = false;
        boolean declaracionExtemporaneaFlag = false;
        boolean excedeLimiteFlag = false;

        for (CuotaExtraccionModel c : aplicables) {
            java.time.LocalDate cierre = c.fechaCierreEfectiva(hoyParaVencimiento, cierrePorVencimientoActivo);

            // A. Verificación de Cierre y Declaración Extemporánea (TC.4)
            if (cierre != null) {
                if (f_imp != null && f_imp.isAfter(cierre)) {
                    // Caso 1: f_imp > cierre -> POSTERIOR_CIERRE
                    posteriorCierreFlag = true;
                    String accionCierre = configuracionGeneralService.getValor("cuota_accion_post_cierre", "ALERTA_CRITICA");
                    boolean bloquear = "BLOQUEO_TOTAL".equalsIgnoreCase(accionCierre) || "BLOQUEO".equalsIgnoreCase(accionCierre);
                    String msg = String.format("Bloqueo: La cuota %s se encuentra cerrada desde el %s. Extracción fuera de plazo.",
                            describirAlcance(c), cierre);
                    com.trazalga.api.services.hallazgos.CriterioHallazgo crit =
                            com.trazalga.api.services.hallazgos.CriterioHallazgo.deFecha("fecha_cierre", cierre.toString(), f_imp.toString());

                    marcasGeneradas.add(com.trazalga.api.dto.ResultadoValidacion.MarcaItem.builder()
                            .marca("POSTERIOR_CIERRE")
                            .detalle(msg)
                            .reglaId(c.getId())
                            .criterio(crit)
                            .build());
                    advertencias.add(msg);
                    if (bloquear) {
                        algunBloqueo = true;
                        if (motivoPrimerBloqueo == null) motivoPrimerBloqueo = msg;
                    }
                } else if (f_imp != null && !f_imp.isAfter(cierre) && f_ing != null && f_ing.isAfter(cierre.plusDays(diasGracia))) {
                    // Caso 2: f_imp <= cierre y f_ing > cierre + gracia -> DECLARACION_EXTEMPORANEA
                    declaracionExtemporaneaFlag = true;
                    String accionExtemp = configuracionGeneralService.getValor("cuota_accion_extemporanea", "ALERTA_CRITICA");
                    boolean bloquear = "BLOQUEO_TOTAL".equalsIgnoreCase(accionExtemp) || "BLOQUEO".equalsIgnoreCase(accionExtemp);
                    java.time.LocalDate plazoGraciaFecha = cierre.plusDays(diasGracia);
                    String detalleCausa = esEdicion ? "Modificación posterior al cierre" : ("Declaración extemporánea ingresada el " + f_ing);
                    String msg = String.format("La cuota %s cerró el %s (plazo gracia %d días hasta %s). %s.",
                            describirAlcance(c), cierre, diasGracia, plazoGraciaFecha, detalleCausa);
                    com.trazalga.api.services.hallazgos.CriterioHallazgo crit =
                            com.trazalga.api.services.hallazgos.CriterioHallazgo.deFecha("fecha_cierre", plazoGraciaFecha.toString(), f_ing.toString());

                    marcasGeneradas.add(com.trazalga.api.dto.ResultadoValidacion.MarcaItem.builder()
                            .marca("DECLARACION_EXTEMPORANEA")
                            .detalle(msg)
                            .reglaId(c.getId())
                            .criterio(crit)
                            .build());
                    advertencias.add(msg);
                    if (bloquear) {
                        algunBloqueo = true;
                        if (motivoPrimerBloqueo == null) motivoPrimerBloqueo = msg;
                    }
                }
            }

            // B. Consumo acumulado y límite efectivo
            BigDecimal consumoAcumulado = ejecutarConsultaConsumo(c, fechaEval, perfil, usuarioId);
            boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(c.getMetrica());
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(c, fechaEval);

            BigDecimal nuevoMonto = esCaptura ? (capturaKg != null ? capturaKg : BigDecimal.ZERO)
                                              : (desembarqueKg != null ? desembarqueKg : BigDecimal.ZERO);
            BigDecimal total = consumoAcumulado.add(nuevoMonto);

            double pct = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                    ? total.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP).doubleValue()
                    : 100.0;

            if (cuotaCuelloBotella == null || pct > maxPctConsumo) {
                cuotaCuelloBotella = c;
                maxPctConsumo = pct;
                totalCuello = total;
                limiteCuello = limiteEfectivo;
            }

            // C. Alerta de umbral en tiempo real (TC.6)
            double umbralRestantePct = configuracionGeneralService.getDouble("cuota_umbral_restante_pct", 10.0);
            double umbralDisparo = 100.0 - umbralRestantePct;
            if (pct >= umbralDisparo) {
                Long personaId = c.getUsuario() != null ? c.getUsuario().getId() : (usuarioId != null ? usuarioId : 0L);
                String claveAviso = String.format("CUOTA:%d:UMBRAL:%d", c.getId(), personaId);
                if (avisoEnviadoRepository != null && !avisoEnviadoRepository.existsById(claveAviso)) {
                    avisoEnviadoRepository.save(new com.trazalga.api.models.AvisoEnviadoModel(claveAviso, new Date()));
                    if (eventPublisher != null) {
                        eventPublisher.publishEvent(new com.trazalga.api.events.CuotaUmbralAlcanzado(c.getId(), personaId, pct));
                    }
                }
            }

            // D. Comparar contra el límite: genera una marca CUOTA_EXCEDIDA por cuota afectada (TC.4)
            if (total.compareTo(limiteEfectivo) > 0) {
                excedeLimiteFlag = true;
                String modoCuota = (c.getModoAccion() != null && !c.getModoAccion().isBlank())
                        ? c.getModoAccion().trim().toUpperCase()
                        : "SOLO_ALERTA";
                boolean bloquear = "BLOQUEO_DECLARACION".equalsIgnoreCase(modoCuota) || "BLOQUEO".equalsIgnoreCase(modoCuota) || "BLOQUEO_TOTAL".equalsIgnoreCase(modoCuota);
                String metricaNombre = esCaptura ? "captura biológica" : "desembarque físico";
                BigDecimal exceso = total.subtract(limiteEfectivo);
                String msg = String.format("Cuota %s (%s) de %.2f kg ha sido sobrepasada. Total acumulado con esta declaración: %.2f kg (exceso: %.2f kg, %.1f%% de consumo).",
                        describirAlcance(c), metricaNombre, limiteEfectivo, total, exceso, pct);

                com.trazalga.api.services.hallazgos.CriterioHallazgo crit =
                        com.trazalga.api.services.hallazgos.CriterioHallazgo.deKilos("limite_kg", limiteEfectivo.doubleValue(), total.doubleValue());

                marcasGeneradas.add(com.trazalga.api.dto.ResultadoValidacion.MarcaItem.builder()
                        .marca("CUOTA_EXCEDIDA")
                        .detalle(msg)
                        .reglaId(c.getId())
                        .criterio(crit)
                        .build());
                advertencias.add(msg);
                if (bloquear) {
                    algunBloqueo = true;
                    if (motivoPrimerBloqueo == null) motivoPrimerBloqueo = msg;
                }
            }
        }

        if (algunBloqueo) {
            String marcaPrincipal = marcasGeneradas.isEmpty() ? "CUOTA_EXCEDIDA" : marcasGeneradas.get(0).getMarca();
            return new EvaluacionCuotaResult(false, posteriorCierreFlag, declaracionExtemporaneaFlag, excedeLimiteFlag,
                    true, marcaPrincipal, marcasGeneradas, totalCuello, limiteCuello, motivoPrimerBloqueo, cuotaCuelloBotella);
        }

        if (!marcasGeneradas.isEmpty()) {
            String msgAlerta = String.join(" | ", advertencias);
            String marcaPrincipal = marcasGeneradas.get(0).getMarca();
            return new EvaluacionCuotaResult(true, posteriorCierreFlag, declaracionExtemporaneaFlag, excedeLimiteFlag,
                    false, marcaPrincipal, marcasGeneradas, totalCuello, limiteCuello, msgAlerta, cuotaCuelloBotella);
        }

        return new EvaluacionCuotaResult(true, false, false, false, false, null, java.util.Collections.emptyList(),
                totalCuello, limiteCuello, "Declaración dentro de la cuota.", cuotaCuelloBotella);
    }

    public int calcularEspecificidadCuota(CuotaExtraccionModel c) {
        int score = 0;
        if (c.getUsuario() != null) score += 64;
        if (c.getAmerb() != null) score += 32;
        if (c.getComuna() != null || (c.getComunas() != null && !c.getComunas().isEmpty())) score += 16;
        if (c.getProvincia() != null) score += 8;
        if (c.getRegion() != null) score += 4;
        if (c.getMacrozona() != null && !Boolean.TRUE.equals(c.getMacrozona().getEsNacional())) score += 2;
        if (score == 0) score = 1;
        return score;
    }

    // =========================================================================
    // COMPATIBILIDAD CON ENDPOINTS Y DASHBOARD ANTERIORES
    // =========================================================================

    public QuotaCheckResult checkDeclarationQuota(Long usuarioId, String perfil, Long especieId, Date fechaDeclaracion, BigDecimal nuevaCantidadKg) {
        EvaluacionCuotaResult res = evaluarCuotaDeclaracion(
                perfil, usuarioId, null, especieId, null, null,
                null, fechaDeclaracion, nuevaCantidadKg, nuevaCantidadKg);
        return new QuotaCheckResult(res.isPermite(), res.getMensaje());
    }

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(
            Date startDate, Date endDate, String periodo, Long comunaId, Long extraccionTipoId, String perfil) {
        if (perfil != null && !perfil.isBlank()) {
            log.warn("Parámetro 'perfil' deprecado en getControlCuotasDiarioGlobal. Se favorece ámbito AREA_LIBRE.");
        }

        List<CuotaExtraccionModel> cuotas = cuotaRepository.findByAmbitoAndActivoTrue("AREA_LIBRE");
        if ((cuotas == null || cuotas.isEmpty()) && perfil != null && !perfil.isBlank()) {
            cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil.toUpperCase());
        }
        if (cuotas == null) cuotas = Collections.emptyList();

        if (comunaId != null) {
            cuotas = cuotas.stream()
                    .filter(c -> contieneComuna(c, comunaId))
                    .collect(Collectors.toList());
        }

        if (extraccionTipoId != null) {
            cuotas = cuotas.stream()
                    .filter(c -> c.getExtraccionTipo() == null || c.getExtraccionTipo().getId().equals(extraccionTipoId))
                    .collect(Collectors.toList());
        }

        if (startDate == null) {
            LocalDate localDate = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            startDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        LocalDate sDate = toLocalDateSafe(startDate);
        LocalDate eDate = toLocalDateSafe(endDate);

        String[] nombresMeses = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                                 "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

        List<ControlCuotaDiariaDTO> result = new ArrayList<>();
        for (CuotaExtraccionModel cuota : cuotas) {
            if (periodo != null && !periodo.isBlank() && !"ALL".equalsIgnoreCase(periodo) && !periodo.equalsIgnoreCase(cuota.getPeriodo())) {
                continue;
            }
            if (cuota.getEspecie() == null) {
                continue;
            }

            // T1.7: Conservar sólo cuotas cuya vigencia se solapa con [startDate, endDate] (hoy por defecto)
            // Las cuotas sin fechas (formato anterior) se siguen mostrando.
            if (cuota.getFechaInicio() != null || cuota.getFechaFin() != null) {
                LocalDate cInicio = cuota.getFechaInicio() != null ? toLocalDateSafe(cuota.getFechaInicio()) : LocalDate.MIN;
                LocalDate cFin = cuota.getFechaFin() != null ? toLocalDateSafe(cuota.getFechaFin()) : LocalDate.MAX;
                boolean seSolapan = !cInicio.isAfter(eDate) && !sDate.isAfter(cFin);
                if (!seSolapan) {
                    continue;
                }
            }

            BigDecimal sumVolumen = BigDecimal.ZERO;
            Double porcentaje = 0.0;
            Integer personasConActividad = null;
            Integer personasSobreLimite = null;
            Double maxPorcentaje = null;
            String textoConsumoPlantilla = null;

            BigDecimal limiteNominal = (cuota.getLimiteKg() != null) ? BigDecimal.valueOf(cuota.getLimiteKg()) : BigDecimal.ZERO;
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(cuota, startDate);

            if (Boolean.TRUE.equals(cuota.getEsPlantilla())) {
                if (consumoIndividualQuery != null) {
                    com.trazalga.api.dto.ResumenPlantillaDTO resumen = consumoIndividualQuery.resumenPlantilla(cuota, startDate);
                    sumVolumen = resumen.getConsumoTotal();
                    porcentaje = resumen.getMaxPorcentaje();
                    personasConActividad = resumen.getPersonasConActividad();
                    personasSobreLimite = resumen.getPersonasSobreLimite();
                    maxPorcentaje = resumen.getMaxPorcentaje();
                    textoConsumoPlantilla = resumen.getTextoConsumo();
                }
            } else {
                sumVolumen = ejecutarConsultaConsumo(cuota, startDate, perfil, cuota.getUsuario() != null ? cuota.getUsuario().getId() : null);
                if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
                    porcentaje = sumVolumen.doubleValue() / limiteEfectivo.doubleValue() * 100.0;
                }
            }

            String humedadNombre = cuota.getHumedadEstado() != null ? cuota.getHumedadEstado().getNombre() : null;
            String extraccionTipoNombre = cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getNombre() : null;
            String comunaNombre = cuota.getComuna() != null ? cuota.getComuna().getNombre() : null;
            Long comunaIdCuota = cuota.getComuna() != null ? cuota.getComuna().getId() : null;
            BigDecimal factor = null;
            String equivalencia = null;

            if (cuota.getHumedadEstado() != null && cuota.getEspecie() != null && !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica())) {
                Optional<FactorConversionModel> fOpt = factorConversionService.findFactorVigente(
                        cuota.getEspecie().getId(), cuota.getHumedadEstado().getId(), startDate);
                if (fOpt.isPresent()) {
                    factor = fOpt.get().getFactor();
                    if (factor != null && factor.compareTo(BigDecimal.ONE) != 0) {
                        equivalencia = String.format("%,.0f kg %s ≡ %,.0f kg captura (factor %.2f)",
                                limiteNominal.doubleValue(),
                                humedadNombre != null ? humedadNombre.toLowerCase() : "seco",
                                limiteEfectivo.doubleValue(),
                                factor.doubleValue());
                    }
                }
            }

            Set<Long> cIds = idsComunas(cuota);
            String comunasNombre = null;
            if (!cIds.isEmpty()) {
                if (cuota.getComunas() != null && !cuota.getComunas().isEmpty()) {
                    comunasNombre = cuota.getComunas().stream().map(ComunaModel::getNombre).collect(Collectors.joining(" + "));
                } else if (cuota.getComuna() != null) {
                    comunasNombre = cuota.getComuna().getNombre();
                }
            }

            ControlCuotaDiariaDTO dto = ControlCuotaDiariaDTO.builder()
                .cuotaId(cuota.getId())
                .especieNombre(cuota.getEspecie().getNombre())
                .volumenExtraido(sumVolumen)
                .limiteCuota(limiteEfectivo)
                .limiteNominal(limiteNominal)
                .limiteEfectivo(limiteEfectivo)
                .humedadEstadoNombre(humedadNombre)
                .metrica(cuota.getMetrica() != null ? cuota.getMetrica() : "CAPTURA")
                .factorConversion(factor)
                .descripcionEquivalencia(equivalencia)
                .porcentajeUso(Math.round(porcentaje * 100.0) / 100.0)
                .alcance(describirAlcance(cuota))
                .periodo(cuota.getPeriodo())
                .ambito(cuota.getAmbito() != null ? cuota.getAmbito() : "AREA_LIBRE")
                .extraccionTipoNombre(extraccionTipoNombre)
                .comunaId(comunaIdCuota)
                .comunaNombre(comunaNombre)
                .comunaIds(cIds)
                .comunasNombre(comunasNombre)
                .fechaInicio(cuota.getFechaInicio() != null ? cuota.getFechaInicio().toString() : null)
                .fechaFin(cuota.getFechaFin() != null ? cuota.getFechaFin().toString() : null)
                .vigenciaFormateada(formatearVigencia(cuota, nombresMeses))
                .esPlantilla(Boolean.TRUE.equals(cuota.getEsPlantilla()))
                .personasConActividad(personasConActividad)
                .personasSobreLimite(personasSobreLimite)
                .maxPorcentaje(maxPorcentaje)
                .textoConsumoPlantilla(textoConsumoPlantilla)
                .usuarioId(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null)
                .usuarioNombre(cuota.getUsuario() != null ? ((cuota.getUsuario().getNombres() != null ? cuota.getUsuario().getNombres() : "") + " " + (cuota.getUsuario().getApellidop() != null ? cuota.getUsuario().getApellidop() : "")).trim() : null)
                .provinciaId(cuota.getProvincia() != null ? cuota.getProvincia().getId() : null)
                .provinciaNombre(cuota.getProvincia() != null ? cuota.getProvincia().getNombre() : null)
                .regionId(cuota.getRegion() != null ? cuota.getRegion().getId() : null)
                .regionNombre(cuota.getRegion() != null ? cuota.getRegion().getNombre() : null)
                .build();

            result.add(dto);
        }
        return result;
    }

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(Date startDate, Date endDate, String periodo, String perfil) {
        return getControlCuotasDiarioGlobal(startDate, endDate, periodo, null, null, perfil);
    }

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(Date startDate, Date endDate, String periodo, Long comunaId, Long extraccionTipoId) {
        return getControlCuotasDiarioGlobal(startDate, endDate, periodo, comunaId, extraccionTipoId, null);
    }

    private void resolverReferencias(CuotaExtraccionModel cuota) {
        if (cuota.getRegion() != null && cuota.getRegion().getId() != null) {
            cuota.setRegion(regionRepository.findById(cuota.getRegion().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La región indicada no existe.")));
        }
        if (cuota.getMacrozona() != null && cuota.getMacrozona().getId() != null) {
            cuota.setMacrozona(macrozonaRepository.findById(cuota.getMacrozona().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La macrozona indicada no existe.")));
        }
        if (cuota.getProvincia() != null && cuota.getProvincia().getId() != null) {
            cuota.setProvincia(provinciaRepository.findById(cuota.getProvincia().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La provincia indicada no existe.")));
        }
        if (cuota.getComunas() != null && !cuota.getComunas().isEmpty()) {
            Set<ComunaModel> resueltas = new LinkedHashSet<>();
            for (ComunaModel c : cuota.getComunas()) {
                if (c != null && c.getId() != null) {
                    resueltas.add(comunaRepository.findById(c.getId())
                            .orElseThrow(() -> new IllegalArgumentException("La comuna indicada con ID " + c.getId() + " no existe.")));
                }
            }
            cuota.setComunas(resueltas);
            if (cuota.getComuna() == null && !resueltas.isEmpty()) {
                cuota.setComuna(resueltas.iterator().next());
            }
        } else if (cuota.getComuna() != null && cuota.getComuna().getId() != null) {
            ComunaModel com = comunaRepository.findById(cuota.getComuna().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La comuna indicada no existe."));
            cuota.setComuna(com);
            cuota.setComunas(new LinkedHashSet<>(Collections.singletonList(com)));
        }
        if (cuota.getEspecie() != null && cuota.getEspecie().getId() != null) {
            cuota.setEspecie(especieRepository.findById(cuota.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La especie indicada no existe.")));
        }
        if (cuota.getAmerb() != null && cuota.getAmerb().getId() != null) {
            cuota.setAmerb(amerbRepository.findById(cuota.getAmerb().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El área de manejo indicada no existe.")));
        }
        if (cuota.getUsuario() != null && cuota.getUsuario().getId() != null) {
            cuota.setUsuario(usuarioRepository.findById(cuota.getUsuario().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario indicado no existe.")));
        }
        if (cuota.getExtraccionTipo() != null && cuota.getExtraccionTipo().getId() != null) {
            cuota.setExtraccionTipo(extraccionTipoRepository.findById(cuota.getExtraccionTipo().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El método de extracción indicado no existe.")));
        }
        if (cuota.getHumedadEstado() != null && cuota.getHumedadEstado().getId() != null) {
            cuota.setHumedadEstado(humedadEstadoRepository.findById(cuota.getHumedadEstado().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El estado de humedad indicado no existe.")));
        }
    }

    public java.util.Map<String, Object> getMaestros() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("regiones", regionRepository.findAll().stream()
            .map(r -> java.util.Map.of("id", r.getId(), "nombre", r.getNombre() != null ? r.getNombre() : ""))
            .toList());
        out.put("macrozonas", macrozonaRepository.findByActivoTrue().stream()
            .map(mz -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", mz.getId());
                m.put("nombre", mz.getNombre() != null ? mz.getNombre() : "");
                m.put("codigo", mz.getCodigo() != null ? mz.getCodigo() : "");
                m.put("esNacional", Boolean.TRUE.equals(mz.getEsNacional()));
                return m;
            })
            .toList());
        out.put("provincias", provinciaRepository.findAll().stream()
            .map(p -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", p.getId());
                m.put("nombre", p.getNombre() != null ? p.getNombre() : "");
                if (p.getRegion() != null) m.put("regionId", p.getRegion().getId());
                return m;
            })
            .toList());
        out.put("comunas", comunaRepository.findAll().stream()
            .map(c -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", c.getId());
                m.put("nombre", c.getNombre() != null ? c.getNombre() : "");
                if (c.getRegion() != null) m.put("regionId", c.getRegion().getId());
                if (c.getProvincia() != null) m.put("provinciaId", c.getProvincia().getId());
                return m;
            })
            .toList());
        out.put("especies", especieRepository.findAll().stream()
            .map(e -> java.util.Map.of("id", e.getId(), "nombre", e.getNombre() != null ? e.getNombre() : ""))
            .toList());
        out.put("extraccionTipos", extraccionTipoRepository.findAll().stream()
            .map(et -> java.util.Map.of("id", et.getId(), "nombre", et.getNombre() != null ? et.getNombre() : ""))
            .toList());
        out.put("humedadEstados", humedadEstadoRepository.findAll().stream()
            .map(he -> java.util.Map.of("id", he.getId(), "nombre", he.getNombre() != null ? he.getNombre() : ""))
            .toList());
        out.put("amerbs", amerbRepository.findAll().stream()
            .map(a -> java.util.Map.of("id", a.getId(), "nombre", a.getNombre() != null ? a.getNombre() : ""))
            .toList());
        return out;
    }

    private void validarDatosBasicos(CuotaExtraccionModel cuota) {
        if (cuota.getLimiteKg() == null || cuota.getLimiteKg() <= 0) {
            throw new IllegalArgumentException("El límite de la cuota debe ser mayor que 0 kg.");
        }
        if (cuota.getPeriodo() == null || cuota.getPeriodo().isBlank()) {
            throw new IllegalArgumentException("El periodo de la cuota es obligatorio (DIARIO, MENSUAL, ANUAL).");
        }

        // T1.4: Validación de vigencia mensual en mismo mes calendario
        if ("MENSUAL".equalsIgnoreCase(cuota.getPeriodo()) && cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            LocalDate inicio = toLocalDateSafe(cuota.getFechaInicio());
            LocalDate fin = toLocalDateSafe(cuota.getFechaFin());
            if (inicio.getYear() != fin.getYear() || inicio.getMonthValue() != fin.getMonthValue()) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                throw new IllegalArgumentException(String.format(
                        "La vigencia mensual debe quedar dentro de un mismo mes (recibido: %s a %s).",
                        inicio.format(dtf), fin.format(dtf)));
            }
        }

        if (cuota.getAmbito() == null || cuota.getAmbito().isBlank()) {
            if (cuota.getAmerb() != null || "AREA".equalsIgnoreCase(cuota.getPerfil())) {
                cuota.setAmbito("AMERB");
            } else {
                cuota.setAmbito("AREA_LIBRE");
            }
        } else {
            String ambNorm = cuota.getAmbito().trim().toUpperCase();
            if (!java.util.Set.of("AREA_LIBRE", "AMERB").contains(ambNorm)) {
                throw new IllegalArgumentException("Ámbito de cuota inválido: " + cuota.getAmbito() + ". Los valores permitidos son AREA_LIBRE o AMERB.");
            }
            cuota.setAmbito(ambNorm);
        }

        if ("AREA_LIBRE".equalsIgnoreCase(cuota.getAmbito())) {
            // T1.2: Columna heredada, sin efecto en áreas libres
            cuota.setPerfil("RECOLECTOR");
        } else if (cuota.getPerfil() == null || cuota.getPerfil().isBlank()) {
            cuota.setPerfil("AMERB".equalsIgnoreCase(cuota.getAmbito()) ? "AREA" : "RECOLECTOR");
        }
        if (cuota.getNivelAgregacion() == null || cuota.getNivelAgregacion().trim().isEmpty()) {
            throw new IllegalArgumentException("El nivel de agregación de la cuota es obligatorio (COMUNA, PROVINCIA, REGION, MACROZONA, INDIVIDUAL, NACIONAL).");
        }
        String nivelNorm = cuota.getNivelAgregacion().trim().toUpperCase();
        if (!java.util.Set.of("COMUNA", "PROVINCIA", "REGION", "MACROZONA", "INDIVIDUAL", "NACIONAL").contains(nivelNorm)) {
            throw new IllegalArgumentException("Nivel de agregación inválido: " + cuota.getNivelAgregacion() + ". Los valores permitidos son COMUNA, PROVINCIA, REGION, MACROZONA, INDIVIDUAL, NACIONAL.");
        }
        cuota.setNivelAgregacion(nivelNorm);

        if (cuota.getEstado() != null && !cuota.getEstado().trim().isEmpty()) {
            String estNorm = cuota.getEstado().trim().toUpperCase();
            if (!java.util.Set.of("ABIERTA", "CERRADA").contains(estNorm)) {
                throw new IllegalArgumentException("Estado de cuota inválido: " + cuota.getEstado() + ". Los valores permitidos son ABIERTA o CERRADA.");
            }
        }
        // Regla normativa Sernapesca (T3):
        // Por defecto las cuotas se descuentan obligatoriamente en CAPTURA biológica corregida.
        // La métrica DESEMBARQUE sólo se permite si la resolución técnica de Subpesca lo explicita expresamente.
        if (cuota.getMetrica() == null || cuota.getMetrica().isBlank()) {
            cuota.setMetrica("CAPTURA");
        } else if ("DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica())) {
            cuota.setMetrica("DESEMBARQUE");
            if (cuota.getResolucion() == null || cuota.getResolucion().trim().isEmpty()) {
                throw new IllegalArgumentException("Sernapesca definió que las cuotas se descuentan obligatoriamente con captura biológica corregida. La métrica DESEMBARQUE sólo se permite si la resolución técnica de Subpesca lo especifica expresamente (campo resolución obligatorio).");
            }
        } else if ("CAPTURA".equalsIgnoreCase(cuota.getMetrica())) {
            cuota.setMetrica("CAPTURA");
        } else {
            throw new IllegalArgumentException("Métrica inválida: " + cuota.getMetrica() + ". Los valores permitidos son CAPTURA o DESEMBARQUE.");
        }

        // Modo de acción (R3.2)
        if (cuota.getModoAccion() == null || cuota.getModoAccion().isBlank()) {
            cuota.setModoAccion("SOLO_ALERTA");
        } else {
            String modoNorm = cuota.getModoAccion().trim().toUpperCase();
            if (!java.util.Set.of("SOLO_ALERTA", "BLOQUEO_DECLARACION").contains(modoNorm)) {
                throw new IllegalArgumentException("Modo de acción inválido: " + cuota.getModoAccion() + ". Los valores permitidos son SOLO_ALERTA o BLOQUEO_DECLARACION.");
            }
            cuota.setModoAccion(modoNorm);
        }

        // TM.1: Delegación de validación y normalización territorial y personal a la estrategia de alcance
        AlcanceCuota estrategia = getAlcanceRegistry().resolver(cuota);
        if (estrategia != null) {
            estrategia.validarYNormalizar(cuota);
        }
    }

    public void validarSolapamiento(CuotaExtraccionModel cuota) {
        if (!Boolean.TRUE.equals(cuota.getActivo())) {
            return;
        }

        List<CuotaExtraccionModel> activas = new ArrayList<>(cuotaRepository.findByActivoTrue());
        activas.removeIf(c -> cuota.getId() != null && cuota.getId().equals(c.getId()));

        String ambitoCuota = cuota.getAmbito() != null ? cuota.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        String nivelCuota = cuota.getNivelAgregacion() != null ? cuota.getNivelAgregacion().trim().toUpperCase() : "";

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        AlcanceCuota estrategia = getAlcanceRegistry().resolver(cuota);

        for (CuotaExtraccionModel otra : activas) {
            String ambitoOtra = otra.getAmbito() != null ? otra.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
            String nivelOtra = otra.getNivelAgregacion() != null ? otra.getNivelAgregacion().trim().toUpperCase() : "";

            if (!ambitoCuota.equals(ambitoOtra)) continue;
            if (!nivelCuota.equals(nivelOtra)) continue;
            if (!seSolapan(cuota, otra)) continue;
            if (!especiesComparables(cuota, otra)) continue;

            String nombreEsp = otra.getEspecie() != null ? otra.getEspecie().getNombre() : (cuota.getEspecie() != null ? cuota.getEspecie().getNombre() : "Todas las especies");
            String nombreMet = otra.getExtraccionTipo() != null ? otra.getExtraccionTipo().getNombre() : (cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getNombre() : "Todos los métodos");
            String iniStr = otra.getFechaInicio() != null ? toLocalDateSafe(otra.getFechaInicio()).format(dtf) : "indefinido";
            String finStr = otra.getFechaFin() != null ? toLocalDateSafe(otra.getFechaFin()).format(dtf) : "indefinido";

            if (estrategia != null && estrategia.mismoAlcance(cuota, otra)) {
                String sujeto = estrategia.describirConflicto(cuota, otra);
                throw new IllegalArgumentException(String.format(
                    "%s ya tiene una cuota activa de %s (%s) del %s al %s (cuota #%d).",
                    sujeto, nombreEsp, nombreMet, iniStr, finStr, otra.getId()
                ));
            }
        }
    }

    public void validarJerarquia(CuotaExtraccionModel cuota) {
        if (!Boolean.TRUE.equals(cuota.getActivo())) {
            return;
        }

        AlcanceCuota estrategia = getAlcanceRegistry().resolver(cuota);
        if (estrategia != null && !estrategia.comparableEnJerarquia()) {
            return;
        }

        List<CuotaExtraccionModel> activas = new ArrayList<>(cuotaRepository.findByActivoTrue());
        activas.removeIf(c -> cuota.getId() != null && cuota.getId().equals(c.getId()));

        validarContraAmbitosSuperiores(cuota, activas);
    }

    private void validarContraAmbitosSuperiores(CuotaExtraccionModel cuota, List<CuotaExtraccionModel> activas) {
        String alcance = alcanceDe(cuota);
        Long regionId = idRegionDe(cuota);
        Long provinciaId = idProvinciaDe(cuota);
        Date fEval = cuota.getFechaInicio() != null ? cuota.getFechaInicio() : new Date();
        BigDecimal limHija = calcularLimiteEfectivo(cuota, fEval);

        // 1. Validar contra AMERB si es usuario en AMERB
        if ("USUARIO".equals(alcance) && cuota.getAmerb() != null) {
            for (CuotaExtraccionModel padre : activas) {
                if ("AREA".equals(alcanceDe(padre))
                        && padre.getAmerb().getId().equals(cuota.getAmerb().getId())
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)) {
                    BigDecimal limPadre = calcularLimiteEfectivo(padre, fEval);
                    if (limHija.compareTo(limPadre) > 0) {
                        throw new IllegalArgumentException(String.format(
                                "La cuota del usuario (%.2f kg) no puede superar la cuota del área de manejo «%s» (%.2f kg) para %s en periodo %s.",
                                limHija, nombreAmerb(padre), limPadre, nombreEspecie(padre), padre.getPeriodo()));
                    }
                }
            }
        }

        // 2. Validar Comunal contra Provincial (orden Comunal <= Provincial)
        if ("COMUNA".equals(alcance) && provinciaId != null) {
            for (CuotaExtraccionModel padre : activas) {
                Long provPadre = idProvinciaDe(padre);
                if ("PROVINCIA".equals(alcanceDe(padre))
                        && provPadre != null && provPadre.equals(provinciaId)
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)) {
                    BigDecimal limPadre = calcularLimiteEfectivo(padre, fEval);
                    if (limHija.compareTo(limPadre) > 0) {
                        throw new IllegalArgumentException(String.format(
                                "La cuota comunal (%.2f kg) no puede superar la cuota provincial de %s (%.2f kg) para %s en periodo %s.",
                                limHija, nombreProvinciaDe(padre), limPadre, nombreEspecie(padre), padre.getPeriodo()));
                    }
                }
            }
        }

        // 3. Validar Comunal o Provincial subordinada contra Regional (orden Provincial <= Regional)
        if (("USUARIO".equals(alcance) || "AREA".equals(alcance) || "COMUNA".equals(alcance) || "PROVINCIA".equals(alcance)) && regionId != null) {
            String etiqueta = "COMUNA".equals(alcance) ? "subordinada" : ("PROVINCIA".equals(alcance) ? "provincial" : "subordinada");
            validarContraRegion(cuota, activas, regionId, etiqueta, fEval, limHija);
        }

        // 4. Validar contra Macrozonas superiores que cubran la región (N:M)
        if (regionId != null && !"NACIONAL".equals(alcance)) {
            List<MacrozonaModel> mzsCubren = macrozonaService.getMacrozonasForRegion(regionId, new Date());
            Set<Long> macrozonaIdsPadre = mzsCubren.stream().map(MacrozonaModel::getId).collect(Collectors.toSet());

            for (CuotaExtraccionModel padre : activas) {
                String alcancePadre = alcanceDe(padre);
                if (("MACROZONA".equals(alcancePadre) || "NACIONAL".equals(alcancePadre))
                        && padre.getMacrozona() != null
                        && macrozonaIdsPadre.contains(padre.getMacrozona().getId())
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)) {
                    BigDecimal limPadre = calcularLimiteEfectivo(padre, fEval);
                    if (limHija.compareTo(limPadre) > 0) {
                        throw new IllegalArgumentException(String.format(
                                "La cuota %s (%.2f kg) no puede superar la cuota macrozonal de «%s» (%.2f kg) para %s en periodo %s.",
                                describirAlcance(cuota), limHija, padre.getMacrozona().getNombre(), limPadre,
                                nombreEspecie(padre), padre.getPeriodo()));
                    }
                }
            }
        }

        // 5. Si la cuota es MACROZONA intermedia, validar contra la cuota NACIONAL
        if ("MACROZONA".equals(alcance)) {
            for (CuotaExtraccionModel padre : activas) {
                if ("NACIONAL".equals(alcanceDe(padre))
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)) {
                    BigDecimal limPadre = calcularLimiteEfectivo(padre, fEval);
                    if (limHija.compareTo(limPadre) > 0) {
                        throw new IllegalArgumentException(String.format(
                                "La cuota macrozonal «%s» (%.2f kg) no puede superar la cuota nacional (%.2f kg) para %s en periodo %s.",
                                cuota.getMacrozona().getNombre(), limHija, limPadre,
                                nombreEspecie(padre), padre.getPeriodo()));
                    }
                }
            }
        }
    }

    private void validarContraRegion(CuotaExtraccionModel cuota, List<CuotaExtraccionModel> activas,
                                     Long regionIdHija, String etiquetaHija, Date fEval, BigDecimal limHija) {
        if (regionIdHija == null) return;
        for (CuotaExtraccionModel padre : activas) {
            Long regionIdPadre = idRegionDe(padre);
            if ("REGION".equals(alcanceDe(padre))
                    && regionIdPadre != null
                    && regionIdHija.equals(regionIdPadre)
                    && seSolapan(cuota, padre) && especiesComparables(cuota, padre)) {
                BigDecimal limPadre = calcularLimiteEfectivo(padre, fEval);
                if (limHija.compareTo(limPadre) > 0) {
                    throw new IllegalArgumentException(String.format(
                            "La cuota %s (%.2f kg) no puede superar la cuota regional de %s (%.2f kg) para %s en periodo %s.",
                            etiquetaHija, limHija, nombreRegionDe(padre), limPadre, nombreEspecie(padre), padre.getPeriodo()));
                }
            }
        }
    }

    public Long idProvinciaDe(CuotaExtraccionModel c) {
        if (c == null) return null;
        if (c.getProvincia() != null && c.getProvincia().getId() != null) return c.getProvincia().getId();
        if (c.getComuna() != null && c.getComuna().getProvincia() != null) return c.getComuna().getProvincia().getId();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getProvincia() != null) return com.getProvincia().getId();
            }
        }
        return null;
    }

    public String nombreProvinciaDe(CuotaExtraccionModel c) {
        if (c == null) return "Provincia";
        if (c.getProvincia() != null && c.getProvincia().getNombre() != null) return c.getProvincia().getNombre();
        if (c.getComuna() != null && c.getComuna().getProvincia() != null) return c.getComuna().getProvincia().getNombre();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getProvincia() != null && com.getProvincia().getNombre() != null) {
                    return com.getProvincia().getNombre();
                }
            }
        }
        return "Provincia";
    }

    private Long idRegionDe(CuotaExtraccionModel c) {
        if (c.getRegion() != null) return c.getRegion().getId();
        if (c.getProvincia() != null && c.getProvincia().getRegion() != null) return c.getProvincia().getRegion().getId();
        if (c.getComuna() != null && c.getComuna().getRegion() != null) return c.getComuna().getRegion().getId();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getRegion() != null) return com.getRegion().getId();
            }
        }
        if (c.getAmerb() != null && c.getAmerb().getRegionModel() != null) return c.getAmerb().getRegionModel().getId();
        return null;
    }

    public String alcanceDe(CuotaExtraccionModel c) {
        if (Boolean.TRUE.equals(c.getEsPlantilla())) return "PLANTILLA";
        if (c.getUsuario() != null) return "USUARIO";
        if (c.getAmerb() != null) return "AREA";
        if (!idsComunas(c).isEmpty()) return "COMUNA";
        if (c.getProvincia() != null) return "PROVINCIA";
        if (c.getRegion() != null) return "REGION";
        if (c.getMacrozona() != null) {
            return Boolean.TRUE.equals(c.getMacrozona().getEsNacional()) ? "NACIONAL" : "MACROZONA";
        }
        return "GLOBAL";
    }

    public String nombreRegionDe(CuotaExtraccionModel c) {
        if (c.getRegion() != null) return c.getRegion().getNombre();
        if (c.getProvincia() != null && c.getProvincia().getRegion() != null) return c.getProvincia().getRegion().getNombre();
        if (c.getComuna() != null && c.getComuna().getRegion() != null) return c.getComuna().getRegion().getNombre();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getRegion() != null && com.getRegion().getNombre() != null) {
                    return com.getRegion().getNombre();
                }
            }
        }
        if (c.getAmerb() != null && c.getAmerb().getRegionModel() != null) return c.getAmerb().getRegionModel().getNombre();
        return null;
    }

    private String nombreEspecie(CuotaExtraccionModel c) {
        return c.getEspecie() != null ? c.getEspecie().getNombre() : "todas las especies";
    }

    private String nombreAmerb(CuotaExtraccionModel c) {
        return c.getAmerb() != null ? c.getAmerb().getNombre() : "(sin área)";
    }

    private boolean mismoPeriodo(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        return a.getPeriodo() != null && a.getPeriodo().equalsIgnoreCase(b.getPeriodo());
    }

    public boolean seSolapan(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null) return false;
        if (a.getFechaInicio() == null && a.getFechaFin() == null && b.getFechaInicio() == null && b.getFechaFin() == null) {
            return mismoPeriodo(a, b);
        }
        LocalDate aInicio = a.getFechaInicio() != null ? toLocalDateSafe(a.getFechaInicio()) : LocalDate.MIN;
        LocalDate aFin = a.getFechaFin() != null ? toLocalDateSafe(a.getFechaFin()) : LocalDate.MAX;
        LocalDate bInicio = b.getFechaInicio() != null ? toLocalDateSafe(b.getFechaInicio()) : LocalDate.MIN;
        LocalDate bFin = b.getFechaFin() != null ? toLocalDateSafe(b.getFechaFin()) : LocalDate.MAX;
        return !aInicio.isAfter(bFin) && !bInicio.isAfter(aFin);
    }

    private boolean especiesComparables(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        String ambA = a.getAmbito() != null ? a.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        String ambB = b.getAmbito() != null ? b.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        if (!ambA.equals(ambB)) return false;

        if (a.getEspecie() != null && b.getEspecie() != null) {
            if (!a.getEspecie().getId().equals(b.getEspecie().getId())) return false;
        }
        if (a.getExtraccionTipo() != null && b.getExtraccionTipo() != null) {
            if (!a.getExtraccionTipo().getId().equals(b.getExtraccionTipo().getId())) return false;
        }
        return true;
    }

    public Set<Long> idsComunas(CuotaExtraccionModel c) {
        if (c == null) return Collections.emptySet();
        Set<Long> ids = new LinkedHashSet<>();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getId() != null) {
                    ids.add(com.getId());
                }
            }
        }
        if (ids.isEmpty() && c.getComuna() != null && c.getComuna().getId() != null) {
            ids.add(c.getComuna().getId());
        }
        return ids;
    }

    public String describirAlcance(CuotaExtraccionModel cuota) {
        if (cuota == null) return "Global";
        AlcanceCuota estrategia = getAlcanceRegistry().resolver(cuota);
        if (estrategia != null) {
            return estrategia.describir(cuota);
        }
        return "Global";
    }

    public BigDecimal calcularConsumoAcumulado(CuotaExtraccionModel cuota, Date fechaEval) {
        if (cuota == null) return BigDecimal.ZERO;
        if (fechaEval == null) fechaEval = new Date();
        String perfil = cuota.getPerfil() != null ? cuota.getPerfil().toUpperCase() : "RECOLECTOR";
        Long targetUid = cuota.getUsuario() != null ? cuota.getUsuario().getId() : null;
        return ejecutarConsultaConsumo(cuota, fechaEval, perfil, targetUid);
    }

    public static class FiltroTerritorialCuota {
        private final String sqlFragment;
        private final Map<String, Object> parametros;

        public FiltroTerritorialCuota(String sqlFragment, Map<String, Object> parametros) {
            this.sqlFragment = sqlFragment;
            this.parametros = parametros;
        }

        public String getSqlFragment() {
            return sqlFragment;
        }

        public Map<String, Object> getParametros() {
            return parametros;
        }

        public void aplicarParametros(Query q) {
            for (Map.Entry<String, Object> entry : parametros.entrySet()) {
                q.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    public static LocalDate toLocalDateSafe(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public java.sql.Date[] calcularRangoFechas(CuotaExtraccionModel cuota, Date fechaEval) {
        LocalDate fechaLocal = toLocalDateSafe(fechaEval);
        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = toLocalDateSafe(cuota.getFechaInicio());
            end = toLocalDateSafe(cuota.getFechaFin());
        } else if ("MENSUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = fechaLocal.withDayOfMonth(1);
            end = fechaLocal.withDayOfMonth(fechaLocal.lengthOfMonth());
        } else if ("ANUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = fechaLocal.withDayOfYear(1);
            end = fechaLocal.withDayOfYear(fechaLocal.lengthOfYear());
        } else { // DIARIO
            start = fechaLocal;
            end = fechaLocal;
        }
        return new java.sql.Date[]{java.sql.Date.valueOf(start), java.sql.Date.valueOf(end)};
    }

    public FiltroTerritorialCuota construirFiltroTerritorial(CuotaExtraccionModel cuota, String tableName, Long targetUsuarioId) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        String modo = getModoImputacion();
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

        boolean esPlantilla = Boolean.TRUE.equals(cuota.getEsPlantilla());
        String nivelAgregacion = cuota.getNivelAgregacion() != null ? cuota.getNivelAgregacion().toUpperCase().trim() : "";

        if (nivelAgregacion.isEmpty()) {
            if (cuota.getUsuario() != null) nivelAgregacion = "INDIVIDUAL";
            else if (cuota.getAmerb() != null) nivelAgregacion = "AREA";
            else if (cuota.getComuna() != null) nivelAgregacion = "COMUNA";
            else if (cuota.getProvincia() != null) nivelAgregacion = "PROVINCIA";
            else if (cuota.getRegion() != null) nivelAgregacion = "REGION";
            else if (cuota.getMacrozona() != null) {
                nivelAgregacion = Boolean.TRUE.equals(cuota.getMacrozona().getEsNacional()) ? "NACIONAL" : "MACROZONA";
            } else {
                nivelAgregacion = "NACIONAL";
            }
        }

        if (esPlantilla || "INDIVIDUAL".equals(nivelAgregacion) || cuota.getUsuario() != null) {
            sql.append("WHERE d.usuario_id = :filtroUsuarioId ");
            Long uid = (targetUsuarioId != null) ? targetUsuarioId : (cuota.getUsuario() != null ? cuota.getUsuario().getId() : null);
            params.put("filtroUsuarioId", uid);
        } else if (cuota.getAmerb() != null && "declaracion_area".equals(tableName)) {
            sql.append("WHERE d.amerb_id = :filtroAmerbId ");
            params.put("filtroAmerbId", cuota.getAmerb().getId());
        } else {
            String actor = "declaracion_recolector".equals(tableName) ? "RECOLECTOR" : ("declaracion_armador".equals(tableName) ? "ARMADOR" : "AREA");
            ReglaImputacionTerritorial regla = getResolutorImputacion().resolver(actor);
            String sqlComuna = (regla != null) ? regla.sqlComuna("d", "u") : ("declaracion_recolector".equals(tableName) ? "COALESCE(u.comuna_id, d.comuna_id)" : "d.comuna_id");

            if (sqlComuna.contains("u.")) {
                sql.append("JOIN usuario u ON d.usuario_id = u.id ");
            }

            if ("COMUNA".equals(nivelAgregacion)) {
                Set<Long> cIds = idsComunas(cuota);
                if (cIds.isEmpty()) {
                    throw new IllegalStateException("Cuota nivel COMUNA sin comuna asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON c.id = ").append(sqlComuna).append(" WHERE c.id IN (:filtroComunaIds) ");
                params.put("filtroComunaIds", cIds);
            } else if ("PROVINCIA".equals(nivelAgregacion)) {
                if (cuota.getProvincia() == null) {
                    throw new IllegalStateException("Cuota nivel PROVINCIA sin provincia asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON c.id = ").append(sqlComuna).append(" WHERE c.provincia_id = :filtroProvinciaId ");
                params.put("filtroProvinciaId", cuota.getProvincia().getId());
            } else if ("REGION".equals(nivelAgregacion)) {
                if (cuota.getRegion() == null) {
                    throw new IllegalStateException("Cuota nivel REGION sin región asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON c.id = ").append(sqlComuna).append(" WHERE c.region_id = :filtroRegionId ");
                params.put("filtroRegionId", cuota.getRegion().getId());
            } else if ("MACROZONA".equals(nivelAgregacion)) {
                if (cuota.getMacrozona() == null) {
                    throw new IllegalStateException("Cuota nivel MACROZONA sin macrozona asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON c.id = ").append(sqlComuna).append(" ")
                   .append("JOIN macrozona_region mr ON c.region_id = mr.region_id ")
                   .append("  AND (mr.vigencia_inicio IS NULL OR d.").append(colFecha).append(" >= mr.vigencia_inicio) ")
                   .append("  AND (mr.vigencia_fin IS NULL OR d.").append(colFecha).append(" <= mr.vigencia_fin) ")
                   .append("WHERE mr.macrozona_id = :filtroMacrozonaId ");
                params.put("filtroMacrozonaId", cuota.getMacrozona().getId());
            } else if ("NACIONAL".equals(nivelAgregacion) || "GLOBAL".equals(nivelAgregacion)) {
                sql.append("WHERE 1=1 ");
            } else {
                throw new IllegalStateException("Nivel de agregación territorial desconocido: " + nivelAgregacion + " en cuota id=" + cuota.getId());
            }
        }

        return new FiltroTerritorialCuota(sql.toString(), params);
    }

    // =========================================================================
    // C5: CACHÉ CONCURRENTE L2 DE DOS FASES PARA CUOTAS DE AMPLIO ALCANCE
    // =========================================================================

    private static class CacheConsumoEntry {
        final BigDecimal consumo;
        final long timestamp;

        CacheConsumoEntry(BigDecimal consumo, long timestamp) {
            this.consumo = consumo;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, CacheConsumoEntry> cacheConsumoL2 = new ConcurrentHashMap<>();

    public void invalidarCacheConsumo() {
        cacheConsumoL2.clear();
    }

    public boolean contieneComuna(CuotaExtraccionModel cuota, Long comunaId) {
        if (comunaId == null) return true;
        if (cuota == null) return false;

        Set<Long> cIds = idsComunas(cuota);
        if (!cIds.isEmpty()) {
            return cIds.contains(comunaId);
        }

        Optional<ComunaModel> comOpt = comunaRepository.findById(comunaId);
        if (comOpt.isEmpty()) return false;
        ComunaModel com = comOpt.get();

        // Provincia
        if (cuota.getProvincia() != null) {
            return com.getProvincia() != null && cuota.getProvincia().getId().equals(com.getProvincia().getId());
        }

        // Región
        if (cuota.getRegion() != null) {
            return com.getRegion() != null && cuota.getRegion().getId().equals(com.getRegion().getId());
        }

        // Macrozona
        if (cuota.getMacrozona() != null) {
            if (Boolean.TRUE.equals(cuota.getMacrozona().getEsNacional())) return true;
            if (com.getRegion() == null) return false;
            return macrozonaService.isRegionInMacrozona(cuota.getMacrozona().getId(), com.getRegion().getId(), new Date());
        }

        // Si no tiene alcance territorial restringido (Global / Nacional)
        return cuota.getAmerb() == null && cuota.getUsuario() == null;
    }

    private BigDecimal ejecutarQueryConsumo(
            String tableName, String colMetrica, FiltroTerritorialCuota filtro, CuotaExtraccionModel cuota, java.sql.Date[] rango) {
        String modo = getModoImputacion();
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(d." + colMetrica + "), 0) FROM " + tableName + " d ");
        sql.append(filtro.getSqlFragment());
        if (cuota.getEspecie() != null) {
            sql.append("AND d.especie_id = :especieId ");
        }
        if (cuota.getExtraccionTipo() != null) {
            sql.append("AND d.extraccion_tipo_id = :extraccionTipoId ");
        }
        sql.append("AND d.").append(colFecha).append(" BETWEEN :startDate AND :endDate");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("startDate", rango[0]);
        q.setParameter("endDate", rango[1]);
        if (cuota.getEspecie() != null) q.setParameter("especieId", cuota.getEspecie().getId());
        if (cuota.getExtraccionTipo() != null) q.setParameter("extraccionTipoId", cuota.getExtraccionTipo().getId());
        filtro.aplicarParametros(q);

        Object singleResult = q.getSingleResult();
        return (singleResult != null) ? new BigDecimal(singleResult.toString()) : BigDecimal.ZERO;
    }

    private BigDecimal consumoAreaLibre(CuotaExtraccionModel cuota, Date fechaEval, Long targetUsuarioId) {
        java.sql.Date[] rango = calcularRangoFechas(cuota, fechaEval);
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        String colMetrica = esCaptura ? "captura" : "desembarque";

        // 1. Consumo de recolectores — imputación por comuna de inscripción del usuario (u.comuna_id)
        FiltroTerritorialCuota filtroRecolector = construirFiltroTerritorial(cuota, "declaracion_recolector", targetUsuarioId);
        BigDecimal consumoRecolector = ejecutarQueryConsumo(
                "declaracion_recolector", colMetrica, filtroRecolector, cuota, rango);

        // 2. Consumo de armadores — imputación por caleta de desembarque (d.comuna_id)
        FiltroTerritorialCuota filtroArmador = construirFiltroTerritorial(cuota, "declaracion_armador", targetUsuarioId);
        BigDecimal consumoArmador = ejecutarQueryConsumo(
                "declaracion_armador", colMetrica, filtroArmador, cuota, rango);

        return consumoRecolector.add(consumoArmador);
    }

    private BigDecimal consumoTablaUnica(CuotaExtraccionModel cuota, Date fechaEval, String perfil, Long targetUsuarioId) {
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        String colMetrica = esCaptura ? "captura" : "desembarque";
        String tableName = "declaracion_recolector";
        if ("ARMADOR".equalsIgnoreCase(perfil)) tableName = "declaracion_armador";
        if ("AREA".equalsIgnoreCase(perfil) || "ÁREA DE MANEJO".equalsIgnoreCase(perfil) || cuota.getAmerb() != null || "AMERB".equalsIgnoreCase(cuota.getAmbito())) {
            tableName = "declaracion_area";
        }

        java.sql.Date[] rango = calcularRangoFechas(cuota, fechaEval);
        FiltroTerritorialCuota filtro = construirFiltroTerritorial(cuota, tableName, targetUsuarioId);
        return ejecutarQueryConsumo(tableName, colMetrica, filtro, cuota, rango);
    }

    public BigDecimal ejecutarConsultaConsumo(CuotaExtraccionModel cuota, Date fechaEval, String perfil, Long targetUsuarioId) {
        String alcance = alcanceDe(cuota);
        boolean esAlcanceAmplio = "MACROZONA".equals(alcance) || "NACIONAL".equals(alcance) || "REGION".equals(alcance) || "GLOBAL".equals(alcance);

        boolean esAreaLibre = "AREA_LIBRE".equalsIgnoreCase(cuota.getAmbito()) || (cuota.getAmbito() == null && cuota.getAmerb() == null && !"AREA".equalsIgnoreCase(cuota.getPerfil()));

        String modoImputacion = getModoImputacion();
        String cachePerfil = esAreaLibre ? "AREALIBRE" : (perfil != null ? perfil : "DEFAULT");
        String cacheKey = (cuota.getId() != null ? cuota.getId() : 0L) + "_" +
                          (fechaEval != null ? fechaEval.getTime() / 86400000L : 0L) + "_" +
                          cachePerfil + "_" +
                          modoImputacion + "_" +
                          (targetUsuarioId != null ? targetUsuarioId : "ALL");

        long ahora = System.currentTimeMillis();
        BigDecimal limiteEfectivo = (cuota.getLimiteKg() != null) ? BigDecimal.valueOf(cuota.getLimiteKg()) : BigDecimal.ZERO;

        if (esAlcanceAmplio) {
            CacheConsumoEntry cached = cacheConsumoL2.get(cacheKey);
            if (cached != null && (ahora - cached.timestamp < 30_000)) {
                // Zona operativa normal (< 90%): responder desde memoria sin colapso de base de datos
                BigDecimal umbral90 = limiteEfectivo.multiply(new BigDecimal("0.90"));
                if (limiteEfectivo.compareTo(BigDecimal.ZERO) == 0 || cached.consumo.compareTo(umbral90) < 0) {
                    return cached.consumo;
                }
            }
        }

        BigDecimal consumo;
        if (esAreaLibre) {
            consumo = consumoAreaLibre(cuota, fechaEval, targetUsuarioId);
        } else {
            consumo = consumoTablaUnica(cuota, fechaEval, perfil, targetUsuarioId);
        }

        if (esAlcanceAmplio) {
            cacheConsumoL2.put(cacheKey, new CacheConsumoEntry(consumo, ahora));
        }

        return consumo;
    }

    public BigDecimal calcularLimiteEfectivo(CuotaExtraccionModel cuota, Date fechaEval) {
        if (cuota == null || cuota.getLimiteKg() == null) return BigDecimal.ZERO;
        BigDecimal limiteEfectivo = BigDecimal.valueOf(cuota.getLimiteKg());
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        if (cuota.getHumedadEstado() != null && cuota.getEspecie() != null && esCaptura) {
            Optional<FactorConversionModel> factorOpt = factorConversionService.findFactorVigente(
                    cuota.getEspecie().getId(), cuota.getHumedadEstado().getId(), fechaEval);
            if (factorOpt.isPresent()) {
                limiteEfectivo = limiteEfectivo.multiply(factorOpt.get().getFactor()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return limiteEfectivo;
    }

    public java.util.Map<String, Object> getConsumoCuota(Long id) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada con id: " + id));
        Date now = new Date();
        LocalDate nowLocal = toLocalDateSafe(now);
        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = toLocalDateSafe(cuota.getFechaInicio());
            end = toLocalDateSafe(cuota.getFechaFin());
        } else if ("MENSUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = nowLocal.withDayOfMonth(1);
            end = nowLocal.withDayOfMonth(nowLocal.lengthOfMonth());
        } else if ("ANUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = nowLocal.withDayOfYear(1);
            end = nowLocal.withDayOfYear(nowLocal.lengthOfYear());
        } else {
            start = nowLocal;
            end = nowLocal;
        }

        BigDecimal limiteEfectivo = calcularLimiteEfectivo(cuota, now);
        BigDecimal consumido = calcularConsumoAcumulado(cuota, now);
        BigDecimal disponible = limiteEfectivo.subtract(consumido);
        if (disponible.compareTo(BigDecimal.ZERO) < 0) disponible = BigDecimal.ZERO;

        BigDecimal pctConsumido = BigDecimal.ZERO;
        BigDecimal pctRestante = BigDecimal.ZERO;
        if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
            pctConsumido = consumido.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP);
            pctRestante = disponible.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP);
        }

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("cuotaId", cuota.getId());
        res.put("limiteKg", cuota.getLimiteKg());
        res.put("limiteEfectivoKg", limiteEfectivo);
        res.put("consumidoKg", consumido);
        res.put("disponibleKg", disponible);
        res.put("pctConsumido", pctConsumido);
        res.put("pctRestante", pctRestante);
        res.put("estado", cuota.getEstado());
        res.put("fechaCierre", cuota.getFechaCierre());
        return res;
    }

    public List<CuotaListadoDTO> getListado(Integer anio, Integer mes, Long comunaId, Long extraccionTipoId, String ambito) {
        String ambitoNorm = (ambito != null && !ambito.isBlank()) ? ambito.trim().toUpperCase() : "AREA_LIBRE";
        List<CuotaExtraccionModel> todas = cuotaRepository.findAll();

        List<CuotaListadoDTO> result = new ArrayList<>();
        Date now = new Date();
        LocalDate nowLocal = toLocalDateSafe(now);

        String[] nombresMeses = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                                 "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

        for (CuotaExtraccionModel c : todas) {
            String cAmbito = c.getAmbito() != null ? c.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
            if (!"TODOS".equals(ambitoNorm)) {
                if ("AMERB".equals(ambitoNorm)) {
                    if (!"AMERB".equals(cAmbito) && c.getAmerb() == null && !"AREA".equalsIgnoreCase(c.getPerfil())) {
                        continue;
                    }
                } else if ("AREA_LIBRE".equals(ambitoNorm)) {
                    if ("AMERB".equals(cAmbito) || c.getAmerb() != null || "AREA".equalsIgnoreCase(c.getPerfil())) {
                        continue;
                    }
                } else {
                    if (!ambitoNorm.equals(cAmbito)) {
                        continue;
                    }
                }
            }

            // Filtro año
            if (anio != null && c.getFechaInicio() != null) {
                LocalDate ini = toLocalDateSafe(c.getFechaInicio());
                LocalDate fin = c.getFechaFin() != null ? toLocalDateSafe(c.getFechaFin()) : ini;
                if (anio < ini.getYear() || anio > fin.getYear()) {
                    continue;
                }
            }

            // Filtro mes
            if (mes != null && mes >= 1 && mes <= 12 && c.getFechaInicio() != null) {
                LocalDate ini = toLocalDateSafe(c.getFechaInicio());
                LocalDate fin = c.getFechaFin() != null ? toLocalDateSafe(c.getFechaFin()) : ini;
                int targetAnio = (anio != null) ? anio : ini.getYear();
                LocalDate mesInicio = LocalDate.of(targetAnio, mes, 1);
                LocalDate mesFin = mesInicio.withDayOfMonth(mesInicio.lengthOfMonth());
                if (ini.isAfter(mesFin) || fin.isBefore(mesInicio)) {
                    continue;
                }
            }

            // Filtro comuna
            if (comunaId != null) {
                if (!contieneComuna(c, comunaId)) {
                    continue;
                }
            }

            // Filtro tipo extracción
            if (extraccionTipoId != null) {
                if (c.getExtraccionTipo() == null || !c.getExtraccionTipo().getId().equals(extraccionTipoId)) {
                    continue;
                }
            }

            // Determinar si es formato anterior
            boolean esFormatoAnt = esFormatoAnterior(c);

            // Calcular fecha de evaluación para el consumo
            Date fechaEval = now;
            if (c.getFechaFin() != null) {
                LocalDate fin = toLocalDateSafe(c.getFechaFin());
                if (nowLocal.isAfter(fin)) {
                    fechaEval = c.getFechaFin();
                } else if (c.getFechaInicio() != null && nowLocal.isBefore(toLocalDateSafe(c.getFechaInicio()))) {
                    fechaEval = c.getFechaInicio();
                }
            }

            BigDecimal limiteNominal = (c.getLimiteKg() != null) ? BigDecimal.valueOf(c.getLimiteKg()) : BigDecimal.ZERO;
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(c, fechaEval);
            BigDecimal factor = BigDecimal.ONE;
            if (c.getHumedadEstado() != null && c.getEspecie() != null && !"DESEMBARQUE".equalsIgnoreCase(c.getMetrica())) {
                factor = factorConversionService.findFactorVigente(c.getEspecie().getId(), c.getHumedadEstado().getId(), fechaEval)
                        .map(FactorConversionModel::getFactor)
                        .orElse(BigDecimal.ONE);
            }

            BigDecimal consumo = BigDecimal.ZERO;
            try {
                consumo = ejecutarConsultaConsumo(c, fechaEval, c.getPerfil(), (c.getUsuario() != null ? c.getUsuario().getId() : null));
            } catch (Exception e) {
                log.warn("No se pudo calcular consumo para cuota id={}: {}", c.getId(), e.getMessage());
            }

            BigDecimal saldo = limiteEfectivo.subtract(consumo);
            if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;

            double pct = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                    ? (consumo.doubleValue() / limiteEfectivo.doubleValue()) * 100.0
                    : 0.0;

            String vigenciaDesc = formatearVigencia(c, nombresMeses);

            Set<Long> cIds = idsComunas(c);
            List<String> comNombres = new ArrayList<>();
            if (c.getComunas() != null && !c.getComunas().isEmpty()) {
                comNombres = c.getComunas().stream()
                        .map(cm -> cm.getNombre() != null ? cm.getNombre() : ("Comuna " + cm.getId()))
                        .toList();
            } else if (c.getComuna() != null) {
                comNombres = List.of(c.getComuna().getNombre() != null ? c.getComuna().getNombre() : ("Comuna " + c.getComuna().getId()));
            }
            String comunasNombre = String.join(" + ", comNombres);

            CuotaListadoDTO dto = CuotaListadoDTO.builder()
                    .id(c.getId())
                    .ambito(cAmbito)
                    .perfil(c.getPerfil())
                    .nivelAgregacion(c.getNivelAgregacion())
                    .regionId(c.getRegion() != null ? c.getRegion().getId() : (c.getComuna() != null && c.getComuna().getRegion() != null ? c.getComuna().getRegion().getId() : null))
                    .regionNombre(nombreRegionDe(c))
                    .provinciaId(c.getProvincia() != null ? c.getProvincia().getId() : null)
                    .provinciaNombre(nombreProvinciaDe(c))
                    .esPlantilla(Boolean.TRUE.equals(c.getEsPlantilla()))
                    .usuarioId(c.getUsuario() != null ? c.getUsuario().getId() : null)
                    .usuarioNombre(c.getUsuario() != null ? ((c.getUsuario().getNombres() != null ? c.getUsuario().getNombres() : "") + " " + (c.getUsuario().getApellidop() != null ? c.getUsuario().getApellidop() : "")).trim() : null)
                    .comunaId(c.getComuna() != null ? c.getComuna().getId() : (!cIds.isEmpty() ? cIds.iterator().next() : null))
                    .comunaNombre(c.getComuna() != null ? c.getComuna().getNombre() : (!comNombres.isEmpty() ? comNombres.get(0) : null))
                    .comunaIds(cIds)
                    .comunaNombres(comNombres)
                    .comunasNombre(comunasNombre)
                    .especieId(c.getEspecie() != null ? c.getEspecie().getId() : null)
                    .especieNombre(c.getEspecie() != null ? c.getEspecie().getNombre() : "Sin especie")
                    .extraccionTipoId(c.getExtraccionTipo() != null ? c.getExtraccionTipo().getId() : null)
                    .extraccionTipoNombre(c.getExtraccionTipo() != null ? c.getExtraccionTipo().getNombre() : "Todos los métodos")
                    .periodo(c.getPeriodo())
                    .fechaInicio(c.getFechaInicio())
                    .fechaFin(c.getFechaFin())
                    .vigenciaDescripcion(vigenciaDesc)
                    .limiteKg(c.getLimiteKg())
                    .limiteNominal(limiteNominal)
                    .limiteEfectivo(limiteEfectivo)
                    .consumoAcumulado(consumo)
                    .porcentajeUso(Math.round(pct * 10.0) / 10.0)
                    .saldoDisponible(saldo)
                    .metrica(c.getMetrica() != null ? c.getMetrica() : "CAPTURA")
                    .humedadEstadoId(c.getHumedadEstado() != null ? c.getHumedadEstado().getId() : null)
                    .humedadEstadoNombre(c.getHumedadEstado() != null ? c.getHumedadEstado().getNombre() : "Sin conversión")
                    .factorConversion(factor)
                    .modoAccion(c.getModoAccion() != null ? c.getModoAccion() : "SOLO_ALERTA")
                    .resolucion(c.getResolucion())
                    .estado(c.getEstado() != null ? c.getEstado() : "ABIERTA")
                    .fechaCierre(c.getFechaCierre())
                    .motivoCierre(c.getMotivoCierre())
                    .activo(c.getActivo())
                    .alcance(describirAlcance(c))
                    .esFormatoAnterior(esFormatoAnt)
                    .build();

            if (Boolean.TRUE.equals(c.getEsPlantilla()) && consumoIndividualQuery != null) {
                try {
                    ResumenPlantillaDTO resumen = consumoIndividualQuery.resumenPlantilla(c, fechaEval);
                    dto.setPersonasConActividad(resumen.getPersonasConActividad());
                    dto.setPersonasSobreLimite(resumen.getPersonasSobreLimite());
                    dto.setMaxPorcentaje(resumen.getMaxPorcentaje());
                    dto.setTextoConsumoPlantilla(resumen.getTextoConsumo());
                    dto.setConsumoAcumulado(resumen.getConsumoTotal());
                    if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0 && resumen.getConsumoTotal() != null) {
                        double pctPlantilla = (resumen.getConsumoTotal().doubleValue() / limiteEfectivo.doubleValue()) * 100.0;
                        dto.setPorcentajeUso(Math.round(pctPlantilla * 10.0) / 10.0);
                    }
                } catch (Exception e) {
                    log.warn("Error calculando resumen de plantilla para cuota id={}: {}", c.getId(), e.getMessage());
                }
            }

            result.add(dto);
        }

        result.sort((a, b) -> {
            int cmpAnt = Boolean.compare(Boolean.TRUE.equals(a.getEsFormatoAnterior()), Boolean.TRUE.equals(b.getEsFormatoAnterior()));
            if (cmpAnt != 0) return cmpAnt;
            if (a.getFechaInicio() != null && b.getFechaInicio() != null) {
                int cmpFecha = b.getFechaInicio().compareTo(a.getFechaInicio());
                if (cmpFecha != 0) return cmpFecha;
            } else if (a.getFechaInicio() != null) {
                return -1;
            } else if (b.getFechaInicio() != null) {
                return 1;
            }
            Long idA = a.getId() != null ? a.getId() : 0L;
            Long idB = b.getId() != null ? b.getId() : 0L;
            return idB.compareTo(idA);
        });

        return result;
    }

    public boolean esFormatoAnterior(CuotaExtraccionModel c) {
        if (c == null) return false;
        if (c.getMacrozona() != null) return true;
        String nivel = c.getNivelAgregacion() != null ? c.getNivelAgregacion().trim().toUpperCase() : "";
        if ("MACROZONA".equals(nivel) || "NACIONAL".equals(nivel)) return true;
        String periodo = c.getPeriodo() != null ? c.getPeriodo().trim().toUpperCase() : "";
        if (!"MENSUAL".equals(periodo)) return true;
        if (c.getFechaInicio() == null || c.getFechaFin() == null) return true;
        return false;
    }

    private String formatearVigencia(CuotaExtraccionModel c, String[] nombresMeses) {
        if (c.getFechaInicio() == null) {
            return c.getPeriodo() != null ? c.getPeriodo() : "Sin vigencia";
        }
        LocalDate ini = toLocalDateSafe(c.getFechaInicio());
        LocalDate fin = c.getFechaFin() != null ? toLocalDateSafe(c.getFechaFin()) : null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        if ("MENSUAL".equalsIgnoreCase(c.getPeriodo()) && fin != null && ini.getMonthValue() == fin.getMonthValue() && ini.getYear() == fin.getYear()) {
            String mesNombre = nombresMeses[ini.getMonthValue()] + " " + ini.getYear();
            if (ini.getDayOfMonth() == 1 && fin.getDayOfMonth() == fin.lengthOfMonth()) {
                return mesNombre;
            } else {
                return String.format("%s (%02d al %02d)", mesNombre, ini.getDayOfMonth(), fin.getDayOfMonth());
            }
        }
        if (fin != null) {
            return ini.format(dtf) + " al " + fin.format(dtf);
        }
        return "Desde " + ini.format(dtf);
    }

    public static class QuotaCheckResult {
        private boolean allowed;
        private String message;

        public QuotaCheckResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
    }
}