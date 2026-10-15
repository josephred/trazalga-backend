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

        // 8. Búsqueda paginada con JOIN FETCH para mantenedores y selectores (TM.1)
        @Query("SELECT DISTINCT u FROM UsuarioModel u " +
               "LEFT JOIN FETCH u.perfil p " +
               "LEFT JOIN FETCH u.comuna c " +
               "LEFT JOIN FETCH c.region " +
               "WHERE (:term = '' OR LOWER(u.rut) LIKE :likeTerm OR LOWER(u.nombres) LIKE :likeTerm OR LOWER(u.apellidop) LIKE :likeTerm) " +
               "AND (:perfil IS NULL OR LOWER(p.nombre) = LOWER(:perfil))")
        List<UsuarioModel> buscarUsuariosConFiltro(
                @Param("term") String term,
                @Param("likeTerm") String likeTerm,
                @Param("perfil") String perfil,
                org.springframework.data.domain.Pageable pageable);

        // 9. IDs de usuarios con perfil fiscalizador / sernapesca / admin (TA.5 / 5.1)
        @Query("SELECT DISTINCT u.id FROM UsuarioModel u " +
               "JOIN u.perfil p " +
               "LEFT JOIN u.comuna c " +
               "LEFT JOIN c.region r " +
               "WHERE (UPPER(p.nombre) LIKE '%FISCALIZADOR%' OR UPPER(p.nombre) LIKE '%SERNAPESCA%' OR UPPER(p.nombre) LIKE '%ADMIN%') " +
               "AND (:regionId IS NULL OR r.id IS NULL OR r.id = :regionId)")
        List<Long> findFiscalizadoresIds(@Param("regionId") Long regionId);

}