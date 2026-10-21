package mx.edu.itch.sigclem.pagos.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;

@Service
public class ArchivoComprobanteService {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    /** Formatos aceptados; se reconocen por su firma de bytes, no por la extensión que envía el navegador. */
    public enum Formato {
        JPG("jpg", MediaType.IMAGE_JPEG, new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff }),
        PNG("png", MediaType.IMAGE_PNG, new byte[] { (byte) 0x89, 'P', 'N', 'G' }),
        PDF("pdf", MediaType.APPLICATION_PDF, new byte[] { '%', 'P', 'D', 'F' });

        private final String extension;
        private final MediaType tipo;
        private final byte[] firma;

        Formato(String extension, MediaType tipo, byte[] firma) {
            this.extension = extension;
            this.tipo = tipo;
            this.firma = firma;
        }

        public String extension() {
            return extension;
        }

        public MediaType tipo() {
            return tipo;
        }
    }

    private final Path directorio;

    public ArchivoComprobanteService(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    /** Lee el archivo subido y verifica tamaño y formato (PDF, JPG o PNG). */
    public byte[] leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("PAG-001", "Adjunta el comprobante en PDF, JPG o PNG de hasta 5 MB.");
        }
        try {
            byte[] contenido = archivo.getBytes();
            detectarFormato(contenido);
            return contenido;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo adjunto.", e);
        }
    }

    public Formato detectarFormato(byte[] contenido) {
        for (Formato formato : Formato.values()) {
            if (contenido.length >= formato.firma.length
                    && Arrays.equals(Arrays.copyOf(contenido, formato.firma.length), formato.firma)) {
                return formato;
            }
        }
        throw new DatosInvalidosException("PAG-003", "El comprobante debe ser un PDF, JPG o PNG válido.");
    }

    public String guardar(byte[] contenido) {
        String nombre = UUID.randomUUID() + "." + detectarFormato(contenido).extension();
        Path destino = directorio.resolve(nombre).normalize();
        if (!destino.getParent().equals(directorio)) {
            throw new IllegalStateException("La ruta del comprobante no es válida.");
        }
        try {
            Files.createDirectories(directorio);
            Files.write(destino, contenido);
            return nombre;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo almacenar el comprobante de pago.", e);
        }
    }

    public void eliminar(String nombre) {
        try {
            Files.deleteIfExists(directorio.resolve(nombre).normalize());
        } catch (IOException e) {
            // El archivo huérfano no afecta la operación; se conserva para revisión manual.
        }
    }

    public Resource cargar(String nombre) {
        if (nombre == null || !nombre.matches("[0-9a-fA-F-]{36}\\.(?:jpg|png|pdf)")) {
            throw new RecursoNoEncontradoException("PAG-002", "No se encontró el archivo del comprobante.");
        }
        Path ruta = directorio.resolve(nombre).normalize();
        if (!ruta.getParent().equals(directorio) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("PAG-002", "No se encontró el archivo del comprobante.");
        }
        try {
            return new UrlResource(ruta.toUri());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el comprobante.", e);
        }
    }

    /** Contenido del archivo almacenado, para volver a leer el QR o el texto al reverificar. */
    public byte[] contenido(String nombre) {
        try {
            return cargar(nombre).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el comprobante almacenado.", e);
        }
    }

    public MediaType tipoContenido(String nombre) {
        if (nombre != null && nombre.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (nombre != null && nombre.endsWith(".png")) return MediaType.IMAGE_PNG;
        return MediaType.IMAGE_JPEG;
    }
}
