package com.trazalga.api.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

@Service
public class CuotaExtraccionService {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

    @Autowired
    private IRegionRepository regionRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IAmerbRepository amerbRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    public List<CuotaExtraccionModel> getAll() {
        return cuotaRepository.findAll();
    }

    public Optional<CuotaExtraccionModel> getById(Long id) {
        return cuotaRepository.findById(id);
    }

    public CuotaExtraccionModel save(CuotaExtraccionModel cuota) {
        resolverReferencias(cuota);
        validarDatosBasicos(cuota);
        validarJerarquia(cuota);
        return cuotaRepository.save(cuota);
    }

    /* =====================================================================
       Validación jerárquica de cuotas (config-time):
       usuario <= área de manejo (si existe cuota) <= región (si existe cuota).
       Se valida en ambas direcciones: al crear/editar una cuota hija no puede
       superar a su padre, y al bajar el límite de una cuota padre no pueden
       quedar hijas por encima. La comparación cruza perfiles: el tope
       geográfico manda sin importar quién declara.
       ===================================================================== */

    /** El frontend envía referencias como {id: n}; se recargan completas para poder validar (p.ej. amerb.region). */
    private void resolverReferencias(CuotaExtraccionModel cuota) {
        if (cuota.getRegion() != null && cuota.getRegion().getId() != null) {
            cuota.setRegion(regionRepository.findById(cuota.getRegion().getId())
                .orElseThrow(() -> new IllegalArgumentException("La región indicada no existe.")));
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
    }

    private void validarDatosBasicos(CuotaExtraccionModel cuota) {
        if (cuota.getLimiteKg() == null || cuota.getLimiteKg() <= 0) {
            throw new IllegalArgumentException("El límite de la cuota debe ser mayor que 0 kg.");
        }
        if (cuota.getPeriodo() == null || cuota.getPeriodo().isBlank()) {
            throw new IllegalArgumentException("El periodo de la cuota es obligatorio (DIARIO o MENSUAL).");
        }
        if (cuota.getPerfil() == null || cuota.getPerfil().isBlank()) {
            throw new IllegalArgumentException("El perfil de la cuota es obligatorio.");
        }
    }

    private void validarJerarquia(CuotaExtraccionModel cuota) {
        if (!Boolean.TRUE.equals(cuota.getActivo())) {
            return; // una cuota inactiva no participa de la jerarquía
        }

        List<CuotaExtraccionModel> activas = new ArrayList<>(cuotaRepository.findByActivoTrue());
        // Al editar, la versión anterior de esta misma cuota no cuenta.
        activas.removeIf(c -> cuota.getId() != null && cuota.getId().equals(c.getId()));

        String alcance = alcanceDe(cuota);
        String regionCuota = nombreRegionDe(cuota);

        // ------ Hacia arriba: la nueva cuota no puede superar a sus padres ------
        if ("USUARIO".equals(alcance)) {
            if (cuota.getAmerb() != null) {
                for (CuotaExtraccionModel padre : activas) {
                    if ("AREA".equals(alcanceDe(padre))
                            && padre.getAmerb().getId().equals(cuota.getAmerb().getId())
                            && mismoPeriodo(cuota, padre) && especiesComparables(cuota, padre)
                            && cuota.getLimiteKg() > padre.getLimiteKg()) {
                        throw new IllegalArgumentException(String.format(
                            "La cuota del usuario (%.2f kg) no puede superar la cuota del área de manejo «%s» (%.2f kg) para %s en periodo %s.",
                            cuota.getLimiteKg(), nombreAmerb(padre), padre.getLimiteKg(), nombreEspecie(padre), padre.getPeriodo()));
                    }
                }
            }
            validarContraRegion(cuota, activas, regionCuota, "del usuario");
        } else if ("AREA".equals(alcance)) {
            validarContraRegion(cuota, activas, regionCuota, "del área de manejo");
        }

        // ------ Hacia abajo: al fijar un padre, ninguna hija activa puede quedar por encima ------
        if ("REGION".equals(alcance)) {
            for (CuotaExtraccionModel hija : activas) {
                String alcanceHija = alcanceDe(hija);
                if (("AREA".equals(alcanceHija) || "USUARIO".equals(alcanceHija))
                        && regionCuota != null && regionCuota.equalsIgnoreCase(nombreRegionDe(hija))
                        && mismoPeriodo(cuota, hija) && especiesComparables(cuota, hija)
                        && hija.getLimiteKg() > cuota.getLimiteKg()) {
                    throw new IllegalArgumentException(String.format(
                        "No puedes fijar la cuota regional en %.2f kg: la cuota %s «%s» (%.2f kg, %s) quedaría por encima. Ajusta primero las cuotas inferiores.",
                        cuota.getLimiteKg(), "AREA".equals(alcanceHija) ? "del área" : "del usuario",
                        describirAlcance(hija), hija.getLimiteKg(), nombreEspecie(hija)));
                }
            }
        } else if ("AREA".equals(alcance)) {
            for (CuotaExtraccionModel hija : activas) {
                if ("USUARIO".equals(alcanceDe(hija))
                        && hija.getAmerb() != null
                        && hija.getAmerb().getId().equals(cuota.getAmerb().getId())
                        && mismoPeriodo(cuota, hija) && especiesComparables(cuota, hija)
                        && hija.getLimiteKg() > cuota.getLimiteKg()) {
                    throw new IllegalArgumentException(String.format(
                        "No puedes fijar la cuota del área en %.2f kg: la cuota del usuario «%s» (%.2f kg, %s) quedaría por encima. Ajusta primero las cuotas de usuario.",
                        cuota.getLimiteKg(), describirAlcance(hija), hija.getLimiteKg(), nombreEspecie(hija)));
                }
            }
        }
    }

    private void validarContraRegion(CuotaExtraccionModel cuota, List<CuotaExtraccionModel> activas, String regionCuota, String quien) {
        if (regionCuota == null) return;
        for (CuotaExtraccionModel padre : activas) {
            if ("REGION".equals(alcanceDe(padre))
                    && regionCuota.equalsIgnoreCase(nombreRegionDe(padre))
                    && mismoPeriodo(cuota, padre) && especiesComparables(cuota, padre)
                    && cuota.getLimiteKg() > padre.getLimiteKg()) {
                throw new IllegalArgumentException(String.format(
                    "La cuota %s (%.2f kg) no puede superar la cuota de la región «%s» (%.2f kg) para %s en periodo %s.",
                    quien, cuota.getLimiteKg(), nombreRegionDe(padre), padre.getLimiteKg(), nombreEspecie(padre), padre.getPeriodo()));
            }
        }
    }

    /** Alcance según el campo más específico presente: USUARIO > AREA > REGION > GLOBAL. */
    private String alcanceDe(CuotaExtraccionModel c) {
        if (c.getUsuario() != null) return "USUARIO";
        if (c.getAmerb() != null) return "AREA";
        if (c.getRegion() != null) return "REGION";
        return "GLOBAL";
    }

    /** Región efectiva de la cuota: la explícita, o la del AMERB (que guarda el nombre de la región). */
    private String nombreRegionDe(CuotaExtraccionModel c) {
        if (c.getRegion() != null && c.getRegion().getNombre() != null) return c.getRegion().getNombre().trim();
        if (c.getAmerb() != null && c.getAmerb().getRegion() != null && !c.getAmerb().getRegion().isBlank()) {
            return c.getAmerb().getRegion().trim();
        }
        return null;
    }

    private boolean mismoPeriodo(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        return a.getPeriodo() != null && a.getPeriodo().equalsIgnoreCase(b.getPeriodo());
    }

    /** Especie null = "todas": una cuota sin especie es comparable con cualquiera. */
    private boolean especiesComparables(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a.getEspecie() == null || b.getEspecie() == null) return true;
        return a.getEspecie().getId().equals(b.getEspecie().getId());
    }

