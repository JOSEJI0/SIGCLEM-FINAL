package mx.edu.itch.sigclem.alumnos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.HistorialAcademico;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO.FilaConError;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;


@Service
@RequiredArgsConstructor
public class ImportacionAlumnosService {

    // --- CONSTANTES DEL NUEVO FORMATO (CONCENTRADO HISTÓRICO) ---
    private static final int COL_CONTROL = 0;
    private static final int COL_CARRERA = 1;
    private static final int COL_NOMBRE = 2;
    private static final int COL_PATERNO = 3;
    private static final int COL_MATERNO = 4;
    private static final int COL_INICIO_NIVELES = 5; // Columna F
    private static final int COLUMNAS_POR_NIVEL = 6;
    private static final int COL_LIBERADO = 71; // Columna BT

    private static final String[] NIVELES = {
            "Básico 1", "Básico 2", "Básico 3", "Básico 4", "Básico 5",
            "Intermedio 1", "Intermedio 2", "Intermedio 3", "Intermedio 4", "Intermedio 5",
            "Intensivo"
    };

    static final String[] ENCABEZADOS = {
            "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
            "Carrera (nombre o clave)", "Semestre (1-12)", "Correo (opcional)", "Habla lengua materna (Sí/No)" };

    private final AlumnoService alumnoService;
    private final CarreraRepository carreraRepository;
    
    // --- NUEVAS DEPENDENCIAS INYECTADAS ---
    private final AlumnoRepository alumnoRepository;
    private final HistorialAcademicoRepository historialRepository;

