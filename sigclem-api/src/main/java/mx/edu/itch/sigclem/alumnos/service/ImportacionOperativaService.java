package mx.edu.itch.sigclem.alumnos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoImportacionOperativaDTO;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoImportacionOperativaDTO.CredencialTemporal;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoImportacionOperativaDTO.ErrorFila;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.catalogos.domain.Aula;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Modalidad;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.repository.AulaRepository;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorCreadoDTO;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorRequest;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.facilitadores.service.FacilitadorService;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.dto.CrearGrupoRequest;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.HorarioRequest;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.service.GrupoService;

@Service
@RequiredArgsConstructor
public class ImportacionOperativaService {

    private static final String[] ALUMNOS = {
            "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
            "Carrera (nombre o clave)", "Semestre (1-12)", "Correo", "Habla lengua materna (Sí/No)"
    };
    private static final String[] FACILITADORES = {
            "Nombre(s)", "Apellido paterno", "Apellido materno", "Correo", "Teléfono (opcional)"
    };
    private static final String[] GRUPOS = {
            "Periodo", "Fecha inicio", "Fecha fin", "Nivel", "Modalidad", "Clave de grupo",
            "Correo del facilitador", "Aula", "Cupo", "Curso Intensivo (Sí/No)"
    };
    private static final String[] HORARIOS = {
            "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Día (1-6)", "Hora inicio", "Hora fin"
    };

    private final Validator validator;
    private final AlumnoService alumnoService;
    private final AlumnoRepository alumnoRepository;
    private final CarreraRepository carreraRepository;
    private final NivelRepository nivelRepository;
    private final ModalidadRepository modalidadRepository;
    private final AulaRepository aulaRepository;
    private final PeriodoRepository periodoRepository;
    private final FacilitadorRepository facilitadorRepository;
    private final FacilitadorService facilitadorService;
    private final GrupoRepository grupoRepository;
    private final GrupoService grupoService;

