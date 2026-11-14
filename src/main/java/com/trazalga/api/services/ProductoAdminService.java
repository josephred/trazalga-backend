package com.trazalga.api.services;

import com.trazalga.api.dto.CatalogoImportacionDTO;
import com.trazalga.api.dto.CatalogoImportacionDTO.FilaResultadoCatalogoDTO;
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
import com.trazalga.api.services.catalogo.LectorArchivoCatalogo;
import com.trazalga.api.services.catalogo.LectorArchivoCatalogo.FilaCruda;
import com.trazalga.api.services.catalogo.NombreFormatoOficial;
import com.trazalga.api.services.catalogo.NombreFormatoOficial.TipoFila;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        return mapearConUsos(productoRepository.findAllByOrderByNombreAsc());
    }

    private List<ProductoAdminDTO> mapearConUsos(List<ProductoModel> productos) {
        Map<Long, Long> usosProd = new HashMap<>();
        acumularConteos(usosProd, produccionRepository.countUsosPorProductoResultante());
        acumularConteos(usosProd, produccionRepository.countUsosPorMateriaPrimaProducto());

        Map<Long, Long> usosDest = new HashMap<>();
        acumularConteos(usosDest, destinoRepository.countUsosPorProducto());

        return productos.stream()
                .map(p -> toDto(p, usosProd.getOrDefault(p.getId(), 0L), usosDest.getOrDefault(p.getId(), 0L)))
                .toList();
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
     * Procesa vista previa o aplicación transaccional de la planilla oficial del catálogo Sernapesca (QF.3).
     * Con {@code aplicar=false} no modifica ninguna entidad persistida; nunca desactiva productos locales ausentes (D45).
     */
    @Transactional
    public CatalogoImportacionDTO importarCatalogo(InputStream stream, String nombreArchivo, boolean aplicar, String usuario) {
        String archivoLimpio = limpiarNombreArchivo(nombreArchivo);
        String fuente = construirFuenteCatalogo(archivoLimpio, LocalDate.now());
        String usuarioNorm = normalizarUsuario(usuario);
        List<FilaCruda> crudas = leerCrudasSeguro(stream, archivoLimpio);

        List<EspecieModel> especies = especieRepository.findAll();
        especies.sort(Comparator.comparing(EspecieModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, EspecieModel> especiePorCanon = new HashMap<>();
        Map<String, EspecieModel> especiePorCodigo = new HashMap<>();
        for (EspecieModel e : especies) {
            String canon = NombreFormatoOficial.normalizarEspecie(e.getNombre());
            if (canon != null) especiePorCanon.putIfAbsent(canon, e);
            if (e.getCodigoSernapesca() != null && !e.getCodigoSernapesca().isBlank()) {
                especiePorCodigo.put(e.getCodigoSernapesca().trim().toUpperCase(Locale.ROOT), e);
            }
        }

        Map<String, HumedadEstadoModel> humedadPorNombre = new HashMap<>();
        for (HumedadEstadoModel h : humedadEstadoRepository.findAll()) {
            humedadPorNombre.put(normalizarTextoClave(h.getNombre()), h);
        }

        List<ProductoModel> existentes = productoRepository.findAllByOrderByNombreAsc();
        Map<String, ProductoModel> porCodigo = new HashMap<>();
        Map<String, ProductoModel> porNombre = new HashMap<>();
        for (ProductoModel p : existentes) {
            if (p.getCodigo() != null && !p.getCodigo().isBlank()) {
                porCodigo.put(p.getCodigo().trim().toUpperCase(Locale.ROOT), p);
            }
            if (p.getNombre() != null && !p.getNombre().isBlank()) {
                porNombre.put(p.getNombre().trim().toLowerCase(Locale.ROOT), p);
            }
        }

        ContextoImportacion ctx = new ContextoImportacion(
                aplicar, fuente, usuarioNorm, especiePorCanon, especiePorCodigo, humedadPorNombre, porCodigo, porNombre);
        for (FilaCruda cruda : crudas) {
            procesarFilaCatalogo(cruda, ctx);
        }

        List<ProductoModel> noEnCatalogoModels = existentes.stream()
                .filter(p -> !ctx.idsPresentes.contains(p.getId()))
                .toList();
        List<ProductoAdminDTO> localesNoEnCatalogo = mapearConUsos(noEnCatalogoModels);

        return CatalogoImportacionDTO.builder()
                .aplicado(aplicar)
                .archivo(archivoLimpio)
                .fuente(fuente)
                .totalFilas(ctx.nuevos + ctx.actualizados + ctx.sinCambio + ctx.recursos + ctx.porRevisar)
                .nuevos(ctx.nuevos)
                .actualizados(ctx.actualizados)
                .sinCambio(ctx.sinCambio)
                .recursos(ctx.recursos)
                .porRevisar(ctx.porRevisar)
                .descartadosDuplicados(ctx.descartadosDuplicados)
                .localesNoEnCatalogoCount(localesNoEnCatalogo.size())
                .filas(ctx.filas)
                .localesNoEnCatalogo(localesNoEnCatalogo)
                .build();
    }

    private static List<FilaCruda> leerCrudasSeguro(InputStream stream, String archivo) {
        try {
            return LectorArchivoCatalogo.leer(stream, archivo);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo de catálogo: " + ex.getMessage(), ex);
        }
    }

    private void procesarFilaCatalogo(FilaCruda cruda, ContextoImportacion ctx) {
        String codigoClave = cruda.codigo() != null ? cruda.codigo().trim().toUpperCase(Locale.ROOT) : "";
        if (!codigoClave.isEmpty() && !ctx.codigosVistos.add(codigoClave)) {
            ctx.descartadosDuplicados++;
            return;
        }
        NombreFormatoOficial.Resultado parsed = NombreFormatoOficial.analizar(cruda.codigo(), cruda.nombre());
        if (parsed.tipo() == TipoFila.POR_REVISAR) {
            ctx.porRevisar++;
            ctx.filas.add(filaResultado(cruda.numeroFila(), "POR_REVISAR", "POR_REVISAR", parsed, null, parsed.motivo()));
            return;
        }
        if (parsed.tipo() == TipoFila.RECURSO) {
            procesarFilaRecurso(cruda.numeroFila(), codigoClave, parsed, ctx);
            return;
        }
        procesarFilaFormato(cruda.numeroFila(), codigoClave, parsed, ctx);
    }

    private void procesarFilaRecurso(int numFila, String codigoClave, NombreFormatoOficial.Resultado parsed, ContextoImportacion ctx) {
        EspecieModel esp = ctx.especiePorCanon.get(parsed.especieCanon());
        if (esp == null) {
            ctx.porRevisar++;
            ctx.filas.add(filaResultado(numFila, "RECURSO", "POR_REVISAR", parsed, null,
                    "Especie del recurso no encontrada en el maestro: '" + parsed.especieCanon() + "'."));
            return;
        }
        EspecieModel duenoCodigo = ctx.especiePorCodigo.get(codigoClave);
        if (duenoCodigo != null && !Objects.equals(duenoCodigo.getId(), esp.getId())) {
            ctx.porRevisar++;
            ctx.filas.add(filaResultado(numFila, "RECURSO", "POR_REVISAR", parsed, esp.getNombre(),
                    "El código Sernapesca " + parsed.codigo() + " ya pertenece a la especie '" + duenoCodigo.getNombre() + "'."));
            return;
        }
        String msg;
        if (Objects.equals(esp.getCodigoSernapesca(), parsed.codigo())) {
            msg = "Recurso vinculado a " + esp.getNombre() + " (código " + parsed.codigo() + ").";
        } else if (ctx.aplicar) {
            esp.setCodigoSernapesca(parsed.codigo());
            especieRepository.save(esp);
            ctx.especiePorCodigo.put(codigoClave, esp);
            msg = "Código Sernapesca " + parsed.codigo() + " asignado a " + esp.getNombre() + ".";
        } else {
            msg = "Vinculará código Sernapesca " + parsed.codigo() + " a " + esp.getNombre() + ".";
        }
        ctx.recursos++;
        ctx.filas.add(filaResultado(numFila, "RECURSO", "RECURSO", parsed, esp.getNombre(), msg));
    }

    private void procesarFilaFormato(int numFila, String codigoClave, NombreFormatoOficial.Resultado parsed, ContextoImportacion ctx) {
        EspecieModel esp = ctx.especiePorCanon.get(parsed.especieCanon());
        if (esp == null) {
            ctx.porRevisar++;
            ctx.filas.add(filaResultado(numFila, "PRODUCTO", "POR_REVISAR", parsed, null,
                    "Especie no encontrada en el maestro: '" + parsed.especieCanon() + "'."));
            return;
        }
        HumedadEstadoModel hum = ctx.humedadPorNombre.get(normalizarTextoClave(parsed.estadoHumedad()));
        if (hum == null) {
            ctx.porRevisar++;
            ctx.filas.add(filaResultado(numFila, "PRODUCTO", "POR_REVISAR", parsed, esp.getNombre(),
                    "Estado de humedad no encontrado en el maestro: '" + parsed.estadoHumedad() + "'."));
            return;
        }

        String calibreGuardar = (parsed.calibre() == null || parsed.calibre().isBlank()) ? null : parsed.calibre();
        ProductoModel existente = ctx.porCodigo.get(codigoClave);
        ProductoModel conflictoNombre = ctx.porNombre.get(parsed.nombreOficial().toLowerCase(Locale.ROOT));

        if (existente == null) {
            if (conflictoNombre != null) {
                ctx.porRevisar++;
                ctx.filas.add(filaResultado(numFila, "PRODUCTO", "POR_REVISAR", parsed, esp.getNombre(),
                        "Ya existe un producto con el mismo nombre (ID=" + conflictoNombre.getId()
                                + ", código=" + (conflictoNombre.getCodigo() != null ? conflictoNombre.getCodigo() : "sin código") + ")."));
                return;
            }
            ctx.nuevos++;
            if (ctx.aplicar) {
                ProductoModel guardado = productoRepository.save(ProductoModel.builder()
                        .codigo(parsed.codigo())
                        .nombre(parsed.nombreOficial())
                        .presentacion(parsed.presentacion())
                        .calibre(calibreGuardar)
                        .especie(esp)
                        .humedadEstado(hum)
                        .activo(true)
                        .fuente(ctx.fuente)
                        .actualizadoEn(LocalDateTime.now())
                        .actualizadoPor(ctx.usuarioNorm)
                        .build());
                ctx.porCodigo.put(codigoClave, guardado);
                ctx.porNombre.put(guardado.getNombre().toLowerCase(Locale.ROOT), guardado);
                ctx.idsPresentes.add(guardado.getId());
            }
            ctx.filas.add(filaResultado(numFila, "PRODUCTO", "NUEVO", parsed, esp.getNombre(), "Formato nuevo listo para crear."));
            return;
        }

        ctx.idsPresentes.add(existente.getId());
        if (conflictoNombre != null && !Objects.equals(conflictoNombre.getId(), existente.getId())) {
            ctx.porRevisar++;
            ctx.filas.add(filaResultado(numFila, "PRODUCTO", "POR_REVISAR", parsed, esp.getNombre(),
                    "El nombre oficial ya está asignado a otro producto (ID=" + conflictoNombre.getId() + ")."));
            return;
        }

        if (coincideConCatalogo(existente, parsed, calibreGuardar, esp, hum)) {
            ctx.sinCambio++;
            ctx.filas.add(filaResultado(numFila, "PRODUCTO", "SIN_CAMBIO", parsed, esp.getNombre(), "Sin cambios respecto al catálogo vigente."));
            return;
        }

        ctx.actualizados++;
        if (ctx.aplicar) {
            if (existente.getNombre() != null) {
                ctx.porNombre.remove(existente.getNombre().toLowerCase(Locale.ROOT));
            }
            existente.setNombre(parsed.nombreOficial());
            existente.setPresentacion(parsed.presentacion());
            existente.setCalibre(calibreGuardar);
            existente.setEspecie(esp);
            existente.setHumedadEstado(hum);
            existente.setActivo(true);
            existente.setFuente(ctx.fuente);
            existente.setActualizadoEn(LocalDateTime.now());
            existente.setActualizadoPor(ctx.usuarioNorm);
            ProductoModel guardado = productoRepository.save(existente);
            ctx.porNombre.put(guardado.getNombre().toLowerCase(Locale.ROOT), guardado);
        }
        ctx.filas.add(filaResultado(numFila, "PRODUCTO", "ACTUALIZADO", parsed, esp.getNombre(), "Actualizará metadatos oficiales y activación."));
    }

    private static boolean coincideConCatalogo(
            ProductoModel existente,
            NombreFormatoOficial.Resultado parsed,
            String calibreGuardar,
            EspecieModel esp,
            HumedadEstadoModel hum) {
        return Objects.equals(existente.getNombre(), parsed.nombreOficial())
                && Objects.equals(existente.getPresentacion(), parsed.presentacion())
                && Objects.equals(existente.getCalibre(), calibreGuardar)
                && Objects.equals(existente.getEspecie() != null ? existente.getEspecie().getId() : null, esp.getId())
                && Objects.equals(existente.getHumedadEstado() != null ? existente.getHumedadEstado().getId() : null, hum.getId())
                && Boolean.TRUE.equals(existente.getActivo());
    }

    private static final class ContextoImportacion {
        final boolean aplicar;
        final String fuente;
        final String usuarioNorm;
        final Map<String, EspecieModel> especiePorCanon;
        final Map<String, EspecieModel> especiePorCodigo;
        final Map<String, HumedadEstadoModel> humedadPorNombre;
        final Map<String, ProductoModel> porCodigo;
        final Map<String, ProductoModel> porNombre;
        final Set<String> codigosVistos = new HashSet<>();
        final Set<Long> idsPresentes = new HashSet<>();
        final List<FilaResultadoCatalogoDTO> filas = new ArrayList<>();
        int nuevos;
        int actualizados;
        int sinCambio;
        int recursos;
        int porRevisar;
        int descartadosDuplicados;

        ContextoImportacion(
                boolean aplicar,
                String fuente,
                String usuarioNorm,
                Map<String, EspecieModel> especiePorCanon,
                Map<String, EspecieModel> especiePorCodigo,
                Map<String, HumedadEstadoModel> humedadPorNombre,
                Map<String, ProductoModel> porCodigo,
                Map<String, ProductoModel> porNombre) {
            this.aplicar = aplicar;
            this.fuente = fuente;
            this.usuarioNorm = usuarioNorm;
            this.especiePorCanon = especiePorCanon;
            this.especiePorCodigo = especiePorCodigo;
            this.humedadPorNombre = humedadPorNombre;
            this.porCodigo = porCodigo;
            this.porNombre = porNombre;
        }
    }

    private static FilaResultadoCatalogoDTO filaResultado(
            int numeroFila,
            String tipo,
            String estado,
            NombreFormatoOficial.Resultado parsed,
            String especieNombre,
            String mensaje) {
        return FilaResultadoCatalogoDTO.builder()
                .numeroFila(numeroFila)
                .tipo(tipo)
                .estado(estado)
                .codigo(parsed.codigo())
                .nombreOficial(parsed.nombreOficial())
                .especieNombre(especieNombre != null ? especieNombre : parsed.especieCanon())
                .humedadNombre(parsed.estadoHumedad())
                .presentacion(parsed.presentacion())
                .calibre(parsed.calibre() != null && !parsed.calibre().isBlank() ? parsed.calibre() : null)
                .mensaje(mensaje)
                .build();
    }

    private static String limpiarNombreArchivo(String raw) {
        if (raw == null || raw.isBlank()) {
            return "catalogo.csv";
        }
        try {
            String base = Paths.get(raw.trim()).getFileName().toString();
            return base.isBlank() ? "catalogo.csv" : base;
        } catch (Exception ex) {
            return raw.trim();
        }
    }

    private static String construirFuenteCatalogo(String nombreArchivo, LocalDate fecha) {
        String sufijo = " (" + fecha + ")";
        int maxBase = 60 - sufijo.length();
        String base = nombreArchivo.length() > maxBase ? nombreArchivo.substring(0, Math.max(1, maxBase)) : nombreArchivo;
        return base + sufijo;
    }

    private static String normalizarTextoClave(String raw) {
        if (raw == null) return "";
        String sinAcentos = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinAcentos.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
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
        if (nombre.length() > 150) {
            throw new IllegalArgumentException("El nombre del formato no puede superar 150 caracteres.");
        }
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

        String presentacion = trimOrNull(req.getPresentacion(), 60);
        String calibre = trimOrNull(req.getCalibre(), 40);

        model.setCodigo(codigo);
        model.setNombre(nombre);
        model.setDescripcion(req.getDescripcion() != null ? req.getDescripcion().trim() : null);
        model.setPresentacion(presentacion);
        model.setCalibre(calibre);
        model.setHumedadEstado(humedad);
        model.setEspecie(especie);
        model.setActivo(req.getActivo() != null ? req.getActivo() : true);
        model.setFuente(req.getFuente() != null && !req.getFuente().isBlank() ? trimMax(req.getFuente().trim(), 60) : "LOCAL");
        model.setActualizadoEn(LocalDateTime.now());
        model.setActualizadoPor(normalizarUsuario(usuario));
    }

    private static String trimOrNull(String val, int maxLen) {
        if (val == null) return null;
        String t = val.trim();
        if (t.isEmpty()) return null;
        return t.length() > maxLen ? t.substring(0, maxLen) : t;
    }

    private static String trimMax(String val, int maxLen) {
        if (val == null) return null;
        return val.length() > maxLen ? val.substring(0, maxLen) : val;
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
                .presentacion(p.getPresentacion())
                .calibre(p.getCalibre())
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