    private String nombreEspecie(CuotaExtraccionModel c) {
        return c.getEspecie() != null ? c.getEspecie().getNombre() : "todas las especies";
    }

    private String nombreAmerb(CuotaExtraccionModel c) {
        if (c.getAmerb() == null) return "";
        return c.getAmerb().getNombre() != null ? c.getAmerb().getNombre() : ("AMERB " + c.getAmerb().getId());
    }

    /**
     * Datos maestros recortados para los selects del mantenedor de cuotas
     * (regiones, especies, áreas de manejo y usuarios). Payload mínimo:
     * evita exponer campos sensibles del usuario (p.ej. clave) y aligera la respuesta.
     */
    public java.util.Map<String, Object> getMaestros() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();

        out.put("regiones", regionRepository.findAll().stream()
            .map(r -> java.util.Map.of("id", r.getId(), "nombre", r.getNombre() != null ? r.getNombre() : ""))
            .toList());

        out.put("especies", especieRepository.findAll().stream()
            .map(e -> java.util.Map.of("id", e.getId(), "nombre", e.getNombre() != null ? e.getNombre() : ""))
            .toList());

        out.put("amerbs", amerbRepository.findAll().stream()
            .map(a -> java.util.Map.of(
                "id", a.getId(),
                "nombre", a.getNombre() != null ? a.getNombre() : ("AMERB " + a.getId()),
                "region", a.getRegion() != null ? a.getRegion() : ""))
            .toList());

