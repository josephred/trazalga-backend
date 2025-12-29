package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // IMPORTAR
import org.springframework.stereotype.Service;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.security.JwtUtils;

@Service
public class AuthService {
    @Autowired
    private IUsuarioRepository usuarioRepo;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder; // INYECTAR EL ENCODER

    public String validarCredenciales(String rut, String claveEnviada) {
        UsuarioModel usuario = usuarioRepo.findByRut(rut)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // MEJORA: Usar matches para comparar la clave plana vs la encriptada de la BD
        if (passwordEncoder.matches(claveEnviada, usuario.getClave())) {
            return jwtUtils.generateToken(usuario);
        } else {
            throw new RuntimeException("Clave incorrecta");
        }
    }
}