package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.security.JwtUtils;

@Service
public class AuthService {
    @Autowired
    private IUsuarioRepository usuarioRepo; // Conexión a la tabla 'usuario'
    @Autowired
    private JwtUtils jwtUtils;

    public String validarCredenciales(String rut, String claveEnvidada) {
        // Buscamos al usuario en la BD usando JPA
        UsuarioModel usuario = usuarioRepo.findByRut(rut)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Comparamos claves (Idealmente usando BCrypt para seguridad)
        if (usuario.getClave().equals(claveEnvidada)) {
            // Si todo está OK, generamos el Token JWT para la sesión
            return jwtUtils.generateToken(usuario);
        } else {
            throw new RuntimeException("Clave incorrecta");
        }
    }
}
