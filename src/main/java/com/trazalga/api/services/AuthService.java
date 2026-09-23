package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.AuthResponse;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.security.JwtUtils;

@Service
public class AuthService {
    @Autowired
    private IUsuarioRepository usuarioRepo;
    @Autowired
    private JwtUtils jwtUtils;

    public String validarCredenciales(String rut, String claveEnviada) {
        return login(rut, claveEnviada).getToken();
    }

    public AuthResponse login(String rut, String claveEnviada) {
        UsuarioModel usuario = usuarioRepo.findByRutWithDetails(rut)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Comparación en texto plano (según solicitud)
        if (claveEnviada.equals(usuario.getClave())) {
            String token = jwtUtils.generateToken(usuario);
            String perfil = usuario.getPerfil() != null ? usuario.getPerfil().getNombre() : null;
            String nombre = usuario.getNombres();
            return new AuthResponse(token, perfil, nombre, usuario.getId());
        } else {
            throw new RuntimeException("Clave incorrecta");
        }
    }
}