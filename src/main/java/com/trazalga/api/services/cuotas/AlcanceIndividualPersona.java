package com.trazalga.api.services.cuotas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;

@Component("alcanceIndividualPersona")
public class AlcanceIndividualPersona implements AlcanceCuota {

    public static final String CLAVE = "INDIVIDUAL_PERSONA";

    @Autowired
    @Lazy
    private IUsuarioRepository usuarioRepository;

    public AlcanceIndividualPersona() {}

    public AlcanceIndividualPersona(IUsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        if (c.getUsuario() == null || (c.getUsuario().getId() == null && c.getUsuario().getRut() == null)) {
            throw new IllegalArgumentException("Las cuotas individuales por persona deben especificar un usuario.");
        }

        UsuarioModel u = c.getUsuario();
        if ((u.getComuna() == null || u.getNombres() == null) && u.getId() != null && usuarioRepository != null) {
            u = usuarioRepository.findById(u.getId()).orElse(u);
            c.setUsuario(u);
        }

        // Si el usuario tiene comuna asignada con región, asociar la región a la cuota
        if (u.getComuna() != null && u.getComuna().getRegion() != null) {
            c.setRegion(u.getComuna().getRegion());
        }

        c.setNivelAgregacion("INDIVIDUAL");
        c.setEsPlantilla(false);
        c.setMacrozona(null);
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null || a.getUsuario() == null || b.getUsuario() == null) {
            return false;
        }
        Long aId = a.getUsuario().getId();
        Long bId = b.getUsuario().getId();
        if (aId != null && bId != null) {
            return aId.equals(bId);
        }
        String aRut = a.getUsuario().getRut();
        String bRut = b.getUsuario().getRut();
        return aRut != null && aRut.equalsIgnoreCase(bRut);
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        if (c.getUsuario() != null) {
            UsuarioModel u = c.getUsuario();
            String nombres = (u.getNombres() != null ? u.getNombres() : "") + " " + (u.getApellidop() != null ? u.getApellidop() : "");
            nombres = nombres.trim();
            String rut = u.getRut() != null ? u.getRut() : "";
            if (!nombres.isEmpty() && !rut.isEmpty()) {
                return String.format("Persona %s (%s)", nombres, rut);
            }
            if (!nombres.isEmpty()) {
                return "Persona " + nombres;
            }
            if (!rut.isEmpty()) {
                return "Persona " + rut;
            }
            return "Persona " + u.getId();
        }
        return "Persona no especificada";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        String base = describir(otra);
        if (base.startsWith("Persona ")) {
            return "La persona " + base.substring(8);
        }
        return "La " + base;
    }

    @Override
    public boolean comparableEnJerarquia() {
        return false;
    }
}
