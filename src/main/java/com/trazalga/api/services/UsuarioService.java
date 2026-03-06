package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.UsuarioRegistroDTO;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IPerfilRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

@Service
public class UsuarioService {

    @Autowired
    IUsuarioRepository usuarioRepository;

    @Autowired
    private IPerfilRepository perfilRepository;

    @Autowired
    private IComunaRepository comunaRepository;

    public UsuarioModel registrarUsuario(UsuarioRegistroDTO dto) {
        // 1. Validar si el RUT ya existe
        if (usuarioRepository.existsByRut(dto.getRut())) {
            throw new RuntimeException("El RUT ya se encuentra registrado.");
        }

        // 2. Crear nueva instancia de Usuario
        UsuarioModel usuario = new UsuarioModel();
        usuario.setRut(dto.getRut());
        usuario.setNombres(dto.getNombres());
        usuario.setApellidop(dto.getApellidop());
        usuario.setApellidom(dto.getApellidom());
        usuario.setCorreo(dto.getCorreo());
        usuario.setEstado("ACTIVO");
        usuario.setFechaCreacion(new Date());

        // 3. GUARDAR CLAVE (Sin encriptar según solicitud)
        usuario.setClave(dto.getClave());

        // 4. Asignar Perfil y Comuna buscando en sus repositorios
        usuario.setPerfil(perfilRepository.findById(dto.getPerfilId())
                .orElseThrow(() -> new RuntimeException("Perfil no encontrado")));

        usuario.setComuna(comunaRepository.findById(dto.getComunaId())
                .orElseThrow(() -> new RuntimeException("Comuna no encontrada")));

        return usuarioRepository.save(usuario);
    }

    public UsuarioModel saveUsuario(UsuarioModel usuarioModel) {
        // No encriptar clave
        return usuarioRepository.save(usuarioModel);
    }

    public ArrayList<UsuarioModel> getUsuarios() {
        return (ArrayList<UsuarioModel>) usuarioRepository.findAll();
    }

    public Optional<UsuarioModel> getById(Long id) {
        return usuarioRepository.findById(id);
    }

    public UsuarioModel getByRut(String rut) {
        return usuarioRepository.findByRut(rut)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con rut: " + rut));
    }

    public List<UsuarioModel> getUsuariosByPerfiles(List<Long> perfiles) {
        return usuarioRepository.findUsuariosByPerfiles(perfiles);
    }

    public List<UsuarioModel> getUsuariosByPerfil() {
        List<Long> longList = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        return usuarioRepository.findUsuariosByPerfiles(longList);
    }

    public List<UsuarioModel> getAllUsuarios() {
        return usuarioRepository.findAll();
    }

    public Optional<UsuarioModel> getUsuarioById(Long id) {
        return usuarioRepository.findById(id);
    }

    public UsuarioModel updateById(UsuarioModel request, Long id) {
        UsuarioModel usuarioModel = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
        usuarioModel.setRut(request.getRut());
        usuarioModel.setNombres(request.getNombres());
        usuarioModel.setApellidop(request.getApellidop());
        usuarioModel.setApellidom(request.getApellidom());
        usuarioModel.setCorreo(request.getCorreo());
        usuarioModel.setPerfil(request.getPerfil());
        return usuarioRepository.save(usuarioModel);
    }

    public Boolean deleteUsuario(Long id) {
        try {
            usuarioRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
