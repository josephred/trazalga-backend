package com.trazalga.api.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

@Service
public class CuotaExtraccionService {

    @Autowired
    ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    IDeclaracionArmadorRepository declaracionArmadorRepository;

    public List<CuotaExtraccionModel> getAll() {
        return cuotaRepository.findAll();
    }

    public Optional<CuotaExtraccionModel> getById(Long id) {
        return cuotaRepository.findById(id);
    }

    public CuotaExtraccionModel save(CuotaExtraccionModel cuota) {
        return cuotaRepository.save(cuota);
    }

    public boolean delete(Long id) {
        try {
            cuotaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public QuotaCheckResult checkDeclarationQuota(Long usuarioId, String perfil, Long especieId, Date fechaDeclaracion, Double nuevaCantidadKg) {
        // Buscar cuotas activas para perfil y especie
        List<CuotaExtraccionModel> cuotas = new ArrayList<>();
        if (especieId != null) {
            cuotas = cuotaRepository.findByPerfilAndEspecieIdAndActivoTrue(perfil, especieId);
        }
        if (cuotas.isEmpty()) {
            // fallback: buscar cuotas por perfil sin especificar especie
            cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil);
        }

        if (cuotas.isEmpty()) {
            // No hay cuota aplicable -> permitido
            return new QuotaCheckResult(true, "No hay cuota definida para este perfil/especie.");
        }

        // Convertir fecha a LocalDate para comparación de día
        LocalDate targetDate = null;
        if (fechaDeclaracion != null) {
            targetDate = Instant.ofEpochMilli(fechaDeclaracion.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        }

        // Revisar cada cuota aplicable
        for (CuotaExtraccionModel cuota : cuotas) {
            if (!"DIARIO".equalsIgnoreCase(cuota.getPeriodo())) {
                // Por ahora solo cobramos la lógica diaria; mensual se puede agregar más adelante
                continue;
            }

            Double sumCaptura = 0.0;

            if ("RECOLECTOR".equalsIgnoreCase(perfil)) {
                // Obtener declaraciones del recolector y filtrar por fecha y especie
                List<DeclaracionRecolectorModel> decls = declaracionRecolectorRepository.findAllByUsuarioId(usuarioId);
                for (DeclaracionRecolectorModel d : decls) {
                    if (d.getFechaDeclaracion() == null || d.getEspecie() == null) continue;
                    if (d.getEspecie().getId() == null) continue;
                    if (!d.getEspecie().getId().equals(especieId)) continue;
                    LocalDate dDate = Instant.ofEpochMilli(d.getFechaDeclaracion().getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (targetDate != null && dDate.equals(targetDate)) {
                        if (d.getCaptura() != null) sumCaptura += d.getCaptura();
                    }
                }
            } else if ("ARMADOR".equalsIgnoreCase(perfil)) {
                List<DeclaracionArmadorModel> decls = declaracionArmadorRepository.findAllByUsuarioId(usuarioId);
                for (DeclaracionArmadorModel d : decls) {
                    if (d.getFechaDeclaracion() == null || d.getEspecie() == null) continue;
                    if (d.getEspecie().getId() == null) continue;
                    if (!d.getEspecie().getId().equals(especieId)) continue;
                    LocalDate dDate = Instant.ofEpochMilli(d.getFechaDeclaracion().getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                    if (targetDate != null && dDate.equals(targetDate)) {
                        if (d.getCaptura() != null) sumCaptura += d.getCaptura();
                    }
                }
            } else {
                // perfiles no controlados por cuota
                return new QuotaCheckResult(true, "Perfil no tiene cuota definida o no aplica.");
            }

            Double total = sumCaptura + (nuevaCantidadKg != null ? nuevaCantidadKg : 0.0);
            if (total > cuota.getLimiteKg()) {
                String msg = String.format("La cuota diaria de %.3f kg para perfil %s y especie excedida: %.3f kg (incluyendo %.3f kg nuevo).", cuota.getLimiteKg(), perfil, total, (nuevaCantidadKg != null ? nuevaCantidadKg : 0.0));
                return new QuotaCheckResult(false, msg);
            }
        }

        return new QuotaCheckResult(true, "Declaración permitida dentro de la cuota.");
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
