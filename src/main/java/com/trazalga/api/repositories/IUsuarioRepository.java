package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.UsuarioModel;

import java.util.List;
import java.util.Optional;

@Repository
public interface IUsuarioRepository extends JpaRepository<UsuarioModel, Long> {

        // 1. Buscador por RUT (Mejorado con Optional)
        Optional<UsuarioModel> findByRut(String rut);

        // 2. Buscador para LOGIN (Carga Perfil y Comuna de un golpe para mayor
        // velocidad)
        @Query("SELECT u FROM UsuarioModel u " +
                        "JOIN FETCH u.perfil " +
                        "LEFT JOIN FETCH u.comuna " +
                        "WHERE u.rut = :rut")
        Optional<UsuarioModel> findByRutWithDetails(@Param("rut") String rut);

        // 3. Obtener usuarios por lista de perfiles (Tu consulta original optimizada)
        @Query("SELECT u FROM UsuarioModel u " +
                        "JOIN FETCH u.perfil " +
                        "JOIN FETCH u.comuna " +
                        "WHERE u.perfil.id IN :perfiles")
        List<UsuarioModel> findUsuariosByPerfiles(@Param("perfiles") List<Long> perfiles);

        // 4. Útil para la App Móvil: Buscar todos los usuarios de un perfil específico
        // Ejemplo: Buscar todos los Comercializadores (Perfil ID 4) para el selector de
        // destinatarios
        List<UsuarioModel> findByPerfilId(Long perfilId);

        // 5. Verificar si existe un RUT antes de registrar
        boolean existsByRut(String rut);

        // 6. Proyección (id, rut) de usuarios por perfil, para la sincronización
        // usuario-embarcación (evita cargar entidades completas en un barrido largo)
        @Query("SELECT u.id, u.rut FROM UsuarioModel u WHERE u.perfil.id IN :perfiles")
        List<Object[]> findIdRutByPerfiles(@Param("perfiles") List<Long> perfiles);

        // 7. Igual que la anterior, pero solo usuarios que aún no tienen embarcación vinculada
        @Query("SELECT u.id, u.rut FROM UsuarioModel u WHERE u.perfil.id IN :perfiles AND u.embarcaciones IS EMPTY")
        List<Object[]> findIdRutSinEmbarcacionByPerfiles(@Param("perfiles") List<Long> perfiles);

}