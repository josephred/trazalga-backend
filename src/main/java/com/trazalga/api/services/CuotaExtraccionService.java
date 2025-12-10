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

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

@Service
public class CuotaExtraccionService {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

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

    public QuotaCheckResult checkDeclarationQuota(Long usuarioId, String perfil, Long especieId, Date fechaDeclaracion, BigDecimal nuevaCantidadKg) {
        // 1. Buscar cuotas activas
        List<CuotaExtraccionModel> cuotas = new ArrayList<>();
        if (especieId != null) {
            cuotas = cuotaRepository.findByPerfilAndEspecieIdAndActivoTrue(perfil, especieId);
        }
        if (cuotas.isEmpty()) {
            cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil);
        }

        if (cuotas.isEmpty()) {
            return new QuotaCheckResult(true, "No hay cuota definida para este perfil/especie.");
        }

        // 2. Validar fecha
        if (fechaDeclaracion == null) {
            return new QuotaCheckResult(false, "La fecha de declaración es requerida para validar la cuota.");
        }

        // 3. Revisar cada cuota aplicable
        for (CuotaExtraccionModel cuota : cuotas) {
            if (!"DIARIO".equalsIgnoreCase(cuota.getPeriodo())) {
                continue;
            }

            BigDecimal sumCaptura = BigDecimal.ZERO;

            // 4. Sumar capturas del día
            if ("RECOLECTOR".equalsIgnoreCase(perfil)) {
                List<DeclaracionRecolectorModel> decls = declaracionRecolectorRepository.findByUsuarioIdAndEspecieIdAndFechaDeclaracion(usuarioId, especieId, fechaDeclaracion);
                for (DeclaracionRecolectorModel d : decls) {
                    if (d.getCaptura() != null) {
                        // Seguridad: Convertimos a BigDecimal por si el modelo sigue siendo Double
                        sumCaptura = sumCaptura.add(new BigDecimal(d.getCaptura().toString()));
                    }
                }
            } else if ("ARMADOR".equalsIgnoreCase(perfil)) {
                List<DeclaracionArmadorModel> decls = declaracionArmadorRepository.findByUsuarioIdAndEspecieIdAndFechaDeclaracion(usuarioId, especieId, fechaDeclaracion);
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
                String msg = String.format("La cuota diaria de %.2f kg para perfil %s y especie ha sido excedida. Total acumulado: %.2f kg (intentando agregar %.2f kg).", 
                                           limiteCuota, perfil, total, nuevaCantidad);
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