    public byte[] plantilla() {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle encabezado = libro.createCellStyle();
            var fuente = libro.createFont();
            fuente.setBold(true);
            encabezado.setFont(fuente);
            encabezado.setWrapText(true);

            Sheet instrucciones = libro.createSheet("Instrucciones");
            instrucciones.createRow(0).createCell(0).setCellValue("Carga operativa SIGCLEM");
            instrucciones.createRow(2).createCell(0).setCellValue(
                    "Usa las hojas Alumnos, Facilitadores, Grupos y Horarios. El sistema crea cuentas, periodos, modalidades y aulas faltantes.");
            instrucciones.createRow(3).createCell(0).setCellValue(
                    "Esta carga no inscribe alumnos, no crea pagos y no importa calificaciones ni avance académico.");
            instrucciones.createRow(4).createCell(0).setCellValue(
                    "Los números de control y correos deben ser únicos. Los alumnos existentes se conservan sin cambios.");
            instrucciones.createRow(5).createCell(0).setCellValue(
                    "Facilitadores nuevos reciben contraseña temporal; se mostrará una sola vez al terminar. Entrégala de forma segura.");
            instrucciones.createRow(6).createCell(0).setCellValue(
                    "Nivel y carrera deben coincidir con los catálogos SIGCLEM. Grupos nuevos requieren al menos un horario válido.");
            instrucciones.createRow(7).createCell(0).setCellValue(
                    "Fechas: AAAA-MM-DD. Día: 1 lunes, 2 martes, 3 miércoles, 4 jueves, 5 viernes, 6 sábado.");
            instrucciones.createRow(8).createCell(0).setCellValue(
                    "Captura el número de control como texto para conservar ceros iniciales. Los alumnos no se vinculan a grupos en esta carga.");
            instrucciones.setColumnWidth(0, 18000);

            crearHoja(libro, "Alumnos", ALUMNOS, encabezado);
            crearHoja(libro, "Facilitadores", FACILITADORES, encabezado);
            crearHoja(libro, "Grupos", GRUPOS, encabezado);
            crearHoja(libro, "Horarios", HORARIOS, encabezado);
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible crear la plantilla de carga operativa.", e);
        }
    }

    public ResultadoImportacionOperativaDTO importar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatosInvalidosException("ALUM-030", "Sube el archivo Excel de carga operativa.");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre != null && !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new DatosInvalidosException("ALUM-031", "El archivo debe ser un Excel .xlsx.");
        }

        try (InputStream entrada = archivo.getInputStream(); Workbook libro = new XSSFWorkbook(entrada)) {
            return importar(libro);
        } catch (IOException e) {
            throw new DatosInvalidosException("ALUM-032", "No fue posible leer el archivo Excel.");
        }
    }

    private ResultadoImportacionOperativaDTO importar(Workbook libro) {
        Sheet hojaAlumnos = libro.getSheet("Alumnos");
        Sheet hojaFacilitadores = libro.getSheet("Facilitadores");
        Sheet hojaGrupos = libro.getSheet("Grupos");
        Sheet hojaHorarios = libro.getSheet("Horarios");
        if (hojaAlumnos == null || hojaFacilitadores == null || hojaGrupos == null || hojaHorarios == null) {
            throw new DatosInvalidosException("ALUM-033",
                    "El archivo debe incluir las hojas Alumnos, Facilitadores, Grupos y Horarios.");
        }

        DataFormatter formatter = new DataFormatter(new Locale("es", "MX"));
        validarEncabezados(hojaAlumnos, ALUMNOS, formatter);
        validarEncabezados(hojaFacilitadores, FACILITADORES, formatter);
        validarEncabezados(hojaGrupos, GRUPOS, formatter);
        validarEncabezados(hojaHorarios, HORARIOS, formatter);

        List<ErrorFila> errores = new ArrayList<>();
        List<CredencialTemporal> credenciales = new ArrayList<>();
        Map<String, Carrera> carreras = indexarCarreras();
        Map<String, Nivel> niveles = indexarNiveles();
        CargaAlumnos cargaAlumnos = importarAlumnos(hojaAlumnos, formatter, carreras, errores);
        CargaFacilitadores cargaFacilitadores = importarFacilitadores(
                hojaFacilitadores, formatter, credenciales, errores);
        Map<ClaveTextoGrupo, List<HorarioRequest>> horarios = importarHorarios(
                hojaHorarios, formatter, errores);
        Set<ClaveTextoGrupo> horariosInvalidos = invalidarGruposConFilasInvalidas(hojaHorarios, formatter, errores);

        Map<String, Periodo> periodos = new HashMap<>();
        periodoRepository.findAll().forEach(p -> periodos.put(clave(p.getNombre()), p));
        Map<String, Modalidad> modalidades = new HashMap<>();
        modalidadRepository.findAll().forEach(m -> modalidades.put(clave(m.getNombre()), m));
        Map<String, Aula> aulas = new HashMap<>();
        aulaRepository.findAll().forEach(a -> aulas.put(clave(a.getNombre()), a));
        Set<ClaveTextoGrupo> gruposExistentes = new HashSet<>();
        grupoRepository.findAllConDatosHistoricos().forEach(g -> gruposExistentes.add(ClaveTextoGrupo.de(g)));

        int filas = contarFilas(hojaAlumnos, formatter) + contarFilas(hojaFacilitadores, formatter)
                + contarFilas(hojaGrupos, formatter) + contarFilas(hojaHorarios, formatter);
        int alumnosCreados = cargaAlumnos.creados();
        int alumnosExistentes = cargaAlumnos.existentes();
        int facilitadoresCreados = cargaFacilitadores.creados();
        int facilitadoresExistentes = cargaFacilitadores.existentes();
        int gruposCreados = 0;
        int gruposYaExistentes = 0;
        int horariosCreados = 0;

        Set<ClaveTextoGrupo> clavesNuevas = new HashSet<>();
        for (int i = 1; i <= hojaGrupos.getLastRowNum(); i++) {
            Row row = hojaGrupos.getRow(i);
            if (vacia(row, formatter)) continue;
            String identificador = texto(row, 5, formatter).trim().toUpperCase(Locale.ROOT);
            try {
                GrupoEntrada entrada = leerGrupo(row, i + 1, formatter, niveles, periodos,
                        cargaFacilitadores.facilitadores(), horarios, horariosInvalidos);
                if (!clavesNuevas.add(entrada.claveTexto())) {
                    throw new IllegalArgumentException("La clave del grupo está duplicada en la hoja Grupos.");
                }
                if (gruposExistentes.contains(entrada.claveTexto())) {
                    gruposYaExistentes++;
                    continue;
                }
                List<HorarioRequest> sesiones = horarios.getOrDefault(entrada.claveTexto(), List.of());
                if (sesiones.isEmpty()) {
                    throw new IllegalArgumentException("Agrega al menos un horario válido para este grupo.");
                }
                Periodo periodo = obtenerPeriodo(entrada, periodos);
                Modalidad modalidad = obtenerModalidad(entrada.modalidad(), modalidades);
                Aula aula = obtenerAula(entrada.aula(), aulas);
                CrearGrupoRequest solicitud = new CrearGrupoRequest(
                        entrada.clave(), entrada.nivel().getId(), entrada.intensivo(), periodo.getId(),
                        modalidad.getId(), entrada.facilitador().getId(), aula.getId(), entrada.cupo(), sesiones);
                GrupoDTO creado = grupoService.abrir(solicitud);
                gruposCreados++;
                horariosCreados += sesiones.size();
                gruposExistentes.add(entrada.claveTexto());
            } catch (RuntimeException e) {
                errores.add(error("Grupos", i + 1, identificador, e));
            }
        }

        return new ResultadoImportacionOperativaDTO(filas, alumnosCreados, alumnosExistentes,
                facilitadoresCreados, facilitadoresExistentes, gruposCreados, gruposYaExistentes,
                horariosCreados, List.copyOf(credenciales), List.copyOf(errores));
    }

    private CargaAlumnos importarAlumnos(Sheet hoja, DataFormatter formatter,
            Map<String, Carrera> carreras, List<ErrorFila> errores) {
        Map<String, Alumno> resultado = new HashMap<>();
        Set<String> creados = new HashSet<>();
        Set<String> existentes = new HashSet<>();
        int totalCreados = 0;
        int totalExistentes = 0;
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (vacia(row, formatter)) continue;
            String control = texto(row, 0, formatter).trim().toUpperCase(Locale.ROOT);
            try {
                if (control.isBlank()) throw new IllegalArgumentException("El número de control es obligatorio.");
                Alumno existente = alumnoRepository.findConCarreraByNumeroControl(control).orElse(null);
                if (existente != null) {
                    resultado.put(control, existente);
                    if (!creados.contains(control) && existentes.add(control)) totalExistentes++;
                    continue;
                }
                String carreraTexto = textoObligatorio(row, 5, formatter, "Carrera");
                Carrera carrera = carreras.get(clave(carreraTexto));
                if (carrera == null) throw new IllegalArgumentException("La carrera no existe en el catálogo.");
                String sexo = textoObligatorio(row, 4, formatter, "Sexo").toUpperCase(Locale.ROOT);
                RegistroAlumnoRequest solicitud = new RegistroAlumnoRequest(control,
                        textoObligatorio(row, 1, formatter, "Nombre"),
                        textoObligatorio(row, 2, formatter, "Apellido paterno"),
                        texto(row, 3, formatter), sexo, carrera.getId(),
                        enteroObligatorio(row, 6, formatter, "Semestre"),
                        vacioANulo(texto(row, 7, formatter)), esSi(texto(row, 8, formatter)));
                validar(solicitud);
                alumnoService.registrar(solicitud);
                Alumno creado = alumnoRepository.findConCarreraByNumeroControl(control)
                        .orElseThrow(() -> new IllegalStateException("No se pudo recuperar el alumno creado."));
                resultado.put(control, creado);
                creados.add(control);
                totalCreados++;
            } catch (RuntimeException e) {
                errores.add(error("Alumnos", i + 1, control, e));
            }
        }
        return new CargaAlumnos(resultado, totalCreados, totalExistentes);
    }

    private CargaFacilitadores importarFacilitadores(Sheet hoja, DataFormatter formatter,
            List<CredencialTemporal> credenciales, List<ErrorFila> errores) {
        Map<String, Facilitador> resultado = new HashMap<>();
        facilitadorRepository.findAll().forEach(f -> resultado.put(clave(f.getCorreo()), f));
        Set<String> creados = new HashSet<>();
        Set<String> existentes = new HashSet<>();
        int totalCreados = 0;
        int totalExistentes = 0;
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (vacia(row, formatter)) continue;
            String correo = clave(texto(row, 3, formatter));
            try {
                Facilitador existente = resultado.get(correo);
                if (existente != null) {
                    if (!creados.contains(correo) && existentes.add(correo)) totalExistentes++;
                    continue;
                }
                FacilitadorRequest solicitud = new FacilitadorRequest(
                        textoObligatorio(row, 0, formatter, "Nombre"),
                        textoObligatorio(row, 1, formatter, "Apellido paterno"),
                        vacioANulo(texto(row, 2, formatter)),
                        textoObligatorio(row, 3, formatter, "Correo"),
                        vacioANulo(texto(row, 4, formatter)), null);
                validar(solicitud);
                FacilitadorCreadoDTO creado = facilitadorService.registrarConCambioObligatorio(solicitud);
                Facilitador entidad = facilitadorRepository.findByCorreoIgnoreCase(creado.facilitador().correo())
                        .orElseThrow(() -> new IllegalStateException(
                                "No se pudo recuperar el facilitador creado."));
                resultado.put(correo, entidad);
                credenciales.add(new CredencialTemporal(
                        creado.facilitador().nombreCompleto(), creado.usuario(), creado.contrasenaTemporal()));
                creados.add(correo);
                totalCreados++;
            } catch (RuntimeException e) {
                errores.add(error("Facilitadores", i + 1, correo, e));
            }
        }
        return new CargaFacilitadores(resultado, totalCreados, totalExistentes);
    }

    private Map<ClaveTextoGrupo, List<HorarioRequest>> importarHorarios(
            Sheet hoja, DataFormatter formatter, List<ErrorFila> errores) {
        Map<ClaveTextoGrupo, List<HorarioRequest>> resultado = new HashMap<>();
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (vacia(row, formatter)) continue;
            String identificador = texto(row, 3, formatter).trim().toUpperCase(Locale.ROOT);
            try {
                ClaveTextoGrupo claveGrupo = leerClaveTexto(row, formatter);
                HorarioRequest horario = new HorarioRequest(
                        enteroObligatorio(row, 4, formatter, "Día"),
                        hora(row, 5, formatter, "Hora inicio"),
                        hora(row, 6, formatter, "Hora fin"));
                validar(horario);
                resultado.computeIfAbsent(claveGrupo, k -> new ArrayList<>()).add(horario);
            } catch (RuntimeException e) {
                errores.add(error("Horarios", i + 1, identificador, e));
            }
        }
        return resultado;
    }

    private Set<ClaveTextoGrupo> invalidarGruposConFilasInvalidas(
            Sheet hoja, DataFormatter formatter, List<ErrorFila> errores) {
        Set<ClaveTextoGrupo> invalidos = new HashSet<>();
        Set<Integer> filasConError = errores.stream().filter(e -> e.hoja().equals("Horarios"))
                .map(ErrorFila::fila).collect(java.util.stream.Collectors.toSet());
        for (Integer filaNumero : filasConError) {
            Row row = hoja.getRow(filaNumero - 1);
            try {
                invalidos.add(leerClaveTexto(row, formatter));
            } catch (RuntimeException ignorada) {
                // The row itself already has a visible validation error.
            }
        }
        return invalidos;
    }

    private GrupoEntrada leerGrupo(Row row, int fila, DataFormatter formatter,
            Map<String, Nivel> niveles, Map<String, Periodo> periodos, Map<String, Facilitador> facilitadores,
            Map<ClaveTextoGrupo, List<HorarioRequest>> horarios, Set<ClaveTextoGrupo> horariosInvalidos) {
        String nombrePeriodo = textoObligatorio(row, 0, formatter, "Periodo");
        textoObligatorio(row, 1, formatter, "Fecha inicio");
        textoObligatorio(row, 2, formatter, "Fecha fin");
        LocalDate inicio = fecha(row, 1, formatter, "Fecha inicio");
        LocalDate fin = fecha(row, 2, formatter, "Fecha fin");
        if (fin.isBefore(inicio)) throw new IllegalArgumentException("La fecha fin debe ser posterior al inicio.");
        if (fin.isBefore(LocalDate.now())) throw new IllegalArgumentException("No se pueden crear grupos de periodos concluidos.");
        Nivel nivel = niveles.get(clave(textoObligatorio(row, 3, formatter, "Nivel")));
        if (nivel == null) throw new IllegalArgumentException("El nivel no existe en el catálogo.");
        String nombreModalidad = textoObligatorio(row, 4, formatter, "Modalidad");
        String claveGrupo = textoObligatorio(row, 5, formatter, "Clave de grupo").toUpperCase(Locale.ROOT);
        if (!claveGrupo.matches("[A-Z]{1,2}")) throw new IllegalArgumentException("La clave debe tener una o dos letras.");
        String correoFacilitador = clave(textoObligatorio(row, 6, formatter, "Correo del facilitador"));
        Facilitador facilitador = facilitadores.get(correoFacilitador);
        if (facilitador == null) throw new IllegalArgumentException("El facilitador no existe o su fila tiene errores.");
        String nombreAula = textoObligatorio(row, 7, formatter, "Aula");
        int cupo = enteroObligatorio(row, 8, formatter, "Cupo");
        if (cupo < 1 || cupo > 255) throw new IllegalArgumentException("El cupo debe estar entre 1 y 255.");
        boolean intensivo = esSi(texto(row, 9, formatter));
        if (intensivo && nivel.getNumero() != 1) {
            throw new IllegalArgumentException("Para Intensivo selecciona Básico 1 como nivel de referencia.");
        }
        validarLongitud(nombrePeriodo, 30, "Periodo");
        validarLongitud(nombreModalidad, 20, "Modalidad");
        validarLongitud(nombreAula, 30, "Aula");

        ClaveTextoGrupo claveTexto = new ClaveTextoGrupo(
                clave(nombrePeriodo), clave(nivel.getNombre()), clave(nombreModalidad), claveGrupo);
        if (horariosInvalidos.contains(claveTexto)) {
            throw new IllegalArgumentException("Corrige todos los horarios de este grupo antes de importarlo.");
        }
        if (horarios.getOrDefault(claveTexto, List.of()).isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un horario válido para este grupo.");
        }
        Periodo periodoExistente = periodos.get(clave(nombrePeriodo));
        if (periodoExistente != null
                && (!periodoExistente.getFechaInicio().equals(inicio) || !periodoExistente.getFechaFin().equals(fin))) {
            throw new IllegalArgumentException("Las fechas no coinciden con el periodo ya registrado.");
        }
        return new GrupoEntrada(nombrePeriodo, inicio, fin, nivel, nombreModalidad, claveGrupo,
                nombreAula, cupo, intensivo, facilitador, claveTexto);
    }

    private Periodo obtenerPeriodo(GrupoEntrada entrada, Map<String, Periodo> periodos) {
        String clave = clave(entrada.periodo());
        Periodo existente = periodos.get(clave);
        if (existente != null) return existente;
        Periodo nuevo = new Periodo();
        nuevo.setNombre(entrada.periodo());
        nuevo.setFechaInicio(entrada.fechaInicio());
        nuevo.setFechaFin(entrada.fechaFin());
        nuevo = periodoRepository.save(nuevo);
        periodos.put(clave, nuevo);
        return nuevo;
    }

    private Modalidad obtenerModalidad(String nombre, Map<String, Modalidad> modalidades) {
        return modalidades.computeIfAbsent(clave(nombre), key -> {
            Modalidad nueva = new Modalidad();
            nueva.setNombre(nombre);
            return modalidadRepository.save(nueva);
        });
    }

    private Aula obtenerAula(String nombre, Map<String, Aula> aulas) {
        return aulas.computeIfAbsent(clave(nombre), key -> {
            Aula nueva = new Aula();
            nueva.setNombre(nombre);
            return aulaRepository.save(nueva);
        });
    }

    private Map<String, Carrera> indexarCarreras() {
        Map<String, Carrera> resultado = new HashMap<>();
        for (Carrera carrera : carreraRepository.findAll()) {
            resultado.put(clave(carrera.getNombre()), carrera);
            resultado.put(clave(carrera.getClavePlan()), carrera);
        }
        return resultado;
    }

    private Map<String, Nivel> indexarNiveles() {
        Map<String, Nivel> resultado = new HashMap<>();
        for (Nivel nivel : nivelRepository.findAllByOrderByNumeroAsc()) {
            resultado.put(clave(nivel.getNombre()), nivel);
        }
        return resultado;
    }

    private void validar(Object solicitud) {
        var violaciones = validator.validate(solicitud);
        if (!violaciones.isEmpty()) throw new IllegalArgumentException(violaciones.iterator().next().getMessage());
    }

    private static Sheet crearHoja(Workbook libro, String nombre, String[] titulos, CellStyle estilo) {
        Sheet hoja = libro.createSheet(nombre);
        Row encabezados = hoja.createRow(0);
        for (int i = 0; i < titulos.length; i++) {
            Cell celda = encabezados.createCell(i);
            celda.setCellValue(titulos[i]);
            celda.setCellStyle(estilo);
            hoja.setColumnWidth(i, 6000);
        }
        hoja.createFreezePane(0, 1);
        return hoja;
    }

    private static void validarEncabezados(Sheet hoja, String[] esperados, DataFormatter formatter) {
        Row fila = hoja.getRow(0);
        for (int columna = 0; columna < esperados.length; columna++) {
            if (!esperados[columna].equals(texto(fila, columna, formatter))) {
                throw new DatosInvalidosException("ALUM-034",
                        "La hoja «" + hoja.getSheetName() + "» no coincide con la plantilla de carga operativa.");
            }
        }
    }

    private static ClaveTextoGrupo leerClaveTexto(Row row, DataFormatter formatter) {
        String periodo = textoObligatorio(row, 0, formatter, "Periodo");
        String nivel = textoObligatorio(row, 1, formatter, "Nivel");
        String modalidad = textoObligatorio(row, 2, formatter, "Modalidad");
        String claveGrupo = textoObligatorio(row, 3, formatter, "Clave de grupo").toUpperCase(Locale.ROOT);
        return new ClaveTextoGrupo(clave(periodo), clave(nivel), clave(modalidad), claveGrupo);
    }

    private static LocalDate fecha(Row row, int columna, DataFormatter formatter, String campo) {
        Cell celda = row == null ? null : row.getCell(columna);
        if (celda != null && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalDate();
        }
        String valor = textoObligatorio(row, columna, formatter, campo);
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            for (DateTimeFormatter formato : List.of(
                    DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("M/d/uuuu"))) {
                try {
                    return LocalDate.parse(valor, formato);
                } catch (DateTimeParseException ignorada) {
                    continue;
                }
            }
            throw new IllegalArgumentException("«" + campo + "» debe ser una fecha válida (AAAA-MM-DD).");
        }
    }

    private static LocalTime hora(Row row, int columna, DataFormatter formatter, String campo) {
        Cell celda = row == null ? null : row.getCell(columna);
        if (celda != null && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalTime();
        }
        String valor = textoObligatorio(row, columna, formatter, campo);
        for (DateTimeFormatter formato : List.of(
                DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm:ss"), DateTimeFormatter.ofPattern("HH:mm:ss"))) {
            try {
                return LocalTime.parse(valor, formato);
            } catch (DateTimeParseException ignorada) {
                continue;
            }
        }
        throw new IllegalArgumentException("«" + campo + "» debe tener formato HH:mm.");
    }

    private static int enteroObligatorio(Row row, int columna, DataFormatter formatter, String campo) {
        String valor = textoObligatorio(row, columna, formatter, campo);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("«" + campo + "» debe ser un número entero.");
        }
    }

    private static int contarFilas(Sheet hoja, DataFormatter formatter) {
        int filas = 0;
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            if (!vacia(hoja.getRow(i), formatter)) filas++;
        }
        return filas;
    }

    private static String texto(Row fila, int columna, DataFormatter formatter) {
        return fila == null || fila.getCell(columna) == null ? "" : formatter.formatCellValue(fila.getCell(columna)).trim();
    }

    private static String textoObligatorio(Row fila, int columna, DataFormatter formatter, String campo) {
        String valor = texto(fila, columna, formatter);
        if (valor.isBlank()) throw new IllegalArgumentException("«" + campo + "» es obligatorio.");
        return valor;
    }

    private static boolean vacia(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell celda : row) if (!formatter.formatCellValue(celda).isBlank()) return false;
        return true;
    }

    private static boolean esSi(String valor) {
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        return normalizado.startsWith("s") || normalizado.equals("x") || normalizado.equals("1")
                || normalizado.equals("true");
    }

    private static String clave(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static void validarLongitud(String valor, int maximo, String campo) {
        if (valor.length() > maximo) throw new IllegalArgumentException("«" + campo + "» excede " + maximo + " caracteres.");
    }

    private static ErrorFila error(String hoja, int fila, String identificador, RuntimeException e) {
        String mensaje = e.getMessage() == null ? "No se pudo procesar el registro." : e.getMessage();
        return new ErrorFila(hoja, fila, identificador, mensaje);
    }

    private record GrupoEntrada(String periodo, LocalDate fechaInicio, LocalDate fechaFin,
            Nivel nivel, String modalidad, String clave, String aula, int cupo, boolean intensivo,
            Facilitador facilitador, ClaveTextoGrupo claveTexto) {
    }

    private record CargaAlumnos(Map<String, Alumno> alumnos, int creados, int existentes) {
    }

    private record CargaFacilitadores(Map<String, Facilitador> facilitadores, int creados, int existentes) {
    }

    private record ClaveTextoGrupo(String periodo, String nivel, String modalidad, String clave) {
        static ClaveTextoGrupo de(Grupo grupo) {
            return new ClaveTextoGrupo(ImportacionOperativaService.clave(grupo.getPeriodo().getNombre()),
                    ImportacionOperativaService.clave(grupo.getNivel().getNombre()),
                    ImportacionOperativaService.clave(grupo.getModalidad().getNombre()),
                    grupo.getClave().toUpperCase(Locale.ROOT));
        }
    }
}
