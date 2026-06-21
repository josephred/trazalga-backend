package com.trazalga.api.services.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public SernapescaSyncService(SernapescaApiClient api,
            IRegionRepository regionRepo, IComunaRepository comunaRepo, ICaletaRepository caletaRepo,
            IEspecieRepository especieRepo, IExtraccionTipoRepository extraccionTipoRepo,
            IEmbarcacionRepository embarcacionRepo, IBuzoRepository buzoRepo, IAmerbRepository amerbRepo,
            IPlantaRepository plantaRepo) {
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
        Set<String> comunaKeys = new HashSet<>();
        for (ComunaModel c : comunaRepo.findAll()) {
            comunaKeys.add(comunaKey(c.getNombre(), c.getRegion() != null ? c.getRegion().getId() : null));
        }
        Set<String> caletaKeys = new HashSet<>();
        for (CaletaModel c : caletaRepo.findAll()) {
            caletaKeys.add(comunaKey(c.getNombre(), c.getRegion() != null ? c.getRegion().getId() : null));
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
                    if (comunaKeys.add(ck)) {
                        nuevasComunas.add(new ComunaModel().setNombre(ct.getNombreComuna().trim()).setRegion(region));
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
                    if (caletaKeys.add(calKey)) {
                        nuevasCaletas.add(new CaletaModel().setNombre(cal.getNombreCaleta().trim()).setRegion(region));
                        calIns++;
                    } else {
                        calOmit++;
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

    @Transactional
    public SyncResult syncEspecies() {
        List<ComboIntDto> especies = api.getEspeciesAutorizadas();
        Set<String> existentes = new HashSet<>();
        for (EspecieModel es : especieRepo.findAll()) {
            existentes.add(norm(es.getNombre()));
        }
        List<EspecieModel> nuevas = new ArrayList<>();
        int ins = 0, omit = 0;
        Set<Integer> permitidos = Set.of(50, 70, 71, 72);
        
        for (ComboIntDto es : especies) {
            if (es.getCodigo() == null || !permitidos.contains(es.getCodigo())) {
                continue;
            }
            if (isBlank(es.getValor())) {
                continue;
            }
            if (existentes.add(norm(es.getValor()))) {
                nuevas.add(new EspecieModel().setNombre(es.getValor().trim()));
                ins++;
            } else {
                omit++;
            }
        }
        especieRepo.saveAll(nuevas);
        return SyncResult.builder().entidad("especie").ok(true)
                .obtenidos(especies.size()).insertados(ins).actualizados(0).omitidos(omit).build();
    }

    // ------------------------------------------------------------------
    // Embarcaciones (por región)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncEmbarcaciones() {
        Set<String> codigos = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        for (EmbarcacionModel e : embarcacionRepo.findAll()) {
            if (e.getCodigo() != null) {
                codigos.add(e.getCodigo());
            }
            nombres.add(norm(e.getNombre()));
        }
        List<EmbarcacionModel> nuevas = new ArrayList<>();
        int obt = 0, ins = 0, omit = 0;
        for (Integer cod : codigosRegiones()) {
            for (EmbarcacionDto d : api.getEmbarcacionesPorRegion(cod)) {
                obt++;
                if (d.getFolioRpa() == null || isBlank(d.getNombreNave())) {
                    omit++;
                    continue;
                }
                String codigo = String.valueOf(d.getFolioRpa());
                String nombre = d.getNombreNave().trim();
                if (!codigos.add(codigo) || !nombres.add(norm(nombre))) {
                    omit++; // ya existe (por código o por nombre único)
                    continue;
                }
                nuevas.add(new EmbarcacionModel().setNombre(nombre).setCodigo(codigo));
                ins++;
            }
        }
        embarcacionRepo.saveAll(nuevas);
        return SyncResult.builder().entidad("embarcacion").ok(true)
                .obtenidos(obt).insertados(ins).actualizados(0).omitidos(omit).build();
    }

    // ------------------------------------------------------------------
    // Buzos / recolectores (por región)
    // ------------------------------------------------------------------

    @Transactional
    public SyncResult syncBuzos() {
        Set<String> codigos = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        for (BuzoModel b : buzoRepo.findAll()) {
            if (b.getCodigo() != null) {
                codigos.add(b.getCodigo());
            }
            nombres.add(norm(b.getNombre()));
        }
        List<BuzoModel> nuevos = new ArrayList<>();
        int obt = 0, ins = 0, omit = 0;
        for (Integer cod : codigosRegiones()) {
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
                    omit++;
                    continue;
                }
                nuevos.add(new BuzoModel().setNombre(nombre).setCodigo(codigo));
                ins++;
            }
        }
        buzoRepo.saveAll(nuevos);
        return SyncResult.builder().entidad("buzo").ok(true)
                .obtenidos(obt).insertados(ins).actualizados(0).omitidos(omit).build();
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
        return s == null ? "" : s.trim().toUpperCase();
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
