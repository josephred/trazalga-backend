package com.trazalga.api.services.declaraciones;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;

/**
 * Escritura de las declaraciones de planta: abastecimiento, producción y destino (refinamiento 06-11, NP.0).
 * <ul>
 *   <li><b>Sesión y dueño</b> al modificar o eliminar, con la misma regla que las declaraciones de origen
 *       ({@link PoliticaEdicionDeclaracion}): sin sesión se rechaza (401) sólo si
 *       {@code declaracion_escritura_exige_sesion} está activo; con sesión, sólo el dueño o un
 *       administrador (403).</li>
 *   <li><b>Selección válida</b>: los lotes que consume una producción, y las producciones que consume un
 *       destino, deben ser de la misma planta, estar libres, no estar anulados y no estar retenidos (409).</li>
 * </ul>
 */
@Service
public class PoliticaEscrituraPlanta {

    public static final String ABASTECIMIENTO = "PLANTA_ABASTECIMIENTO";
    public static final String PRODUCCION = "PLANTA_PRODUCCION";
    public static final String DESTINO = "PLANTA_DESTINO";

    private static final Set<String> ESTADOS_ANULADOS = Set.of("ANULADA", "ANULADO", "RECHAZADA", "RECHAZADO");

    @Autowired private PoliticaEdicionDeclaracion politicaEdicion;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired private IDeclaracionPlantaProduccionRepository produccionRepository;
    @Autowired private IDeclaracionPlantaDestinoRepository destinoRepository;
    @Autowired private BloqueoCargaService bloqueoCargaService;
    @Autowired(required = false) private ConfiguracionGeneralService configService;

    // ------------------------------------------------------------------ sesión y dueño

    /**
     * Antes de modificar o eliminar una declaración de planta.
     *
     * @param etapa {@link #ABASTECIMIENTO}, {@link #PRODUCCION} o {@link #DESTINO}.
     */
    @Transactional(readOnly = true)
    public void verificarSesionYDueno(String etapa, Long declaracionId) {
        if (declaracionId == null) {
            return;
        }
        UsuarioModel duenio;
        boolean existe;
        switch (etapa) {
            case ABASTECIMIENTO -> {
                var d = abastecimientoRepository.findById(declaracionId);
                existe = d.isPresent();
                duenio = d.map(DeclaracionPlantaAbastecimientoModel::getUsuario).orElse(null);
            }
            case PRODUCCION -> {
                var d = produccionRepository.findById(declaracionId);
                existe = d.isPresent();
                duenio = d.map(DeclaracionPlantaProduccionModel::getUsuario).orElse(null);
            }
            case DESTINO -> {
                var d = destinoRepository.findById(declaracionId);
                existe = d.isPresent();
                duenio = d.map(DeclaracionPlantaDestinoModel::getUsuario).orElse(null);
            }
            default -> throw new IllegalArgumentException("Etapa de planta no válida: " + etapa);
        }
        if (!existe) {
            return; // el servicio responde como siempre ante una declaración inexistente
        }
        // Dentro de la transacción: el usuario es un proxy LAZY y aquí se puede leer su RUT
        politicaEdicion.verificarSesionYDueno(duenio != null ? duenio.getRut() : null);
    }

    // ------------------------------------------------------------------ selección válida

