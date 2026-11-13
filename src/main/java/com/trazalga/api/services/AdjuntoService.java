package com.trazalga.api.services;

import com.trazalga.api.exceptions.DeclaracionCongeladaException;
import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.AdjuntoModel;
import com.trazalga.api.repositories.IAdjuntoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento seguro en disco para respaldo fotográfico/PDF de voucher y guía (NA.4).
 */
@Service
@Slf4j
public class AdjuntoService {

    private static final int MAX_DIM_PX = 2000;
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final IAdjuntoRepository adjuntoRepository;
    private final String baseDir;
    private final long maxBytes;

    @Autowired
    public AdjuntoService(
            IAdjuntoRepository adjuntoRepository,
            @Value("${trazalga.adjuntos.dir:uploads/adjuntos}") String baseDir,
            @Value("${trazalga.adjuntos.max-mb:10}") long maxMb) {
        this.adjuntoRepository = adjuntoRepository;
        this.baseDir = baseDir;
        this.maxBytes = Math.max(1L, maxMb) * 1024L * 1024L;
    }

    public record AdjuntoContenido(AdjuntoModel metadata, Resource resource) {
    }

    @Transactional
    public AdjuntoModel guardar(MultipartFile file, Long usuarioId, String tipoDocumento) {
        if (file == null || file.isEmpty()) {
            throw new ValidacionPlantaException("Debe adjuntar un archivo válido.");
        }
        if (file.getSize() > maxBytes) {
            throw new ValidacionPlantaException("El archivo supera el tamaño máximo permitido (" + (maxBytes / (1024 * 1024)) + " MB).");
        }

        String nombreOrig = sanitizarNombre(file.getOriginalFilename());
        String mime = normalizarMime(file.getContentType(), nombreOrig);
        if (!MIME_PERMITIDOS.contains(mime)) {
            throw new ValidacionPlantaException("Formato de archivo no permitido (" + mime + "). Use JPG, PNG, WEBP o PDF.");
        }

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidacionPlantaException("No se pudo leer el archivo adjunto.");
        }

        Procesado proc = procesarContenido(rawBytes, mime);
        String sha256 = sha256Hex(proc.bytes);

        Optional<AdjuntoModel> existente = adjuntoRepository.findFirstBySha256(sha256);
        if (existente.isPresent()) {
            Path pExist = resolveSafe(existente.get().getRutaRelativa());
            if (Files.exists(pExist)) {
                return existente.get();
            }
        }

        LocalDate hoy = LocalDate.now();
        String uuid = UUID.randomUUID().toString();
        String ext = extensionDeMime(proc.mime);
        String rutaRel = String.format("%04d/%02d/%s.%s", hoy.getYear(), hoy.getMonthValue(), uuid, ext);
        Path destino = resolveSafe(rutaRel);

        escribirAtomico(destino, proc.bytes);

