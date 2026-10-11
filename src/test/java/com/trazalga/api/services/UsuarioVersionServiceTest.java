package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ResumenVersionesAppDTO;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;

@ExtendWith(MockitoExtension.class)
public class UsuarioVersionServiceTest {

    @Mock
    private IUsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioVersionService service;

    private UsuarioModel usuarioPlanta;
    private PerfilModel perfilPlanta;

    @BeforeEach
    void setUp() {
        service.limpiarCache();

        perfilPlanta = new PerfilModel();
        perfilPlanta.setId(3L);
        perfilPlanta.setNombre("PLANTA");

        usuarioPlanta = new UsuarioModel();
        usuarioPlanta.setId(10L);
        usuarioPlanta.setRut("76123456-7");
        usuarioPlanta.setNombres("Planta Sur Austral");
        usuarioPlanta.setPerfil(perfilPlanta);
        usuarioPlanta.setAppVersion(null);
    }

    @Test
    @DisplayName("TP.1 Semver: Compara correctamente versiones mayores, menores e iguales")
    void testComparacionSemver() {
        assertTrue(UsuarioVersionService.esVersionMenorQue("1.0.9", "1.1.0"));
        assertTrue(UsuarioVersionService.esVersionMenorQue("1.0.0", "1.1.0"));
        assertTrue(UsuarioVersionService.esVersionMenorQue("0.9.5", "1.1.0"));
        assertTrue(UsuarioVersionService.esVersionMenorQue(null, "1.1.0"));
        assertTrue(UsuarioVersionService.esVersionMenorQue("", "1.1.0"));

        assertFalse(UsuarioVersionService.esVersionMenorQue("1.1.0", "1.1.0"));
        assertFalse(UsuarioVersionService.esVersionMenorQue("1.1.1", "1.1.0"));
        assertFalse(UsuarioVersionService.esVersionMenorQue("1.2.0", "1.1.0"));
        assertFalse(UsuarioVersionService.esVersionMenorQue("2.0.0", "1.1.0"));
    }

    @Test
    @DisplayName("TP.1: Primera petición con versión móvil guarda en la BD")
    void testRegistrarVersionPrimeraVez() {
        when(usuarioRepository.findByRut("76123456-7")).thenReturn(Optional.of(usuarioPlanta));

        service.registrarVersion("76123456-7", "1.1.0");

        verify(usuarioRepository, times(1)).save(usuarioPlanta);
        assertEquals("1.1.0", usuarioPlanta.getAppVersion());
        assertNotNull(usuarioPlanta.getAppVersionAt());
    }

    @Test
    @DisplayName("TP.1: Peticiones subsiguientes con la misma versión se resuelven en caché sin escribir en la BD")
    void testRegistrarVersionUsaCacheSinReescribirBD() {
        when(usuarioRepository.findByRut("76123456-7")).thenReturn(Optional.of(usuarioPlanta));

        // Primera petición: escribe en BD y puebla caché
        service.registrarVersion("76123456-7", "1.1.0");
        verify(usuarioRepository, times(1)).save(usuarioPlanta);

        // Peticiones posteriores con la misma versión: caché en memoria evita I/O redundante
        service.registrarVersion("76123456-7", "1.1.0");
        service.registrarVersion("76123456-7", "1.1.0");
        service.registrarVersion("76123456-7", "1.1.0");

        // El save solo debe haber sido llamado una sola vez en total
        verify(usuarioRepository, times(1)).save(any(UsuarioModel.class));
    }

    @Test
    @DisplayName("TP.1: Si la versión del usuario cambia, se actualiza en BD y se renueva el caché")
    void testRegistrarVersionCambioActualizaBD() {
        when(usuarioRepository.findByRut("76123456-7")).thenReturn(Optional.of(usuarioPlanta));

        // Versión inicial
        service.registrarVersion("76123456-7", "1.1.0");
        verify(usuarioRepository, times(1)).save(usuarioPlanta);

        // Nueva versión instalada por el operador (ej. 1.1.1)
        service.registrarVersion("76123456-7", "1.1.1");
        verify(usuarioRepository, times(2)).save(usuarioPlanta);
        assertEquals("1.1.1", usuarioPlanta.getAppVersion());
    }

    @Test
    @DisplayName("TP.1: Listar usuarios agrupa distribución y detecta plantas con versión anterior a 1.1.0")
    void testListarUsuariosVersionResumen() {
        UsuarioModel u1 = new UsuarioModel();
        u1.setId(1L);
        u1.setRut("11111111-1");
        u1.setNombres("Planta Norte");
        u1.setPerfil(perfilPlanta);
        u1.setAppVersion("1.0.8"); // Desactualizada

        UsuarioModel u2 = new UsuarioModel();
        u2.setId(2L);
        u2.setRut("22222222-2");
        u2.setNombres("Planta Centro");
        u2.setPerfil(perfilPlanta);
        u2.setAppVersion("1.1.0"); // Al día

        UsuarioModel u3 = new UsuarioModel();
        u3.setId(3L);
        u3.setRut("33333333-3");
        u3.setNombres("Planta Sur");
        u3.setPerfil(perfilPlanta);
        u3.setAppVersion(null); // Sin versión -> < 1.1.0

        PerfilModel perfilPescador = new PerfilModel();
        perfilPescador.setId(1L);
        perfilPescador.setNombre("RECOLECTOR");

        UsuarioModel u4 = new UsuarioModel();
        u4.setId(4L);
        u4.setRut("44444444-4");
        u4.setNombres("Recolector Juan");
        u4.setPerfil(perfilPescador);
        u4.setAppVersion("1.1.0");

        when(usuarioRepository.findAll()).thenReturn(Arrays.asList(u1, u2, u3, u4));

        ResumenVersionesAppDTO resumen = service.listarUsuariosVersion("PLANTA");

        assertNotNull(resumen);
        assertEquals(3, resumen.getTotalUsuarios()); // Solo las 3 plantas
        assertEquals(3, resumen.getTotalPlantas());
        assertEquals(2, resumen.getTotalPlantasAnterioresA110()); // u1 (1.0.8) y u3 (null)
        assertEquals(2, resumen.getPlantasConAppAnteriorA110().size());
        assertEquals(1L, resumen.getDistribucionVersiones().get("1.0.8"));
        assertEquals(1L, resumen.getDistribucionVersiones().get("1.1.0"));
        assertEquals(1L, resumen.getDistribucionVersiones().get("Sin registrar"));
    }
}
