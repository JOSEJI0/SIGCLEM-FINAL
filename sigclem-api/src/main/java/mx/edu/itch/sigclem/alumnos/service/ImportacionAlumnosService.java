package mx.edu.itch.sigclem.alumnos.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCompletarDatosDTO;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

/**
 * Punto de entrada de la importación histórica desde Excel. Reconoce dos formatos:
 * <ul>
 * <li><b>Concentrado de la coordinación</b> (una fila por alumno con los bloques Básico 1 … Intermedio 5 e
 * Intensivo): lo procesa {@link ImportacionConcentradoService}, sin inventar datos; opcionalmente se adjunta la
 * matrícula de control escolar para completar semestre, sexo, correo y lengua indígena.</li>
 * <li><b>Plantilla completa</b> (hojas Alumnos, Grupos, Inscripciones, Horarios): reconstruye grupos y actas
 * cerrados; la procesa {@link ImportacionHistoricaService}. Sirve para periodos con todos los datos.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ImportacionAlumnosService {

    private final ImportacionHistoricaService historicoService;
    private final ImportacionConcentradoService concentradoService;

    public ResultadoCargaDTO importar(MultipartFile archivo) {
        return importar(archivo, null);
    }

    public ResultadoCargaDTO importar(MultipartFile archivo, MultipartFile matricula) {
        validarExcel(archivo, "Sube el archivo Excel con los alumnos.");
        try (InputStream entrada = archivo.getInputStream(); Workbook libro = new XSSFWorkbook(entrada)) {
            if (libro.getSheet("Grupos") != null && libro.getSheet("Inscripciones") != null) {
                return historicoService.importar(libro);
            }
            if (!ImportacionConcentradoService.esConcentrado(libro)) {
                throw new DatosInvalidosException("ALUM-029", "No se reconoce el formato. Usa el concentrado "
                        + "(columnas No. Control, Carrera, Nombre, Apellido Paterno, Básico 1 … Intermedio 5, Intensivo, "
                        + "Liberado) o la plantilla completa con las hojas Grupos e Inscripciones.");
            }
            if (matricula == null || matricula.isEmpty()) {
                return concentradoService.importar(libro, null);
            }
            validarExcel(matricula, "Sube la matrícula en Excel.");
            try (InputStream entradaMatricula = matricula.getInputStream();
                    Workbook libroMatricula = new XSSFWorkbook(entradaMatricula)) {
                return concentradoService.importar(libro, libroMatricula);
            }
        } catch (IOException e) {
            throw new DatosInvalidosException("ALUM-022", "No se pudo leer el archivo Excel; revisa que no esté dañado.");
        }
    }

    public ResultadoCompletarDatosDTO completarExistentes(MultipartFile archivo) {
        validarExcel(archivo, "Sube el archivo Excel con los datos existentes.");
        try (InputStream entrada = archivo.getInputStream(); Workbook libro = new XSSFWorkbook(entrada)) {
            return historicoService.completarExistentes(libro);
        } catch (IOException e) {
            throw new DatosInvalidosException("ALUM-022", "No se pudo leer el archivo Excel; revisa que no esté dañado.");
        }
    }

    public byte[] plantilla() {
        return historicoService.plantilla();
    }

    private static void validarExcel(MultipartFile archivo, String mensajeVacio) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatosInvalidosException("ALUM-020", mensajeVacio);
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre != null && !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new DatosInvalidosException("ALUM-021", "El archivo debe ser un Excel con extensión .xlsx.");
        }
    }
}