        AdjuntoModel model = AdjuntoModel.builder()
                .uuid(uuid)
                .nombreOriginal(nombreOrig)
                .mimeType(proc.mime)
                .tamanoBytes((long) proc.bytes.length)
                .sha256(sha256)
                .rutaRelativa(rutaRel)
                .tipoDocumento(tipoDocumento != null && !tipoDocumento.isBlank() ? tipoDocumento.trim().toUpperCase(Locale.ROOT) : null)
                .subidoPorUsuarioId(usuarioId)
                .build();
        return adjuntoRepository.save(model);
    }

    @Transactional(readOnly = true)
    public Optional<AdjuntoContenido> cargarPorUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        return adjuntoRepository.findByUuid(uuid.trim()).flatMap(meta -> {
            Path path = resolveSafe(meta.getRutaRelativa());
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.of(new AdjuntoContenido(meta, new FileSystemResource(path)));
        });
    }

    /**
     * Elimina un adjunto solo si la declaración asociada no está congelada ni sigue referenciado.
     */
    @Transactional
    public void eliminarSiPermitido(String uuid, boolean declaracionCongelada) {
        if (declaracionCongelada) {
            throw new DeclaracionCongeladaException("ADJUNTO", null, "No es posible eliminar evidencia adjunta de una declaración congelada.", java.util.List.of());
        }
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        adjuntoRepository.findByUuid(uuid.trim()).ifPresent(meta -> {
            String ruta = meta.getRutaRelativa();
            adjuntoRepository.delete(meta);
            if (adjuntoRepository.countByRutaRelativa(ruta) == 0L) {
                try {
                    Files.deleteIfExists(resolveSafe(ruta));
                } catch (IOException ex) {
                    log.warn("No se pudo borrar adjunto en disco {}: {}", ruta, ex.getMessage());
                }
            }
        });
    }

    public Path resolveSafe(String rutaRelativa) {
        Path root = Paths.get(baseDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(rutaRelativa).normalize();
        if (!resolved.startsWith(root)) {
            throw new ValidacionPlantaException("Ruta de adjunto inválida.");
        }
        return resolved;
    }

    private Procesado procesarContenido(byte[] raw, String mime) {
        if ("application/pdf".equals(mime)) {
            if (raw.length < 4 || raw[0] != '%' || raw[1] != 'P' || raw[2] != 'D' || raw[3] != 'F') {
                throw new ValidacionPlantaException("El archivo no corresponde a un PDF válido.");
            }
            return new Procesado(raw, mime);
        }

        if ("image/webp".equals(mime)) {
            boolean esWebp = raw.length >= 12 && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F'
                    && raw[8] == 'W' && raw[9] == 'E' && raw[10] == 'B' && raw[11] == 'P';
            if (!esWebp) {
                throw new ValidacionPlantaException("La imagen WEBP no es válida.");
            }
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
                if (img == null) {
                    return new Procesado(raw, mime);
                }
                if (Math.max(img.getWidth(), img.getHeight()) <= MAX_DIM_PX) {
                    return new Procesado(raw, mime);
                }
                return redimensionar(img, false);
            } catch (IOException e) {
                return new Procesado(raw, mime);
            }
        }

        // JPEG o PNG
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) {
                throw new ValidacionPlantaException("No se pudo decodificar la imagen adjunta.");
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (Math.max(w, h) <= MAX_DIM_PX) {
                return new Procesado(raw, mime);
            }
            boolean conAlfa = "image/png".equals(mime) && img.getColorModel() != null && img.getColorModel().hasAlpha();
            return redimensionar(img, conAlfa);
        } catch (ValidacionPlantaException v) {
            throw v;
        } catch (IOException e) {
            throw new ValidacionPlantaException("No se pudo procesar la imagen adjunta.");
        }
    }

    private Procesado redimensionar(BufferedImage src, boolean pngConAlfa) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = (double) MAX_DIM_PX / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, pngConAlfa ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!pngConAlfa) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, nw, nh);
            }
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String fmt = pngConAlfa ? "png" : "jpg";
        ImageIO.write(out, fmt, baos);
        return new Procesado(baos.toByteArray(), pngConAlfa ? "image/png" : "image/jpeg");
    }

    private void escribirAtomico(Path destino, byte[] bytes) {
        try {
            Files.createDirectories(destino.getParent());
            Path tmp = destino.resolveSibling(destino.getFileName().toString() + ".tmp");
            Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error guardando adjunto en disco: " + e.getMessage(), e);
        }
    }

    private static String sanitizarNombre(String raw) {
        String base = raw != null ? Paths.get(raw).getFileName().toString().trim() : "adjunto";
        if (base.isEmpty()) base = "adjunto";
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String normalizarMime(String contentType, String filename) {
        String ct = contentType != null ? contentType.toLowerCase(Locale.ROOT).split(";")[0].trim() : "";
        if ("image/jpg".equals(ct)) ct = "image/jpeg";
        if (!ct.isEmpty() && !"application/octet-stream".equals(ct)) {
            return ct;
        }
        String fn = filename.toLowerCase(Locale.ROOT);
        if (fn.endsWith(".jpg") || fn.endsWith(".jpeg")) return "image/jpeg";
        if (fn.endsWith(".png")) return "image/png";
        if (fn.endsWith(".webp")) return "image/webp";
        if (fn.endsWith(".pdf")) return "application/pdf";
        return ct;
    }

    private static String extensionDeMime(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            default -> "jpg";
        };
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    private record Procesado(byte[] bytes, String mime) {
    }
}