    @Transactional
    public ResultadoCargaDTO importar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatosInvalidosException("ALUM-020", "Sube el archivo Excel con los alumnos.");
        }
        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo != null && !nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new DatosInvalidosException("ALUM-021", "El archivo debe ser un Excel con extensión .xlsx.");
        }
        
        Map<String, Carrera> carreras = indexarCarreras();
        DataFormatter formato = new DataFormatter(new Locale("es", "MX"));

        int creados = 0;
        int nivelesImportados = 0; // Nuevo contador
        int filas = 0;
        List<FilaConError> errores = new ArrayList<>();

        try (InputStream entrada = archivo.getInputStream(); Workbook libro = new XSSFWorkbook(entrada)) {
            Sheet hoja = libro.getSheetAt(0); // SOLO LEER LA HOJA 1 "General"
            
            for (int f = 1; f <= hoja.getLastRowNum(); f++) {
                Row fila = hoja.getRow(f);
                
                // --- LÓGICA ANTERIOR COMENTADA ---
                /*
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
                */

                // --- NUEVA LÓGICA DE PROCESAMIENTO ---
                if (fila == null) continue;

                String numeroControl = obtenerTexto(fila, COL_CONTROL);
                String nombre = obtenerTexto(fila, COL_NOMBRE);
                
                // Tratamiento de filas vacías
                if (numeroControl.isEmpty() && nombre.isEmpty()) {
                    continue;
                }
                filas++;

                try {
                    // Generar control para externos si está vacío
                    if (numeroControl.isEmpty()) {
                        numeroControl = "EXT-" + f; 
                    }

                    // 1. Buscar o crear al Alumno
                    Alumno alumno = procesarAlumno(fila, formato, carreras, numeroControl, nombre);

                    // 2. Borrar historial previo (por si se vuelve a subir el Excel y se actualiza)
                    historialRepository.deleteByAlumnoId(alumno.getNumeroControl());

                    // 3. Iterar sobre los 11 niveles
                    for (int i = 0; i < NIVELES.length; i++) {
                        int colBase = COL_INICIO_NIVELES + (i * COLUMNAS_POR_NIVEL);
                        
                        String periodo = limpiarPeriodo(obtenerTexto(fila, colBase + 1));
                        
                        // SI EL PERIODO ESTÁ VACÍO, EL ALUMNO NO CURSÓ ESE NIVEL. SE SALTA.
                        if (periodo.isEmpty()) continue;

                        HistorialAcademico historial = new HistorialAcademico();
                        historial.setAlumno(alumno);
                        historial.setNivel(NIVELES[i]);
                        historial.setPeriodo(periodo);
                        historial.setFacilitador(obtenerTexto(fila, colBase + 2));
                        historial.setGrupo(obtenerTexto(fila, colBase + 3));
                        historial.setHorario(obtenerTexto(fila, colBase + 4));
                        historial.setAula(obtenerTexto(fila, colBase + 5));
                        historial.setCalificacion(limpiarCalificacion(obtenerTexto(fila, colBase)));
                        
                        historialRepository.save(historial);
                        nivelesImportados++;
                    }
                    
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
        
        // Retornamos el DTO (Asegúrate de agregar 'nivelesImportados' a tu ResultadoCargaDTO)
        return new ResultadoCargaDTO(filas, creados, nivelesImportados, errores);
    }

    /**
     * Nuevo método para procesar y guardar/actualizar los datos básicos del alumno.
     */
    private Alumno procesarAlumno(Row fila, DataFormatter formato, Map<String, Carrera> carreras, String numeroControl, String nombre) {
        String carreraTexto = obtenerTexto(fila, COL_CARRERA);
        Carrera carrera = carreras.get(clave(carreraTexto));
        if (carrera == null) {
            throw new DatosInvalidosException("ALUM-024", "No se encontró la carrera «" + carreraTexto + "».");
        }

        String apellidoPaterno = obtenerTexto(fila, COL_PATERNO);
        String apellidoMaterno = obtenerTexto(fila, COL_MATERNO);
        boolean liberado = esSi(obtenerTexto(fila, COL_LIBERADO));

        // Buscar si ya existe
        return alumnoRepository.findConCarreraByNumeroControl(numeroControl).map(existente -> {
            // Actualizar datos si ya existe
            existente.setNombre(nombre);
            existente.setApellidoPaterno(apellidoPaterno);
            existente.setApellidoMaterno(apellidoMaterno);
            existente.setCarrera(carrera);
            existente.setLiberado(liberado);
            return alumnoRepository.save(existente);
        }).orElseGet(() -> {
            // Crear nuevo si no existe.
            // NOTA: El Excel nuevo no trae Sexo, Semestre, Correo ni Habla Lengua Materna.
            // Pasamos valores por defecto para que no falle tu validación actual.
            alumnoService.registrar(new RegistroAlumnoRequest(
                    numeroControl, 
                    nombre,
                    apellidoPaterno, 
                    apellidoMaterno, 
                    "H", // Sexo por defecto (ajustar si es necesario)
                    carrera.getId(), 
                    1, // Semestre por defecto
                    null, // Correo nulo
                    false // Habla lengua materna por defecto
            ));
            
            // Recuperamos el alumno recién creado para obtener su ID
            Alumno nuevo = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                    .orElseThrow(() -> new DatosInvalidosException("ALUM-027", "Error al recuperar el alumno recién creado."));
            nuevo.setLiberado(liberado);
            return alumnoRepository.save(nuevo);
        });
    }

    // --- MÉTODOS DE LIMPIEZA Y EXTRACCIÓN ---

    private String obtenerTexto(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue()); // Evita decimales en No. Control
        }
        return cell.getStringCellValue().trim();
    }

    private Double limpiarCalificacion(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.toUpperCase().contains("NA") || raw.toUpperCase().contains("N/A")) {
            return null;
        }
        // Elimina textos como "90 (Noventa)" dejando solo el "90"
        String soloNumeros = raw.replaceAll("[^0-9.]", "");
        if (soloNumeros.isEmpty()) return null;
        try {
            return Double.parseDouble(soloNumeros);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String limpiarPeriodo(String periodo) {
        if (periodo == null) return "";
        // Convierte "2025-01-01 00:00:00" en "Enero 2025"
        if (periodo.contains("00:00:00")) {
            if (periodo.startsWith("2025-01")) return "Enero 2025";
            if (periodo.startsWith("2026-01")) return "Enero 2026";
            return "Enero del año correspondiente";
        }
        return periodo.trim();
    }

    // --- MÉTODOS ANTIGUOS COMENTADOS O ADAPTADOS ---

    /*
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
    */

    private Map<String, Carrera> indexarCarreras() {
        Map<String, Carrera> indice = new HashMap<>();
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

    private static boolean esSi(String valor) {
        String v = valor.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("s") || v.equals("x") || v.equals("1") || v.startsWith("y");
    }

    // Métodos que ya no se usan para este Excel, pero los dejamos comentados por si acaso
    /*
    private static String normalizarSexo(String valor) { ... }
    private static Integer enteroSemestre(String valor) { ... }
    private static String vacioANulo(String valor) { ... }
    private static boolean filaVacia(Row fila, DataFormatter formato) { ... }
    private static String celda(Row fila, int columna, DataFormatter formato) { ... }
    */

    /** Genera la plantilla de Excel (Mantenemos la tuya original por ahora). */
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