package mx.edu.itch.sigclem.alumnos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO.FilaConError;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

/**
 * Carga masiva de alumnos desde un archivo Excel (.xlsx) con un formato fijo (cambio 15). Crea las cuentas con la
 * contraseña inicial igual al número de control. Cada fila se procesa de forma independiente: una fila con error no
 * impide crear las demás, y al final se informa qué filas fallaron.
 *
 * <p>Columnas esperadas (la primera fila es el encabezado y se ignora):
 * A número de control · B nombre(s) · C apellido paterno · D apellido materno · E sexo (H/M) ·
 * F carrera (nombre o clave) · G semestre (1-12) · H correo (opcional) · I habla lengua materna (Sí/No).</p>
 */
@Service
@RequiredArgsConstructor
public class ImportacionAlumnosService {

    static final String[] ENCABEZADOS = {
            "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
            "Carrera (nombre o clave)", "Semestre (1-12)", "Correo (opcional)", "Habla lengua materna (Sí/No)" };

    private final AlumnoService alumnoService;
    private final CarreraRepository carreraRepository;

    public ResultadoCargaDTO importar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatosInvalidosException("ALUM-020", "Sube el archivo Excel con los alumnos.");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre != null && !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new DatosInvalidosException("ALUM-021", "El archivo debe ser un Excel con extensión .xlsx.");
        }
        Map<String, Carrera> carreras = indexarCarreras();
        DataFormatter formato = new DataFormatter(new Locale("es", "MX"));

        int creados = 0;
        int filas = 0;
        List<FilaConError> errores = new ArrayList<>();
        try (InputStream entrada = archivo.getInputStream(); Workbook libro = new XSSFWorkbook(entrada)) {
            Sheet hoja = libro.getSheetAt(0);
            for (int f = 1; f <= hoja.getLastRowNum(); f++) {
                Row fila = hoja.getRow(f);
                if (fila == null || filaVacia(fila, formato)) {
                    continue;
                }
                filas++;
                String numeroControl = celda(fila, 0, formato);
                try {
                    crear(fila, formato, carreras);
                    creados++;
                } catch (RuntimeException e) {
                    errores.add(new FilaConError(f + 1, numeroControl, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new DatosInvalidosException("ALUM-022", "No se pudo leer el archivo Excel; revisa que no esté dañado.");
        }
        if (filas == 0) {
            throw new DatosInvalidosException("ALUM-023", "El archivo no tiene filas de alumnos (después del encabezado).");
        }
        return new ResultadoCargaDTO(filas, creados, errores);
    }

    private void crear(Row fila, DataFormatter formato, Map<String, Carrera> carreras) {
        String numeroControl = celda(fila, 0, formato).toUpperCase(Locale.ROOT);
        String sexo = normalizarSexo(celda(fila, 4, formato));
        String carreraTexto = celda(fila, 5, formato);
        Carrera carrera = carreras.get(clave(carreraTexto));
        if (carrera == null) {
            throw new DatosInvalidosException("ALUM-024", "No se encontró la carrera «" + carreraTexto + "».");
        }
        Integer semestre = enteroSemestre(celda(fila, 6, formato));
        String correo = vacioANulo(celda(fila, 7, formato));
        boolean hablaLengua = esSi(celda(fila, 8, formato));
        String apellidoMaterno = vacioANulo(celda(fila, 3, formato));

        alumnoService.registrar(new RegistroAlumnoRequest(numeroControl, celda(fila, 1, formato),
                celda(fila, 2, formato), apellidoMaterno, sexo, carrera.getId(), semestre, correo, hablaLengua));
    }

    private Map<String, Carrera> indexarCarreras() {
        Map<String, Carrera> indice = new java.util.HashMap<>();
        for (Carrera c : carreraRepository.findAll()) {
            indice.put(clave(c.getNombre()), c);
            if (c.getClavePlan() != null) {
                indice.put(clave(c.getClavePlan()), c);
            }
        }
        return indice;
    }

    private static String clave(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizarSexo(String valor) {
        String v = valor.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("H") || v.startsWith("M")) {
            return v.substring(0, 1);
        }
        throw new DatosInvalidosException("ALUM-025", "El sexo debe ser H (hombre) o M (mujer).");
    }

    private static Integer enteroSemestre(String valor) {
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new DatosInvalidosException("ALUM-026", "El semestre «" + valor + "» no es un número.");
        }
    }

    private static boolean esSi(String valor) {
        String v = valor.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("s") || v.equals("x") || v.equals("1") || v.startsWith("y");
    }

    private static String vacioANulo(String valor) {
        String v = valor == null ? "" : valor.trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean filaVacia(Row fila, DataFormatter formato) {
        for (int c = 0; c <= 8; c++) {
            if (!celda(fila, c, formato).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String celda(Row fila, int columna, DataFormatter formato) {
        Cell c = fila.getCell(columna);
        return c == null ? "" : formato.formatCellValue(c).trim();
    }

    /** Genera la plantilla de Excel con el encabezado y una fila de ejemplo, para que el coordinador la llene. */
    public byte[] plantilla() {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Alumnos");
            Row encabezado = hoja.createRow(0);
            var negrita = libro.createFont();
            negrita.setBold(true);
            var estilo = libro.createCellStyle();
            estilo.setFont(negrita);
            for (int i = 0; i < ENCABEZADOS.length; i++) {
                Cell c = encabezado.createCell(i);
                c.setCellValue(ENCABEZADOS[i]);
                c.setCellStyle(estilo);
                hoja.setColumnWidth(i, 6000);
            }
            String[] ejemplo = { "22520001", "Ana", "López", "García", "M", "Ingeniería en Sistemas Computacionales",
                    "3", "ana@example.edu.mx", "No" };
            Row fila = hoja.createRow(1);
            for (int i = 0; i < ejemplo.length; i++) {
                fila.createCell(i).setCellValue(ejemplo[i]);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla de Excel.", e);
        }
    }
}
