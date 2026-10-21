package mx.edu.itch.sigclem.constancias.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.constancias.domain.ConfiguracionConstancia;
import mx.edu.itch.sigclem.constancias.domain.ImagenConstancia;
import mx.edu.itch.sigclem.constancias.dto.ConfiguracionConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;
import mx.edu.itch.sigclem.constancias.repository.ConfiguracionConstanciaRepository;
import mx.edu.itch.sigclem.constancias.repository.ImagenConstanciaRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Firmantes, textos y membrete de las constancias. Coordinación los actualiza cuando cambian las personas en los
 * puestos o los logos del año; solo afectan a las constancias que se generen después del cambio.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionConstanciaService {

    private static final long TAMANO_MAXIMO_IMAGEN = 2L * 1024 * 1024;
    private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G' };
    private static final byte[] FIRMA_JPG = { (byte) 0xff, (byte) 0xd8, (byte) 0xff };

    private final ConfiguracionConstanciaRepository configuracionRepository;
    private final ImagenConstanciaRepository imagenRepository;
    private final UsuarioRepository usuarioRepository;

    /** Datos vigentes para generar un PDF. */
    @Transactional(readOnly = true)
    public MembreteConstancia membreteVigente() {
        ConfiguracionConstancia c = obtenerEntidad();
        return new MembreteConstancia(
                new MembreteConstancia.Firmante(c.getSubdirectorNombre(), "F".equals(c.getSubdirectorGenero())),
                new MembreteConstancia.Firmante(c.getJefeDgtvNombre(), "F".equals(c.getJefeDgtvGenero())),
                new MembreteConstancia.Firmante(c.getCoordinadorNombre(), "F".equals(c.getCoordinadorGenero())),
                c.getLema(), c.getRegistroCoordinacion(), c.getPrefijoOficio(), c.getCopias(),
                contenido(ImagenConstancia.Tipo.ENCABEZADO), contenido(ImagenConstancia.Tipo.PIE),
                contenido(ImagenConstancia.Tipo.LOGO_COORDINACION));
    }

    @Transactional(readOnly = true)
    public ConfiguracionConstanciaDTO obtener() {
        return dto(obtenerEntidad());
    }

    @Transactional
    public ConfiguracionConstanciaDTO actualizar(ConfiguracionConstanciaDTO.Request r, String nombreUsuario) {
        ConfiguracionConstancia c = obtenerEntidad();
        c.setSubdirectorNombre(limpiar(r.subdirectorNombre()));
        c.setSubdirectorGenero(r.subdirectorGenero());
        c.setJefeDgtvNombre(limpiar(r.jefeDgtvNombre()));
        c.setJefeDgtvGenero(r.jefeDgtvGenero());
        c.setCoordinadorNombre(limpiar(r.coordinadorNombre()));
        c.setCoordinadorGenero(r.coordinadorGenero());
        c.setLema(r.lema().strip());
        c.setRegistroCoordinacion(limpiar(r.registroCoordinacion()));
        c.setPrefijoOficio(limpiar(r.prefijoOficio()));
        c.setCopias(r.copias().strip());
        c.setFechaActualizacion(LocalDateTime.now());
        c.setUsuarioActualiza(usuarioRepository.findByNombreUsuario(nombreUsuario).orElse(null));
        return dto(configuracionRepository.save(c));
    }

    @Transactional
    public void guardarImagen(ImagenConstancia.Tipo tipo, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > TAMANO_MAXIMO_IMAGEN) {
            throw new DatosInvalidosException("CFG-001", "Adjunta una imagen PNG o JPG de hasta 2 MB.");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen.", e);
        }
        String tipoContenido = empiezaCon(contenido, FIRMA_PNG) ? "image/png"
                : empiezaCon(contenido, FIRMA_JPG) ? "image/jpeg" : null;
        if (tipoContenido == null) {
            throw new DatosInvalidosException("CFG-001", "La imagen debe ser PNG o JPG.");
        }
        verificarDimensiones(contenido);
        ImagenConstancia imagen = imagenRepository.findById(tipo).orElseGet(ImagenConstancia::new);
        imagen.setTipo(tipo);
        imagen.setContenido(contenido);
        imagen.setTipoContenido(tipoContenido);
        imagen.setFechaActualizacion(LocalDateTime.now());
        imagenRepository.save(imagen);
    }

    @Transactional(readOnly = true)
    public ImagenConstancia obtenerImagen(ImagenConstancia.Tipo tipo) {
        return imagenRepository.findById(tipo).orElseThrow(() -> new RecursoNoEncontradoException("CFG-002",
                "No se ha cargado esa imagen; las constancias usan la predeterminada."));
    }

    @Transactional
    public void eliminarImagen(ImagenConstancia.Tipo tipo) {
        imagenRepository.deleteById(tipo);
    }

    /** Convierte «encabezado», «pie» o «logo-coordinacion» (como aparece en la URL) en el tipo de imagen. */
    public static ImagenConstancia.Tipo tipoDesdeRuta(String valor) {
        try {
            return ImagenConstancia.Tipo.valueOf(valor.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new RecursoNoEncontradoException("CFG-003", "Tipo de imagen no válido: usa encabezado, pie o logo-coordinacion.");
        }
    }

    private ConfiguracionConstancia obtenerEntidad() {
        return configuracionRepository.findById(ConfiguracionConstancia.ID).orElseThrow(() ->
                new RecursoNoEncontradoException("CFG-004", "Falta la configuración de constancias (migración V14)."));
    }

    private byte[] contenido(ImagenConstancia.Tipo tipo) {
        return imagenRepository.findById(tipo).map(ImagenConstancia::getContenido).orElse(null);
    }

    private ConfiguracionConstanciaDTO dto(ConfiguracionConstancia c) {
        Map<String, LocalDateTime> imagenes = new LinkedHashMap<>();
        for (ImagenConstancia.Tipo tipo : ImagenConstancia.Tipo.values()) {
            imagenes.put(tipo.name(), null);
        }
        imagenRepository.findAll().forEach(i -> imagenes.put(i.getTipo().name(), i.getFechaActualizacion()));
        return new ConfiguracionConstanciaDTO(c.getSubdirectorNombre(), c.getSubdirectorGenero(),
                c.getJefeDgtvNombre(), c.getJefeDgtvGenero(), c.getCoordinadorNombre(), c.getCoordinadorGenero(),
                c.getLema(), c.getRegistroCoordinacion(), c.getPrefijoOficio(), c.getCopias(),
                c.getFechaActualizacion(),
                c.getUsuarioActualiza() == null ? null : c.getUsuarioActualiza().getNombreUsuario(), imagenes);
    }

    private static String limpiar(String valor) {
        return valor.trim().replaceAll("\\s+", " ");
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        return contenido.length >= firma.length && Arrays.equals(Arrays.copyOf(contenido, firma.length), firma);
    }

    private static void verificarDimensiones(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new DatosInvalidosException("CFG-001", "La imagen no se pudo leer.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                if (lector.getWidth(0) > 6000 || lector.getHeight(0) > 6000) {
                    throw new DatosInvalidosException("CFG-001", "La imagen no debe exceder 6000 × 6000 píxeles.");
                }
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new DatosInvalidosException("CFG-001", "La imagen no se pudo leer.");
        }
    }
}
