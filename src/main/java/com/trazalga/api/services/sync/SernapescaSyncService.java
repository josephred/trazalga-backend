package com.trazalga.api.services.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.sernapesca.AmIdentificacionDto;
import com.trazalga.api.dto.sernapesca.CaletaDto;
import com.trazalga.api.dto.sernapesca.ComboIntDto;
import com.trazalga.api.dto.sernapesca.ComunaTreeDto;
import com.trazalga.api.dto.sernapesca.DestinatarioDto;
import com.trazalga.api.dto.sernapesca.EmbarcacionDto;
import com.trazalga.api.dto.sernapesca.MetodoRecoleccionDto;
import com.trazalga.api.dto.sernapesca.PescadorDto;
import com.trazalga.api.dto.sernapesca.RegionTreeDto;
import com.trazalga.api.dto.sync.SyncResult;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.PlantaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IPlantaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

/**
 * Pobla las tablas de datos maestros a partir del API de Sernapesca.
 * Todas las operaciones son idempotentes (upsert por clave natural): pueden
 * re-ejecutarse sin generar duplicados.
 */
@Service
public class SernapescaSyncService {

    private static final Logger log = LoggerFactory.getLogger(SernapescaSyncService.class);

    private final SernapescaApiClient api;
    private final IRegionRepository regionRepo;
    private final IComunaRepository comunaRepo;
    private final ICaletaRepository caletaRepo;
    private final IEspecieRepository especieRepo;
    private final IExtraccionTipoRepository extraccionTipoRepo;
    private final IEmbarcacionRepository embarcacionRepo;
    private final IBuzoRepository buzoRepo;
    private final IAmerbRepository amerbRepo;
    private final IPlantaRepository plantaRepo;
    private final IUsuarioRepository usuarioRepo;

    /** Códigos de región (CSV) a sincronizar para buzos. Por defecto solo la 4 (Coquimbo). */
    @Value("${trazalga.sync.buzos.regiones:4}")
    private String regionesBuzosCsv;

    /** Códigos de región (CSV) a sincronizar para embarcaciones. Por defecto solo la 4 (Coquimbo). */
    @Value("${trazalga.sync.embarcaciones.regiones:4}")
    private String regionesEmbarcacionesCsv;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private SernapescaSyncService self;

    public SernapescaSyncService(SernapescaApiClient api,
            IRegionRepository regionRepo, IComunaRepository comunaRepo, ICaletaRepository caletaRepo,
            IEspecieRepository especieRepo, IExtraccionTipoRepository extraccionTipoRepo,
            IEmbarcacionRepository embarcacionRepo, IBuzoRepository buzoRepo, IAmerbRepository amerbRepo,
            IPlantaRepository plantaRepo, IUsuarioRepository usuarioRepo) {
        this.api = api;
        this.regionRepo = regionRepo;
        this.comunaRepo = comunaRepo;
        this.caletaRepo = caletaRepo;
        this.especieRepo = especieRepo;
        this.extraccionTipoRepo = extraccionTipoRepo;
        this.embarcacionRepo = embarcacionRepo;
        this.buzoRepo = buzoRepo;
        this.amerbRepo = amerbRepo;
        this.plantaRepo = plantaRepo;
        this.usuarioRepo = usuarioRepo;
    }

    // ------------------------------------------------------------------
    // Orquestador
    // ------------------------------------------------------------------

    /** Pobla todas las entidades disponibles desde Sernapesca. */
    public List<SyncResult> syncAll() {
        List<SyncResult> results = new ArrayList<>();
        results.addAll(syncRegionesComunasCaletas());
        results.add(syncTiposExtraccion());
        results.add(syncEspecies());
        results.add(syncEmbarcaciones());
        results.add(syncBuzos());
        results.add(syncAmerbs());
        results.add(syncPlantas());
        results.add(syncUsuarioEmbarcaciones(true));
        // Sin fuente conocida en el API de Sernapesca:
        results.add(SyncResult.error("composicion",
                "No existe un endpoint equivalente en el API de Sernapesca. Poblar manualmente."));
        results.add(SyncResult.error("usuarios",
                "Las cuentas de usuario son propias de la app (rut/clave/perfil) y no provienen de Sernapesca."));
        return results;
    }

    // ------------------------------------------------------------------
    // Región / Comuna / Caleta (jerárquico)
    // ------------------------------------------------------------------

