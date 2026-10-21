package mx.edu.itch.sigclem.lenguasmaternas.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;

@Service
public class ArchivoLenguaMaternaService {

    private static final int MAXIMO_BYTES = 5 * 1024 * 1024;
    private static final byte[] PDF = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final Path directorio;

    public ArchivoLenguaMaternaService(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio, "lenguas-maternas").toAbsolutePath().normalize();
    }

    public String guardar(MultipartFile archivo) {
        if (archivo.isEmpty() || archivo.getSize() > MAXIMO_BYTES) {
            throw new DatosInvalidosException("LENG-001", "Adjunta un documento de hasta 5 MB.");
        }
        try (InputStream contenido = archivo.getInputStream()) {
            byte[] firma = contenido.readNBytes(8);
            String extension = extension(firma);
            String nombre = UUID.randomUUID() + extension;
            Path destino = directorio.resolve(nombre).normalize();
            if (!destino.getParent().equals(directorio)) {
                throw new IllegalStateException("La ruta del documento no es válida.");
            }
            Files.createDirectories(directorio);
            try (InputStream copia = archivo.getInputStream()) {
                Files.copy(copia, destino);
            }
            return nombre;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo almacenar el documento de lengua materna.", e);
        }
    }

    public Resource cargar(String nombre) {
        if (nombre == null || !nombre.matches("[0-9a-fA-F-]{36}\\.(pdf|png|jpg)")) {
            throw new RecursoNoEncontradoException("LENG-002", "No se encontró el documento.");
        }
        Path ruta = directorio.resolve(nombre).normalize();
        if (!ruta.getParent().equals(directorio) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("LENG-002", "No se encontró el documento.");
        }
        try {
            return new UrlResource(ruta.toUri());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el documento.", e);
        }
    }

    public String tipoContenido(String nombre) {
        if (nombre.endsWith(".pdf")) return "application/pdf";
        if (nombre.endsWith(".png")) return "image/png";
        if (nombre.endsWith(".jpg")) return "image/jpeg";
        throw new RecursoNoEncontradoException("LENG-002", "No se encontró el documento.");
    }

    public void eliminar(String nombre) {
        if (nombre == null || !nombre.matches("[0-9a-fA-F-]{36}\\.(pdf|png|jpg)")) {
            throw new IllegalArgumentException("El nombre del documento almacenado no es válido.");
        }
        try {
            Files.deleteIfExists(directorio.resolve(nombre).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo retirar el documento anterior.", e);
        }
    }

    private String extension(byte[] firma) {
        if (firma.length >= PDF.length && Arrays.equals(Arrays.copyOf(firma, PDF.length), PDF)) return ".pdf";
        if (Arrays.equals(firma, PNG)) return ".png";
        if (firma.length >= JPEG.length && Arrays.equals(Arrays.copyOf(firma, JPEG.length), JPEG)) return ".jpg";
        throw new DatosInvalidosException(
                "LENG-003", "El documento debe ser un PDF, PNG o JPEG válido.");
    }
}
