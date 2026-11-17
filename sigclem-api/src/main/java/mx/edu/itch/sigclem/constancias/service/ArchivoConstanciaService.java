package mx.edu.itch.sigclem.constancias.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;

@Service
public class ArchivoConstanciaService {

    private final Path directorio;

    public ArchivoConstanciaService(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio, "constancias").toAbsolutePath().normalize();
    }

    public String guardar(Integer id, byte[] pdf) {
        String nombre = id + ".pdf";
        Path destino = directorio.resolve(nombre).normalize();
        if (!destino.getParent().equals(directorio)) {
            throw new IllegalArgumentException("La ruta de la constancia no es válida.");
        }
        Path temporal = directorio.resolve(id + ".tmp").normalize();
        try {
            Files.createDirectories(directorio);
            Files.write(temporal, pdf);
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            return "constancias/" + nombre;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException limpieza) {
                e.addSuppressed(limpieza);
            }
            throw new UncheckedIOException("No se pudo almacenar la constancia.", e);
        }
    }

    public String guardarWord(String tipo, Integer id, byte[] word) {
        validarIdentificadorWord(tipo);
        String nombre = tipo + "-" + id + ".docx";
        Path destino = directorio.resolve(nombre).normalize();
        Path temporal = directorio.resolve(tipo + "-" + id + ".tmp").normalize();
        if (!destino.getParent().equals(directorio) || !temporal.getParent().equals(directorio)) {
            throw new IllegalArgumentException("La ruta de la constancia Word no es válida.");
        }
        try {
            Files.createDirectories(directorio);
            Files.write(temporal, word);
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            return "constancias/" + nombre;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException limpieza) {
                e.addSuppressed(limpieza);
            }
            throw new UncheckedIOException("No se pudo almacenar la constancia Word.", e);
        }
    }

    public Resource cargar(String archivo) {
        if (archivo == null || !archivo.matches("constancias/[0-9]+\\.pdf")) {
            throw new RecursoNoEncontradoException("CON-006", "No se encontró el PDF de la constancia.");
        }
        String nombre = archivo.substring("constancias/".length());
        Path ruta = directorio.resolve(nombre).normalize();
        if (!ruta.getParent().equals(directorio) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("CON-006", "No se encontró el PDF de la constancia.");
        }
        try {
            return new UrlResource(ruta.toUri());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el PDF de la constancia.", e);
        }
    }

    public Resource cargarWord(String archivo) {
        if (archivo == null || !archivo.matches("constancias/(ingles|lengua-materna)-[0-9]+\\.docx")) {
            throw new RecursoNoEncontradoException("CON-006", "No se encontró la constancia Word.");
        }
        String nombre = archivo.substring("constancias/".length());
        Path ruta = directorio.resolve(nombre).normalize();
        if (!ruta.getParent().equals(directorio) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("CON-006", "No se encontró la constancia Word.");
        }
        try {
            return new UrlResource(ruta.toUri());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir la constancia Word.", e);
        }
    }

    public void eliminar(String archivo) {
        if (archivo == null || !archivo.matches("constancias/[0-9]+\\.pdf")) {
            throw new IllegalArgumentException("El nombre del PDF de constancia no es válido.");
        }
        try {
            Files.deleteIfExists(directorio.resolve(archivo.substring("constancias/".length())).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo retirar el PDF de la constancia.", e);
        }
    }

    public void eliminarWord(String archivo) {
        if (archivo == null || !archivo.matches("constancias/(ingles|lengua-materna)-[0-9]+\\.docx")) {
            throw new IllegalArgumentException("El nombre de la constancia Word no es válido.");
        }
        try {
            Files.deleteIfExists(directorio.resolve(archivo.substring("constancias/".length())).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo retirar la constancia Word.", e);
        }
    }

    private static void validarIdentificadorWord(String tipo) {
        if (!"ingles".equals(tipo) && !"lengua-materna".equals(tipo)) {
            throw new IllegalArgumentException("El tipo de constancia Word no es válido.");
        }
    }
}
