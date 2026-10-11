package com.trazalga.api.services;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.ResumenVersionesAppDTO;
import com.trazalga.api.dto.UsuarioVersionItemDTO;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;

/**
 * Servicio para gestión y observabilidad de versiones de la app móvil (TP.1).
 * Implementa caché en memoria por usuario para evitar escrituras redundantes en la base de datos.
 */
@Service
public class UsuarioVersionService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioVersionService.class);
    public static final String VERSION_REFERENCIA_PLANTA = "1.1.0";

    @Autowired
    private IUsuarioRepository usuarioRepository;

    // Caché en memoria (rut -> appVersion) para evitar escrituras en cada petición (TP.1)
    private final Map<String, String> userVersionCache = new ConcurrentHashMap<>();

    /**
     * Registra la versión de la app móvil del usuario autenticado.
     * Solo escribe en la base de datos si la versión es diferente a la conocida en caché.
     */
    @Transactional
    public void registrarVersion(String rut, String appVersion) {
        if (rut == null || rut.isBlank() || appVersion == null || appVersion.isBlank()) {
            return;
        }

        String rutTrim = rut.trim().toUpperCase();
        String versionTrim = appVersion.trim();

        // Si ya está en caché con la misma versión, omitir escritura a la BD
        String cachedVersion = userVersionCache.get(rutTrim);
        if (versionTrim.equals(cachedVersion)) {
            return;
        }

        Optional<UsuarioModel> optUser = usuarioRepository.findByRut(rutTrim);
        if (optUser.isPresent()) {
            UsuarioModel user = optUser.get();
            if (!versionTrim.equals(user.getAppVersion())) {
                user.setAppVersion(versionTrim);
                user.setAppVersionAt(new Date());
                usuarioRepository.save(user);
                log.info("TP.1: Versión de app actualizada para usuario RUT {}: {} (anterior: {})",
                        rutTrim, versionTrim, cachedVersion);
            }
            userVersionCache.put(rutTrim, versionTrim);
        }
    }

    /**
     * Consulta y resume las versiones de app de los usuarios, con filtro opcional por perfil.
     */
    @Transactional(readOnly = true)
    public ResumenVersionesAppDTO listarUsuariosVersion(String perfilFiltro) {
        List<UsuarioModel> todos = usuarioRepository.findAll();

        String perfilNorm = perfilFiltro != null ? perfilFiltro.trim().toUpperCase() : null;

        List<UsuarioVersionItemDTO> items = todos.stream()
                .filter(u -> {
                    if (perfilNorm == null || perfilNorm.isBlank()) return true;
                    PerfilModel p = u.getPerfil();
                    return p != null && p.getNombre() != null && p.getNombre().toUpperCase().contains(perfilNorm);
                })
                .map(this::toItemDTO)
                .sorted(Comparator.comparing(
                        UsuarioVersionItemDTO::getAppVersionAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .collect(Collectors.toList());

        // Distribución de versiones
        Map<String, Long> distribucion = items.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getAppVersion() != null && !i.getAppVersion().isBlank() ? i.getAppVersion() : "Sin registrar",
                        Collectors.counting()
                ));

        // Plantas con app anterior a 1.1.0
        List<UsuarioVersionItemDTO> plantasAnteriores = items.stream()
                .filter(this::esUsuarioPlanta)
                .filter(UsuarioVersionItemDTO::isEsAnteriorA110)
                .collect(Collectors.toList());

        long totalPlantas = items.stream().filter(this::esUsuarioPlanta).count();

        return ResumenVersionesAppDTO.builder()
                .totalUsuarios(items.size())
                .totalPlantas(totalPlantas)
                .totalPlantasAnterioresA110(plantasAnteriores.size())
                .distribucionVersiones(distribucion)
                .plantasConAppAnteriorA110(plantasAnteriores)
                .usuarios(items)
                .build();
    }

    /**
     * Limpia el caché en memoria (útil para pruebas unitarias).
     */
    public void limpiarCache() {
        userVersionCache.clear();
    }

    private UsuarioVersionItemDTO toItemDTO(UsuarioModel u) {
        String ver = u.getAppVersion();
        boolean anterior = esVersionMenorQue(ver, VERSION_REFERENCIA_PLANTA);

        PerfilModel p = u.getPerfil();
        Long perfilId = p != null ? p.getId() : null;
        String perfilNombre = p != null ? p.getNombre() : "Sin perfil";

        return UsuarioVersionItemDTO.builder()
                .id(u.getId())
                .rut(u.getRut())
                .nombreCompleto(u.getNombreCompleto())
                .perfilId(perfilId)
                .perfilNombre(perfilNombre)
                .appVersion(ver)
                .appVersionAt(u.getAppVersionAt())
                .esAnteriorA110(anterior)
                .build();
    }

    private boolean esUsuarioPlanta(UsuarioVersionItemDTO item) {
        if (item.getPerfilNombre() == null) return false;
        String p = item.getPerfilNombre().toUpperCase();
        return p.contains("PLANTA");
    }

    /**
     * Compara semver para determinar si version es estrictamente menor que versionReferencia.
     */
    public static boolean esVersionMenorQue(String version, String versionReferencia) {
        if (version == null || version.isBlank()) {
            return true;
        }
        if (versionReferencia == null || versionReferencia.isBlank()) {
            return false;
        }

        int[] v1 = parseVersion(version);
        int[] v2 = parseVersion(versionReferencia);

        int maxLen = Math.max(v1.length, v2.length);
        for (int i = 0; i < maxLen; i++) {
            int num1 = i < v1.length ? v1[i] : 0;
            int num2 = i < v2.length ? v2[i] : 0;
            if (num1 < num2) return true;
            if (num1 > num2) return false;
        }
        return false;
    }

    private static int[] parseVersion(String ver) {
        try {
            String clean = ver.replaceAll("[^0-9.]", "");
            String[] parts = clean.split("\\.");
            int[] nums = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                nums[i] = parts[i].isBlank() ? 0 : Integer.parseInt(parts[i]);
            }
            return nums;
        } catch (Exception e) {
            return new int[]{0};
        }
    }
}