    /**
     * Lotes de abastecimiento que consume una producción.
     *
     * @param plantaUsuarioId usuario planta que declara la producción.
     * @param seleccion       {@code declaraciones_seleccionadas} del pedido.
     * @param produccionId    al modificar, la producción que ya consume esos lotes; null al crear.
     */
    @Transactional(readOnly = true)
    public void verificarLotesDeProduccion(Long plantaUsuarioId, String seleccion, Long produccionId) {
        List<Long> ids = ids(seleccion, ABASTECIMIENTO);
        if (ids.isEmpty()) {
            if (configService == null || configService.getBoolean("produccion_exige_lotes", true)) {
                throw new ValidacionPlantaException("Debe seleccionar al menos un lote de abastecimiento.");
            }
            return;
        }
        Map<Long, DeclaracionPlantaAbastecimientoModel> porId = new java.util.HashMap<>();
        for (DeclaracionPlantaAbastecimientoModel a : abastecimientoRepository.findAllById(ids)) {
            porId.put(a.getId(), a);
        }
        Map<Long, MotivoBloqueoDTO> retenidos = bloqueoCargaService.bloqueosPorTipoEIds(ABASTECIMIENTO, ids);
        for (Long id : ids) {
            DeclaracionPlantaAbastecimientoModel a = porId.get(id);
            if (a == null) {
                throw conflicto("El lote de abastecimiento #" + id + " no existe.");
            }
            String folio = a.getFolioDeclaracionAPla() != null ? a.getFolioDeclaracionAPla() : "#" + id;
            // NA.1: el lote pertenece a la planta que lo recibió (dueña de la recepción).
            // Destinatario se autoasigna en backend para el flujo interno de planta.
            if (!esDe(plantaUsuarioId, a.getUsuario())) {
                throw conflicto("El lote " + folio + " no pertenece a esta planta.");
            }
            if (a.getDeclaracionDestinatario() != null && !a.getDeclaracionDestinatario().equals(produccionId)) {
                throw conflicto("El lote " + folio + " ya fue utilizado en otra declaración.");
            }
            if (anulado(a.getEstado())) {
                throw conflicto("El lote " + folio + " está anulado.");
            }
            MotivoBloqueoDTO b = retenidos.get(id);
            if (b != null) {
                throw conflicto("El lote " + folio + " está retenido por " + b.getMarca()
                        + " (hallazgo #" + b.getMarcaId() + ") hasta su fiscalización.");
            }
        }
    }

    /**
     * Producciones que consume un destino.
     *
     * @param destinoId al modificar, el destino que ya consume esas producciones; null al crear.
     */
    @Transactional(readOnly = true)
    public void verificarProduccionesDeDestino(Long plantaUsuarioId, String seleccion, Long destinoId) {
        List<Long> ids = ids(seleccion, PRODUCCION);
        if (ids.isEmpty()) {
            if (configService == null || configService.getBoolean("destino_exige_producciones", true)) {
                throw new ValidacionPlantaException("Debe seleccionar al menos una producción.");
            }
            return;
        }
        Map<Long, DeclaracionPlantaProduccionModel> porId = new java.util.HashMap<>();
        for (DeclaracionPlantaProduccionModel p : produccionRepository.findAllById(ids)) {
            porId.put(p.getId(), p);
        }
        Map<Long, MotivoBloqueoDTO> retenidas = bloqueoCargaService.bloqueosPorTipoEIds(PRODUCCION, ids);
        for (Long id : ids) {
            DeclaracionPlantaProduccionModel p = porId.get(id);
            if (p == null) {
                throw conflicto("La producción #" + id + " no existe.");
            }
            String folio = p.getFolioDeclaracionPpla() != null ? p.getFolioDeclaracionPpla() : "#" + id;
            if (!esDe(plantaUsuarioId, p.getUsuario())) {
                throw conflicto("La producción " + folio + " no pertenece a esta planta.");
            }
            if (p.getDeclaracionDestinatario() != null && !p.getDeclaracionDestinatario().equals(destinoId)) {
                throw conflicto("La producción " + folio + " ya fue utilizada en otra declaración.");
            }
            if (anulado(p.getEstado())) {
                throw conflicto("La producción " + folio + " está anulada.");
            }
            MotivoBloqueoDTO b = retenidas.get(id);
            if (b != null) {
                throw conflicto("La producción " + folio + " está retenida por " + b.getMarca()
                        + " (hallazgo #" + b.getMarcaId() + ") hasta su fiscalización.");
            }
        }
    }