    @Transactional
    public List<SyncResult> syncRegionesComunasCaletas() {
        List<RegionTreeDto> tree = api.getRegionComunaCaleta();

        Map<String, RegionModel> regionByName = new HashMap<>();
        for (RegionModel r : regionRepo.findAll()) {
            regionByName.put(norm(r.getNombre()), r);
        }
        Map<String, ComunaModel> comunaByKey = new HashMap<>();
        for (ComunaModel c : comunaRepo.findAll()) {
            comunaByKey.put(comunaKey(c.getNombre(), c.getRegion() != null ? c.getRegion().getId() : null), c);
        }
        Map<String, CaletaModel> caletaByKey = new HashMap<>();
        for (CaletaModel c : caletaRepo.findAll()) {
            caletaByKey.put(comunaKey(c.getNombre(), c.getRegion() != null ? c.getRegion().getId() : null), c);
        }

        int regObt = 0, regIns = 0, regOmit = 0;
        int comObt = 0, comIns = 0, comOmit = 0;
        int calObt = 0, calIns = 0, calOmit = 0;
        List<ComunaModel> nuevasComunas = new ArrayList<>();
        List<CaletaModel> nuevasCaletas = new ArrayList<>();

        for (RegionTreeDto rt : tree) {
            if (isBlank(rt.getNombreRegion())) {
                continue;
            }
            regObt++;
            RegionModel region = regionByName.get(norm(rt.getNombreRegion()));
            if (region == null) {
                region = regionRepo.save(new RegionModel().setNombre(rt.getNombreRegion().trim()));
                regionByName.put(norm(region.getNombre()), region);
                regIns++;
            } else {
                regOmit++;
            }

            if (rt.getComunas() == null) {
                continue;
            }
            for (ComunaTreeDto ct : rt.getComunas()) {
                if (!isBlank(ct.getNombreComuna())) {
                    comObt++;
                    String ck = comunaKey(ct.getNombreComuna(), region.getId());
                    ComunaModel comuna = comunaByKey.get(ck);
                    if (comuna == null) {
                        comuna = new ComunaModel().setNombre(ct.getNombreComuna().trim()).setRegion(region);
                        nuevasComunas.add(comuna);
                        comunaByKey.put(ck, comuna);
                        comIns++;
                    } else {
                        comOmit++;
                    }
                }
                if (ct.getCaletas() == null) {
                    continue;
                }
                for (CaletaDto cal : ct.getCaletas()) {
                    if (isBlank(cal.getNombreCaleta())) {
                        continue;
                    }
                    calObt++;
                    String calKey = comunaKey(cal.getNombreCaleta(), region.getId());
                    CaletaModel caleta = caletaByKey.get(calKey);
                    
                    ComunaModel comuna = null;
                    if (!isBlank(ct.getNombreComuna())) {
                        String ck = comunaKey(ct.getNombreComuna(), region.getId());
                        comuna = comunaByKey.get(ck);
                    }

                    if (caleta == null) {
                        caleta = new CaletaModel().setNombre(cal.getNombreCaleta().trim()).setRegion(region).setComuna(comuna);
                        nuevasCaletas.add(caleta);
                        caletaByKey.put(calKey, caleta);
                        calIns++;
                    } else {
                        if (caleta.getComuna() == null && comuna != null) {
                            caleta.setComuna(comuna);
                            nuevasCaletas.add(caleta);
                        } else {
                            calOmit++;
                        }
                    }
                }
            }
        }

        comunaRepo.saveAll(nuevasComunas);
        caletaRepo.saveAll(nuevasCaletas);

        List<SyncResult> out = new ArrayList<>();
        out.add(SyncResult.builder().entidad("region").ok(true)
                .obtenidos(regObt).insertados(regIns).actualizados(0).omitidos(regOmit).build());
        out.add(SyncResult.builder().entidad("comuna").ok(true)
                .obtenidos(comObt).insertados(comIns).actualizados(0).omitidos(comOmit).build());
        out.add(SyncResult.builder().entidad("caleta").ok(true)
                .obtenidos(calObt).insertados(calIns).actualizados(0).omitidos(calOmit).build());
        return out;
    }

