package com.trazalga.api.services;

import com.trazalga.api.dto.ProductoAdminDTO;
import com.trazalga.api.dto.ProductoAdminDTO.ProductoAdminRequest;
import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductoAdminService {

    @Autowired
    private IProductoRepository productoRepository;

    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IDeclaracionPlantaProduccionRepository produccionRepository;

    @Autowired
    private IDeclaracionPlantaDestinoRepository destinoRepository;

    @Autowired
    private ConfiguracionGeneralService configService;

    @Transactional(readOnly = true)
    public List<ProductoAdminDTO> listarConUsos() {
        List<ProductoModel> productos = productoRepository.findAllByOrderByNombreAsc();
        Map<Long, Long> usosProd = new HashMap<>();
        acumularConteos(usosProd, produccionRepository.countUsosPorProductoResultante());
        acumularConteos(usosProd, produccionRepository.countUsosPorMateriaPrimaProducto());

        Map<Long, Long> usosDest = new HashMap<>();
        acumularConteos(usosDest, destinoRepository.countUsosPorProducto());

        return productos.stream().map(p -> toDto(p, usosProd.getOrDefault(p.getId(), 0L), usosDest.getOrDefault(p.getId(), 0L))).toList();
    }

    @Transactional
    public ProductoAdminDTO crear(ProductoAdminRequest req, String usuario) {
        ProductoModel model = new ProductoModel();
        aplicarCambios(model, req, usuario, null);
        ProductoModel saved = productoRepository.save(model);
        return toDto(saved, 0L, 0L);
    }

    @Transactional
    public ProductoAdminDTO actualizar(Long id, ProductoAdminRequest req, String usuario) {
        ProductoModel model = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Formato de producto no encontrado: " + id));
        aplicarCambios(model, req, usuario, id);
        ProductoModel saved = productoRepository.save(model);
        return toDto(saved, 0L, 0L);
    }

    @Transactional
    public ProductoAdminDTO cambiarActivo(Long id, boolean activo, String usuario) {
        ProductoModel model = productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Formato de producto no encontrado: " + id));
        model.setActivo(activo);
        model.setActualizadoEn(LocalDateTime.now());
        model.setActualizadoPor(normalizarUsuario(usuario));
        ProductoModel saved = productoRepository.save(model);
        return toDto(saved, 0L, 0L);
    }

    /**
     * Valida que el formato de producto elegido sea activo y tenga código oficial
     * cuando `produccion_exige_formato_oficial` está habilitado (NF.4).
     */
    public void validarFormatoParaDeclaracion(ProductoModel producto) {
        if (configService == null || !configService.getBoolean("produccion_exige_formato_oficial", false)) {
            return;
        }
        if (producto == null || producto.getId() == null) {
            throw new ValidacionPlantaException("Debe seleccionar un formato de producto oficial del catálogo.");
        }
        ProductoModel persistido = productoRepository.findById(producto.getId())
                .orElseThrow(() -> new ValidacionPlantaException("El formato de producto seleccionado no existe en el catálogo."));
        if (Boolean.FALSE.equals(persistido.getActivo())) {
            throw new ValidacionPlantaException("El formato '" + persistido.getNombre() + "' está inactivo en el catálogo.");
        }
        if (persistido.getCodigo() == null || persistido.getCodigo().isBlank()) {
            throw new ValidacionPlantaException("El formato '" + persistido.getNombre() + "' aún no tiene código oficial Sernapesca asignado.");
        }
    }

    private void aplicarCambios(ProductoModel model, ProductoAdminRequest req, String usuario, Long selfId) {
        if (req == null || req.getNombre() == null || req.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del formato es obligatorio.");
        }
        String nombre = req.getNombre().trim();
        Optional<ProductoModel> porNombre = productoRepository.findByNombreIgnoreCase(nombre);
        if (porNombre.isPresent() && !porNombre.get().getId().equals(selfId)) {
            throw new IllegalStateException("Ya existe un formato de producto con el nombre '" + nombre + "'.");
        }

        String codigo = req.getCodigo() != null && !req.getCodigo().isBlank() ? req.getCodigo().trim().toUpperCase() : null;
        if (codigo != null) {
            if (codigo.length() > 30) {
                throw new IllegalArgumentException("El código oficial no puede superar 30 caracteres.");
            }
            Optional<ProductoModel> porCodigo = productoRepository.findByCodigoIgnoreCase(codigo);
            if (porCodigo.isPresent() && !porCodigo.get().getId().equals(selfId)) {
                throw new IllegalStateException("Ya existe un formato con el código '" + codigo + "'.");
            }
        }

        HumedadEstadoModel humedad = null;
        if (req.getHumedadEstadoId() != null) {
            humedad = humedadEstadoRepository.findById(req.getHumedadEstadoId())
                    .orElseThrow(() -> new IllegalArgumentException("Estado de humedad no encontrado: " + req.getHumedadEstadoId()));
        }

        EspecieModel especie = null;
        if (req.getEspecieId() != null) {
            especie = especieRepository.findById(req.getEspecieId())
                    .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada: " + req.getEspecieId()));
        }

        model.setCodigo(codigo);
        model.setNombre(nombre);
        model.setDescripcion(req.getDescripcion() != null ? req.getDescripcion().trim() : null);
        model.setHumedadEstado(humedad);
        model.setEspecie(especie);
        model.setActivo(req.getActivo() != null ? req.getActivo() : true);
        model.setFuente(req.getFuente() != null && !req.getFuente().isBlank() ? req.getFuente().trim() : "LOCAL");
        model.setActualizadoEn(LocalDateTime.now());
        model.setActualizadoPor(normalizarUsuario(usuario));
    }

    private static String normalizarUsuario(String usuario) {
        if (usuario == null || usuario.isBlank()) return "ADMIN";
        return usuario.length() > 60 ? usuario.substring(0, 60) : usuario;
    }

    private static void acumularConteos(Map<Long, Long> destino, List<Object[]> rows) {
        if (rows == null) return;
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                Long prodId = ((Number) row[0]).longValue();
                long count = ((Number) row[1]).longValue();
                destino.merge(prodId, count, Long::sum);
            }
        }
    }

    private static ProductoAdminDTO toDto(ProductoModel p, long usosProd, long usosDest) {
        return ProductoAdminDTO.builder()
                .id(p.getId())
                .codigo(p.getCodigo())
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .humedadEstadoId(p.getHumedadEstado() != null ? p.getHumedadEstado().getId() : null)
                .humedadEstadoNombre(p.getHumedadEstado() != null ? p.getHumedadEstado().getNombre() : null)
                .especieId(p.getEspecie() != null ? p.getEspecie().getId() : null)
                .especieNombre(p.getEspecie() != null ? p.getEspecie().getNombre() : null)
                .activo(p.getActivo() != null ? p.getActivo() : Boolean.TRUE)
                .fuente(p.getFuente())
                .actualizadoEn(p.getActualizadoEn())
                .actualizadoPor(p.getActualizadoPor())
                .usosProduccion(usosProd)
                .usosDestino(usosDest)
                .usosTotales(usosProd + usosDest)
                .build();
    }
}