    /**
     * Valida cantidad, especie, formato y tolerancia de kilos de un destino frente a sus producciones (ZP.2 / D56).
     */
    @Transactional(readOnly = true)
    public void validarContraProduccionesDeDestino(DeclaracionPlantaDestinoModel destino) {
        if (destino == null || destino.getCantidad() == null
                || destino.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionPlantaException("La cantidad declarada en el destino debe ser mayor a 0 kg.");
        }
        List<Long> ids = ids(destino.getDeclaracionesSeleccionadas(), PRODUCCION);
        if (ids.isEmpty()) {
            return;
        }
        Long especieDestinoId = destino.getEspecie() != null ? destino.getEspecie().getId() : null;
        if (especieDestinoId == null) {
            throw new ValidacionPlantaException("Debe seleccionar la especie del destino.");
        }
        boolean exigeMismoFormato = configService == null
                || configService.getBoolean("destino_exige_mismo_formato", true);
        Long productoDestinoId = destino.getProducto() != null ? destino.getProducto().getId() : null;
        if (exigeMismoFormato && productoDestinoId == null) {
            throw new ValidacionPlantaException("Debe seleccionar el formato de producto del destino.");
        }

        List<DeclaracionPlantaProduccionModel> producciones = produccionRepository.findAllById(ids);
        BigDecimal sumaProducida = BigDecimal.ZERO;
        for (DeclaracionPlantaProduccionModel p : producciones) {
            String folio = p.getFolioDeclaracionPpla() != null ? p.getFolioDeclaracionPpla() : "#" + p.getId();
            EspecieModel espProd = p.getMateriaPrimaEspecie();
            if (espProd == null || !especieDestinoId.equals(espProd.getId())) {
                String nombreEsp = espProd != null ? espProd.getNombre() : "sin especie";
                throw new ValidacionPlantaException("La especie del destino no coincide con la de la producción "
                        + folio + " (" + nombreEsp + ").");
            }
            if (exigeMismoFormato) {
                ProductoModel fmtProd = p.getProductoResultante();
                if (fmtProd == null || !productoDestinoId.equals(fmtProd.getId())) {
                    String nombreFmt = fmtProd != null ? fmtProd.getNombre() : "sin formato";
                    throw new ValidacionPlantaException("El formato del destino no coincide con el de la producción "
                            + folio + " (" + nombreFmt + ").");
                }
            }
            if (p.getCantidadProducto() != null) {
                sumaProducida = sumaProducida.add(p.getCantidadProducto());
            }
        }

        double toleranciaPct = configService != null
                ? Math.max(0.0, configService.getDouble("destino_tolerancia_pct", 1.0))
                : 1.0;
        BigDecimal factor = BigDecimal.ONE.add(
                BigDecimal.valueOf(toleranciaPct).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal maxPermitido = sumaProducida.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        if (destino.getCantidad().compareTo(maxPermitido) > 0) {
            throw new ValidacionPlantaException("La cantidad del destino (" + destino.getCantidad().toPlainString()
                    + " kg) supera el máximo permitido de " + maxPermitido.toPlainString()
                    + " kg según las producciones seleccionadas (suma " + sumaProducida.toPlainString()
                    + " kg, tolerancia " + BigDecimal.valueOf(toleranciaPct).stripTrailingZeros().toPlainString() + " %).");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static List<Long> ids(String seleccion, String tipo) {
        if (seleccion == null || seleccion.isBlank()) {
            return List.of();
        }
        return SeleccionTokens.idsParaTipo(SeleccionTokens.parse(seleccion), tipo).stream()
                .filter(Objects::nonNull).distinct().toList();
    }

    private static boolean esDe(Long plantaUsuarioId, UsuarioModel duenio) {
        if (plantaUsuarioId == null) {
            return false;
        }
        return duenio != null && plantaUsuarioId.equals(duenio.getId());
    }

    private static boolean anulado(String estado) {
        return estado != null && ESTADOS_ANULADOS.contains(estado.trim().toUpperCase(Locale.ROOT));
    }

    private static ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }
}