    // ------------------------------------------------------------------
    // Tipos de extracción (métodos de recolección)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncTiposExtraccion() {
        List<MetodoRecoleccionDto> metodos = api.getMetodosRecoleccion();
        Set<String> existentes = new HashSet<>();
        for (ExtraccionTipoModel t : extraccionTipoRepo.findAll()) {
            existentes.add(norm(t.getNombre()));
        }
        List<ExtraccionTipoModel> nuevos = new ArrayList<>();
        int ins = 0, omit = 0;
        for (MetodoRecoleccionDto m : metodos) {
            if (isBlank(m.getNombre())) {
                continue;
            }
            if (existentes.add(norm(m.getNombre()))) {
                nuevos.add(new ExtraccionTipoModel().setNombre(m.getNombre().trim()));
                ins++;
            } else {
                omit++;
            }
        }
        extraccionTipoRepo.saveAll(nuevos);
        return SyncResult.builder().entidad("extraccion_tipo").ok(true)
                .obtenidos(metodos.size()).insertados(ins).actualizados(0).omitidos(omit).build();
    }

    // ------------------------------------------------------------------
    // Especies (autorizadas para recolección de orilla)
    // ------------------------------------------------------------------

    /**
     * Especies vigentes de la pesquería actual (algas pardas): quedan visibles en
     * los selectores. Las demás que traiga Sernapesca se crean OCULTAS (activo=false):
     * disponibles en BD por si el proyecto escala a otras pesquerías, sin saturar la vista.
     */
    private static final Set<String> ESPECIES_VISIBLES = Set.of("HUIRO PALO", "HUIRO NEGRO", "HUIRO", "COCHAYUYO");

    @Transactional
    public SyncResult syncEspecies() {
        List<ComboIntDto> especies = api.getEspeciesAutorizadas();
        Set<String> existentes = new HashSet<>();
        for (EspecieModel es : especieRepo.findAll()) {
            existentes.add(norm(es.getNombre()));
        }
        List<EspecieModel> nuevas = new ArrayList<>();
        int ins = 0, omit = 0;
        Set<Integer> permitidos = Set.of(145, 141, 142, 110, 130, 150, 138, 137, 136, 122, 125, 120, 116, 115, 165, 225);
        
        for (ComboIntDto es : especies) {
            if (es.getCodigo() == null || !permitidos.contains(es.getCodigo())) {
                continue;
            }
            if (isBlank(es.getValor())) {
                continue;
            }
            if (existentes.add(norm(es.getValor()))) {
                nuevas.add(new EspecieModel()
                        .setNombre(es.getValor().trim())
                        .setActivo(ESPECIES_VISIBLES.contains(norm(es.getValor()))));
                ins++;
            } else {
                omit++;
            }
        }
        
        // Garantizar que "Huiro Palo" sea el primer elemento guardado para que obtenga ID 1
        nuevas.sort((a, b) -> {
            boolean aIsHuiroPalo = a.getNombre().toUpperCase().contains("HUIRO PALO");
            boolean bIsHuiroPalo = b.getNombre().toUpperCase().contains("HUIRO PALO");
            if (aIsHuiroPalo && !bIsHuiroPalo) return -1;
            if (!aIsHuiroPalo && bIsHuiroPalo) return 1;
            return a.getNombre().compareTo(b.getNombre());
        });
        
        especieRepo.saveAll(nuevas);
        return SyncResult.builder().entidad("especie").ok(true)
                .obtenidos(especies.size()).insertados(ins).actualizados(0).omitidos(omit).build();
    }

    // ------------------------------------------------------------------
    // Embarcaciones (por región)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncEmbarcaciones() {
        Map<String, EmbarcacionModel> existentePorCodigo = new HashMap<>();
        for (EmbarcacionModel e : embarcacionRepo.findAll()) {
            if (e.getCodigo() != null) {
                existentePorCodigo.put(e.getCodigo(), e);
            }
        }
        List<EmbarcacionModel> nuevas = new ArrayList<>();
        List<EmbarcacionModel> actualizadas = new ArrayList<>();
        int obt = 0, ins = 0, act = 0, omit = 0;
        for (Integer cod : regionesEmbarcaciones()) {
            for (EmbarcacionDto d : api.getEmbarcacionesPorRegion(cod)) {
                obt++;
                if (d.getFolioRpa() == null || isBlank(d.getNombreNave())) {
                    omit++;
                    continue;
                }
                String codigo = String.valueOf(d.getFolioRpa());
                String nombre = d.getNombreNave().trim();

                // El identificador confiable es el código (folioRpa): el nombre de nave NO es
                // único a nivel nacional, dos embarcaciones de regiones distintas pueden
                // compartirlo (ver EmbarcacionModel), así que ya no se usa para deduplicar.
                EmbarcacionModel existente = existentePorCodigo.get(codigo);
                if (existente != null) {
                    if (existente.getCodigoRegion() == null) {
                        existente.setCodigoRegion(cod);
                        actualizadas.add(existente);
                        act++;
                    } else {
                        omit++;
                    }
                    continue;
                }
                EmbarcacionModel nueva = new EmbarcacionModel().setNombre(nombre).setCodigo(codigo).setCodigoRegion(cod);
                nuevas.add(nueva);
                existentePorCodigo.put(codigo, nueva);
                ins++;
            }
        }
        embarcacionRepo.saveAll(nuevas);
        embarcacionRepo.saveAll(actualizadas);
        return SyncResult.builder().entidad("embarcacion").ok(true)
                .obtenidos(obt).insertados(ins).actualizados(act).omitidos(omit).build();
    }

    /** Códigos de región a sincronizar para embarcaciones (configurable; por defecto solo la 4). */
    private List<Integer> regionesEmbarcaciones() {
        List<Integer> out = new ArrayList<>();
        if (regionesEmbarcacionesCsv != null) {
            for (String s : regionesEmbarcacionesCsv.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    try {
                        out.add(Integer.parseInt(t));
                    } catch (NumberFormatException e) {
                        log.warn("Código de región inválido en trazalga.sync.embarcaciones.regiones: '{}'", t);
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Buzos / recolectores (por región)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncBuzos() {
        Set<String> codigos = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        Map<String, BuzoModel> existentePorCodigo = new HashMap<>();
        for (BuzoModel b : buzoRepo.findAll()) {
            if (b.getCodigo() != null) {
                codigos.add(b.getCodigo());
                existentePorCodigo.put(b.getCodigo(), b);
            }
            nombres.add(norm(b.getNombre()));
        }
        List<BuzoModel> nuevos = new ArrayList<>();
        List<BuzoModel> actualizados = new ArrayList<>();
        int obt = 0, ins = 0, act = 0, omit = 0;
        for (Integer cod : regionesBuzos()) {
            for (PescadorDto d : api.getRecolectoresPorRegion(cod)) {
                obt++;
                String codigo = !isBlank(d.getRutCompleto()) ? d.getRutCompleto().trim()
                        : (d.getFolioRpa() != null ? String.valueOf(d.getFolioRpa()) : null);
                if (codigo == null || isBlank(d.getNombreCompleto())) {
                    omit++;
                    continue;
                }
                String nombre = d.getNombreCompleto().trim();
                if (!codigos.add(codigo) || !nombres.add(norm(nombre))) {
                    // Ya existe: etiquetar su región si aún no la tiene (para filas previas sin región).
                    BuzoModel existente = existentePorCodigo.get(codigo);
                    if (existente != null && existente.getCodigoRegion() == null) {
                        existente.setCodigoRegion(cod);
                        actualizados.add(existente);
                        act++;
                    } else {
                        omit++;
                    }
                    continue;
                }
                nuevos.add(new BuzoModel().setNombre(nombre).setCodigo(codigo).setCodigoRegion(cod));
                ins++;
            }
        }
        buzoRepo.saveAll(nuevos);
        buzoRepo.saveAll(actualizados);
        return SyncResult.builder().entidad("buzo").ok(true)
                .obtenidos(obt).insertados(ins).actualizados(act).omitidos(omit).build();
    }

    /** Códigos de región a sincronizar para buzos (configurable; por defecto solo la 4). */
    private List<Integer> regionesBuzos() {
        List<Integer> out = new ArrayList<>();
        if (regionesBuzosCsv != null) {
            for (String s : regionesBuzosCsv.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    try {
                        out.add(Integer.parseInt(t));
                    } catch (NumberFormatException e) {
                        log.warn("Código de región inválido en trazalga.sync.buzos.regiones: '{}'", t);
                    }
                }
            }
        }
        if (out.isEmpty()) {
            out.add(4);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // AMERB / áreas de manejo (por región)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncAmerbs() {
        Set<String> codigos = new HashSet<>();
        for (AmerbModel a : amerbRepo.findAll()) {
            if (a.getCodigoSernapesca() != null) {
                codigos.add(a.getCodigoSernapesca());
            }
        }
        List<AmerbModel> nuevas = new ArrayList<>();
        int obt = 0, ins = 0, omit = 0;
        for (Integer cod : codigosRegiones()) {
            for (AmIdentificacionDto d : api.getAreasManejoPorRegion(cod)) {
                obt++;
                if (d.getCdArea() == null) {
                    omit++;
                    continue;
                }
                String codigo = String.valueOf(d.getCdArea());
                if (!codigos.add(codigo)) {
                    omit++;
                    continue;
                }
                String nombre = !isBlank(d.getNmSector()) ? d.getNmSector().trim()
                        : (!isBlank(d.getNombreOrganizacion()) ? d.getNombreOrganizacion().trim()
                                : "AMERB " + codigo);
                String region = (d.getRegion() != null && d.getRegion().getValor() != null)
                        ? d.getRegion().getValor().trim() : "";
                nuevas.add(new AmerbModel()
                        .setNombre(truncate(nombre, 100))
                        .setRegion(truncate(region, 50))
                        .setTitular(truncate(d.getNombreOrganizacion(), 100))
                        .setCodigoSernapesca(truncate(codigo, 50))
                        .setFolioOrganizacion(d.getFolioOrganizacion()));
                ins++;
            }
        }
        amerbRepo.saveAll(nuevas);
        return SyncResult.builder().entidad("amerb").ok(true)
                .obtenidos(obt).insertados(ins).actualizados(0).omitidos(omit).build();
    }

    // ------------------------------------------------------------------
    // Plantas (destinatarios tipo planta)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncPlantas() {
        Set<Long> existentes = new HashSet<>();
        for (PlantaModel p : plantaRepo.findAll()) {
            existentes.add(p.getId());
        }
        List<PlantaModel> nuevas = new ArrayList<>();
        int obt = 0, ins = 0, omit = 0;
        for (Integer cod : codigosRegiones()) {
            for (DestinatarioDto d : api.getPlantasPorRegion(cod)) {
                obt++;
                if (d.getCdDestinatario() == null || isBlank(d.getNombre())) {
                    omit++;
                    continue;
                }
                Long id = d.getCdDestinatario().longValue();
                if (!existentes.add(id)) {
                    omit++;
                    continue;
                }
                nuevas.add(new PlantaModel()
                        .setId(id)
                        .setCodigo(d.getCdDestinatario())
                        .setRut(d.getRut())
                        .setNombre(d.getNombre().trim())
                        .setDireccion(d.getDireccion() != null ? d.getDireccion().trim() : null));
                ins++;
            }
        }
        plantaRepo.saveAll(nuevas);
        return SyncResult.builder().entidad("planta").ok(true)
                .obtenidos(obt).insertados(ins).actualizados(0).omitidos(omit).build();
    }

    // ------------------------------------------------------------------
    // Relación usuario-embarcación (consulta por RUT al API, en segundo plano)
    // ------------------------------------------------------------------

    /** Perfiles que operan con embarcación propia (armadores). */
    private static final List<Long> PERFILES_CON_EMBARCACION = List.of(2L, 9L, 10L);

    /** Pausa entre llamadas al API de Sernapesca, para no saturarlo. */
    @Value("${trazalga.sync.usuario-embarcacion.delay-ms:250}")
    private long uexDelayMs;

    /** Hilo dedicado: no ocupa el ForkJoinPool común y serializa ejecuciones. */
    private final java.util.concurrent.ExecutorService uexExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sync-usuario-embarcacion");
                t.setDaemon(true);
                return t;
            });

    private final java.util.concurrent.atomic.AtomicBoolean uexEnCurso = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicInteger uexProcesados = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger uexVinculados = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger uexEmbarcacionesCreadas = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger uexOmitidos = new java.util.concurrent.atomic.AtomicInteger();
    private volatile int uexTotal = 0;
    private volatile java.util.Date uexInicio;
    private volatile java.util.Date uexFin;
    private volatile String uexUltimoError;

    @jakarta.annotation.PreDestroy
    void shutdownUexExecutor() {
        uexExecutor.shutdownNow();
    }

    /**
     * Lanza la sincronización usuario-embarcación en segundo plano y responde de inmediato.
     *
     * @param soloFaltantes true (por defecto): procesa solo usuarios que aún no tienen embarcación
     *                      vinculada, de modo que cada ejecución continúe donde quedó la anterior.
     *                      false: re-consulta y re-vincula a todos los usuarios de los perfiles con embarcación.
     */
    public SyncResult syncUsuarioEmbarcaciones(boolean soloFaltantes) {
        if (!uexEnCurso.compareAndSet(false, true)) {
            return SyncResult.builder().entidad("usuario_embarcacion").ok(true)
                    .obtenidos(uexProcesados.get()).insertados(uexVinculados.get()).omitidos(uexOmitidos.get())
                    .mensaje("Ya hay una sincronización Usuario-Embarcación en curso ("
                            + uexProcesados.get() + " de " + uexTotal + " usuarios procesados). "
                            + "Puedes consultar el avance en GET /sync/sernapesca/usuario-embarcacion/estado.")
                    .build();
        }

        List<Object[]> pendientes;
        try {
            pendientes = soloFaltantes
                    ? usuarioRepo.findIdRutSinEmbarcacionByPerfiles(PERFILES_CON_EMBARCACION)
                    : usuarioRepo.findIdRutByPerfiles(PERFILES_CON_EMBARCACION);
        } catch (RuntimeException e) {
            uexEnCurso.set(false);
            throw e;
        }

        uexTotal = pendientes.size();
        uexProcesados.set(0);
        uexVinculados.set(0);
        uexEmbarcacionesCreadas.set(0);
        uexOmitidos.set(0);
        uexInicio = new java.util.Date();
        uexFin = null;
        uexUltimoError = null;

        final List<Object[]> trabajo = pendientes;
        uexExecutor.submit(() -> {
            try {
                syncUsuarioEmbarcacionesInternal(trabajo);
            } catch (Exception e) {
                log.error("Error en sincronización en segundo plano de usuario-embarcacion", e);
                uexUltimoError = e.getMessage();
            } finally {
                uexFin = new java.util.Date();
                uexEnCurso.set(false);
            }
        });

        return SyncResult.builder()
                .entidad("usuario_embarcacion")
                .ok(true)
                .obtenidos(uexTotal)
                .mensaje("La sincronización de la relación Usuario-Embarcación se ha iniciado en segundo plano ("
                        + uexTotal + " usuarios " + (soloFaltantes ? "sin embarcación pendientes" : "a re-vincular")
                        + "). Los datos se actualizarán progresivamente.")
                .build();
    }

    /** Estado consultable del proceso en segundo plano. */
    public Map<String, Object> getEstadoUsuarioEmbarcaciones() {
        Map<String, Object> out = new HashMap<>();
        boolean enCurso = uexEnCurso.get();
        out.put("enCurso", enCurso);
        out.put("total", uexTotal);
        out.put("procesados", uexProcesados.get());
        out.put("vinculados", uexVinculados.get());
        out.put("embarcacionesCreadas", uexEmbarcacionesCreadas.get());
        out.put("omitidos", uexOmitidos.get());
        out.put("inicio", uexInicio);
        out.put("fin", uexFin);
        out.put("ultimoError", uexUltimoError);
        out.put("mensaje", enCurso
                ? "En curso: " + uexProcesados.get() + " de " + uexTotal + " usuarios procesados."
                : (uexInicio == null
                        ? "Sin ejecuciones desde el último reinicio."
                        : "Finalizada: " + uexVinculados.get() + " vinculados, "
                          + uexEmbarcacionesCreadas.get() + " embarcaciones creadas, "
                          + uexOmitidos.get() + " omitidos de " + uexTotal + "."));
        return out;
    }

    private void syncUsuarioEmbarcacionesInternal(List<Object[]> usuarios) {
        log.info("Iniciando sincronización de usuario-embarcación en segundo plano ({} usuarios)...", usuarios.size());

        for (Object[] fila : usuarios) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Sincronización usuario-embarcación interrumpida tras {} usuarios.", uexProcesados.get());
                break;
            }

            Long usuarioId = ((Number) fila[0]).longValue();
            String rutStr = fila[1] != null ? fila[1].toString() : null;
            uexProcesados.incrementAndGet();

            if (rutStr == null) {
                uexOmitidos.incrementAndGet();
                continue;
            }
            rutStr = rutStr.replace(".", "").trim();
            if (rutStr.contains("-")) {
                rutStr = rutStr.split("-")[0];
            }
            Integer rutInt;
            try {
                rutInt = Integer.parseInt(rutStr);
            } catch (NumberFormatException nfe) {
                uexOmitidos.incrementAndGet();
                continue;
            }

            if (!pausaApi()) {
                break;
            }
            PescadorDto pescador = api.getPescadorPorRut(rutInt);
            if (pescador == null || pescador.getFolioRpa() == null) {
                uexOmitidos.incrementAndGet();
                continue;
            }

            if (!pausaApi()) {
                break;
            }
            EmbarcacionDto dto = api.getEmbarcacionPorFolioRpa(pescador.getFolioRpa());
            if (dto == null) {
                uexOmitidos.incrementAndGet();
                continue;
            }

            try {
                int resultado = self.saveUserVesselRelation(usuarioId, dto);
                if (resultado >= 0) {
                    uexVinculados.incrementAndGet();
                    if (resultado == 1) {
                        uexEmbarcacionesCreadas.incrementAndGet();
                    }
                } else {
                    uexOmitidos.incrementAndGet();
                }
            } catch (Exception ex) {
                log.error("Error guardando relación de embarcación para el usuario con ID " + usuarioId, ex);
                uexUltimoError = "Usuario " + usuarioId + ": " + ex.getMessage();
                uexOmitidos.incrementAndGet();
            }
        }

        log.info("Sincronización de usuario-embarcación finalizada. Procesados: {}, vinculados: {} ({} embarcaciones nuevas), omitidos: {}",
                uexProcesados.get(), uexVinculados.get(), uexEmbarcacionesCreadas.get(), uexOmitidos.get());
    }

    /** Pausa entre llamadas al API; devuelve false si el hilo fue interrumpido (abortar). */
    private boolean pausaApi() {
        try {
            Thread.sleep(uexDelayMs);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Sincronización usuario-embarcación interrumpida durante la pausa.");
            return false;
        }
    }

    /**
     * Vincula la embarcación al usuario (transacción propia por usuario, para que un
     * fallo puntual no pierda el avance del resto).
     *
     * @return 1 si además se creó la embarcación, 0 si se vinculó una existente,
     *         -1 si no se pudo vincular.
     */
    @Transactional
    public int saveUserVesselRelation(Long userId, EmbarcacionDto dto) {
        if (dto.getFolioRpa() == null || isBlank(dto.getNombreNave())) {
            return -1;
        }

        com.trazalga.api.models.UsuarioModel u = usuarioRepo.findById(userId).orElse(null);
        if (u == null) {
            return -1;
        }

        String codigo = String.valueOf(dto.getFolioRpa()).trim();
        String nombreTrim = dto.getNombreNave().trim();

        EmbarcacionModel emb = embarcacionRepo.findByCodigo(codigo).orElse(null);
        boolean creada = false;
        if (emb == null) {
            emb = embarcacionRepo.findByNombre(nombreTrim).orElse(null);
        }

        if (emb == null) {
            emb = new EmbarcacionModel()
                    .setNombre(truncate(nombreTrim, 100))
                    .setCodigo(truncate(codigo, 50));
            emb = embarcacionRepo.save(emb);
            creada = true;
        } else if (emb.getCodigo() == null) {
            emb.setCodigo(truncate(codigo, 50));
            emb = embarcacionRepo.save(emb);
        }

        // Si el usuario ya está vinculado exactamente a esta embarcación, no reescribir
        if (u.getEmbarcaciones().size() == 1 && u.getEmbarcaciones().get(0).getId().equals(emb.getId())) {
            return 0;
        }

        u.getEmbarcaciones().clear();
        u.getEmbarcaciones().add(emb);
        usuarioRepo.save(u);

        return creada ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Códigos de región para los endpoints que requieren iterar por región. */
    private List<Integer> codigosRegiones() {
        List<Integer> codigos = new ArrayList<>();
        for (ComboIntDto r : api.getRegiones()) {
            if (r.getCodigo() != null) {
                codigos.add(r.getCodigo());
            }
        }
        if (codigos.isEmpty()) {
            log.warn("No se obtuvieron códigos de región desde Sernapesca; las entidades por-región quedarán vacías.");
        }
        return codigos;
    }

    private static String comunaKey(String nombre, Long regionId) {
        return norm(nombre) + "|" + (regionId == null ? "" : regionId);
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = s.trim().toUpperCase();
        n = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