        out.put("usuarios", usuarioRepository.findAll().stream()
            .map(u -> {
                String nombre = ((u.getNombres() != null ? u.getNombres() : "") + " "
                        + (u.getApellidop() != null ? u.getApellidop() : "")).trim();
                return java.util.Map.of(
                    "id", u.getId(),
                    "nombre", nombre.isEmpty() ? ("Usuario " + u.getId()) : nombre,
                    "rut", u.getRut() != null ? u.getRut() : "",
                    "perfil", (u.getPerfil() != null && u.getPerfil().getNombre() != null) ? u.getPerfil().getNombre() : "");
            })
            .toList());

        return out;
    }

    public boolean delete(Long id) {
        try {
            cuotaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public QuotaCheckResult checkDeclarationQuota(Long usuarioId, String perfil, Long especieId, Date fechaDeclaracion, BigDecimal nuevaCantidadKg) {
        // 1. Buscar cuotas activas
        List<CuotaExtraccionModel> cuotas = new ArrayList<>();
        if (especieId != null) {
            cuotas = cuotaRepository.findByPerfilAndEspecieIdAndActivoTrue(perfil, especieId);
        }
        if (cuotas.isEmpty()) {
            cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil);
        }

        // Las cuotas por AMERB aplican a declaraciones de área, que no se validan por este flujo
        cuotas = new ArrayList<>(cuotas);
        cuotas.removeIf(c -> c.getAmerb() != null);

        // Si existen cuotas específicas para este actor, priman sobre las globales;
        // las cuotas de otros actores nunca aplican
        List<CuotaExtraccionModel> especificasActor = new ArrayList<>();
        for (CuotaExtraccionModel c : cuotas) {
            if (c.getUsuario() != null && c.getUsuario().getId().equals(usuarioId)) {
                especificasActor.add(c);
            }
        }
        if (!especificasActor.isEmpty()) {
            cuotas = especificasActor;
        } else {
            cuotas.removeIf(c -> c.getUsuario() != null);
        }

        if (cuotas.isEmpty()) {
            return new QuotaCheckResult(true, "No hay cuota definida para este perfil/especie.");
        }

        // 2. Validar fecha
        if (fechaDeclaracion == null) {
            return new QuotaCheckResult(false, "La fecha de declaración es requerida para validar la cuota.");
        }

        // Normalizar a la fecha calendario en UTC (el JSON "yyyy-MM-dd" se parsea como medianoche UTC;
        // sin esto, la truncación a DATE en la zona horaria local puede restar un día)
        LocalDate diaDeclaracion = fechaDeclaracion.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        Date fechaConsulta = java.sql.Date.valueOf(diaDeclaracion);

        // 3. Revisar cada cuota aplicable
        for (CuotaExtraccionModel cuota : cuotas) {
            if (!"DIARIO".equalsIgnoreCase(cuota.getPeriodo())) {
                continue;
            }

            BigDecimal sumCaptura = BigDecimal.ZERO;

            // 4. Sumar capturas del día
            if ("RECOLECTOR".equalsIgnoreCase(perfil)) {
                List<DeclaracionRecolectorModel> decls = declaracionRecolectorRepository.findByUsuarioIdAndEspecieIdAndFechaDeclaracion(usuarioId, especieId, fechaConsulta);
                for (DeclaracionRecolectorModel d : decls) {
                    if (d.getCaptura() != null) {
                        // Seguridad: Convertimos a BigDecimal por si el modelo sigue siendo Double
                        sumCaptura = sumCaptura.add(new BigDecimal(d.getCaptura().toString()));
                    }
                }
            } else if ("ARMADOR".equalsIgnoreCase(perfil)) {
                List<DeclaracionArmadorModel> decls = declaracionArmadorRepository.findByUsuarioIdAndEspecieIdAndFechaDeclaracion(usuarioId, especieId, fechaConsulta);
                for (DeclaracionArmadorModel d : decls) {
                    if (d.getCaptura() != null) {
                        // Seguridad: Convertimos a BigDecimal por si el modelo sigue siendo Double
                        sumCaptura = sumCaptura.add(new BigDecimal(d.getCaptura().toString()));
                    }
                }
            } else {
                return new QuotaCheckResult(true, "Perfil no tiene cuota de extracción definida.");
            }

            // 5. Calcular Total
            BigDecimal nuevaCantidad = (nuevaCantidadKg != null) ? nuevaCantidadKg : BigDecimal.ZERO;
            BigDecimal total = sumCaptura.add(nuevaCantidad);
            
            // 6. SOLUCIÓN DEL ERROR (Línea ~98):
            // Convertimos el Double (limiteKg) a BigDecimal antes de comparar.
            BigDecimal limiteCuota = BigDecimal.valueOf(cuota.getLimiteKg());

            // 7. Comparar
            if (total.compareTo(limiteCuota) > 0) {
                String msg = "La cuota diaria de %.2f kg para perfil %s y especie ha sido excedida. Total acumulado: %.2f kg (intentando agregar %.2f kg).".formatted(
                        limiteCuota, perfil, total, nuevaCantidad);
                return new QuotaCheckResult(false, msg);
            }
        }

        return new QuotaCheckResult(true, "Declaración permitida dentro de la cuota.");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(Date startDate, Date endDate, String periodo, String perfil) {
        if (periodo == null || periodo.isEmpty()) periodo = "DIARIO";
        if (perfil == null || perfil.isEmpty()) perfil = "RECOLECTOR";

        List<ControlCuotaDiariaDTO> result = new ArrayList<>();
        List<CuotaExtraccionModel> cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil.toUpperCase());
        
        if (startDate == null) {
            LocalDate localDate = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            startDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        String tableName = "declaracion_recolector";
        if ("ARMADOR".equalsIgnoreCase(perfil)) tableName = "declaracion_armador";
        if ("AREA".equalsIgnoreCase(perfil) || "ÁREA DE MANEJO".equalsIgnoreCase(perfil)) tableName = "declaracion_area";

        boolean esArea = "declaracion_area".equals(tableName);

        for (CuotaExtraccionModel cuota : cuotas) {
            if (!periodo.equalsIgnoreCase(cuota.getPeriodo()) || cuota.getEspecie() == null) {
                continue;
            }

            boolean filtraActor = cuota.getUsuario() != null;
            boolean filtraAmerb = cuota.getAmerb() != null && esArea;

            String sql = "SELECT COALESCE(SUM(desembarque), 0) FROM " + tableName +
                         " WHERE especie_id = :especieId AND fecha_declaracion BETWEEN :startDate AND :endDate";
            if (filtraActor) sql += " AND usuario_id = :usuarioId";
            if (filtraAmerb) sql += " AND amerb_id = :amerbId";

            jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
            query.setParameter("especieId", cuota.getEspecie().getId());
            query.setParameter("startDate", startDate);
            query.setParameter("endDate", endDate);
            if (filtraActor) query.setParameter("usuarioId", cuota.getUsuario().getId());
            if (filtraAmerb) query.setParameter("amerbId", cuota.getAmerb().getId());

            Object res = query.getSingleResult();
            BigDecimal sumCaptura = BigDecimal.ZERO;
            if (res != null) {
                sumCaptura = new BigDecimal(res.toString());
            }

            BigDecimal limite = BigDecimal.valueOf(cuota.getLimiteKg());
            Double porcentaje = 0.0;
            if (limite.compareTo(BigDecimal.ZERO) > 0) {
                porcentaje = sumCaptura.doubleValue() / limite.doubleValue() * 100.0;
            }

            ControlCuotaDiariaDTO dto = ControlCuotaDiariaDTO.builder()
                .especieNombre(cuota.getEspecie().getNombre())
                .volumenExtraido(sumCaptura)
                .limiteCuota(limite)
                .porcentajeUso(Math.round(porcentaje * 100.0) / 100.0)
                .alcance(describirAlcance(cuota))
                .build();
            
            result.add(dto);
        }
        return result;
    }

    private String describirAlcance(CuotaExtraccionModel cuota) {
        if (cuota.getUsuario() != null) {
            String nombre = ((cuota.getUsuario().getNombres() != null ? cuota.getUsuario().getNombres() : "") + " " +
                             (cuota.getUsuario().getApellidop() != null ? cuota.getUsuario().getApellidop() : "")).trim();
            return nombre.isEmpty() ? "Actor " + cuota.getUsuario().getId() : nombre;
        }
        if (cuota.getAmerb() != null) {
            String nombre = cuota.getAmerb().getNombre();
            if (nombre == null || nombre.trim().isEmpty()) return "AMERB " + cuota.getAmerb().getId();
            return nombre.toUpperCase().startsWith("AMERB") ? nombre : "AMERB " + nombre;
        }
        return "Global";
    }

    public static class QuotaCheckResult {
        private boolean allowed;
        private String message;

        public QuotaCheckResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getMessage() {
            return message;
        }
    }
}