package mx.edu.itch.sigclem.alumnos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.domain.HistorialAcademico;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO;
import mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO.FilaConError;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository;
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
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Horario;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;

@Service
@RequiredArgsConstructor
public class ImportacionHistoricaService {

    private static final String[] ALUMNOS = {
            "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
            "Carrera (nombre o clave)", "Semestre (1-12)", "Correo", "Habla lengua materna (Sí/No)"
    };
    private static final String[] GRUPOS = {
            "Periodo", "Fecha inicio", "Fecha fin", "Nivel", "Modalidad", "Clave de grupo",
            "Correo del facilitador", "Facilitador", "Aula", "Cupo", "Estatus del acta",
            "Fecha de cierre", "Cerrada por", "Observación de cierre", "Grupo original (referencia)",
            "Curso Intensivo (Sí/No)"
    };
    private static final String[] INSCRIPCIONES = {
            "Número de control", "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Oportunidad",
            "Calificación", "Avance pendiente", "Nivel acreditado (Intensivo)"
    };
    private static final String[] HORARIOS = {
            "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Día (1-6)", "Hora inicio", "Hora fin"
    };
    private static final String[] HISTORIAL_INFORMATIVO = {
            "Número de control", "Nivel", "Periodo", "Facilitador", "Grupo de referencia",
            "Horario", "Aula", "Calificación", "Observación"
    };

    private final AlumnoService alumnoService;
    private final Validator validator;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final HistorialAcademicoRepository historialRepository;
    private final CarreraRepository carreraRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final PeriodoRepository periodoRepository;
    private final NivelRepository nivelRepository;
    private final ModalidadRepository modalidadRepository;
    private final AulaRepository aulaRepository;
    private final FacilitadorRepository facilitadorRepository;

    @Transactional(readOnly = true)
    public byte[] plantilla() {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            var encabezado = libro.createCellStyle();
            var fuente = libro.createFont();
            fuente.setBold(true);
            encabezado.setFont(fuente);
            encabezado.setWrapText(true);

            Sheet instrucciones = libro.createSheet("Instrucciones");
            instrucciones.createRow(0).createCell(0).setCellValue("Histórico académico SIGCLEM");
            instrucciones.createRow(2).createCell(0).setCellValue(
                    "El libro contiene los grupos con acta cerrada y sus inscripciones que ya existen en SIGCLEM.");
            instrucciones.createRow(3).createCell(0).setCellValue(
                    "Se puede volver a importar sin borrar datos: los grupos e inscripciones se identifican por sus claves.");
            instrucciones.createRow(4).createCell(0).setCellValue(
                    "Para agregar registros, completa las hojas Alumnos, Grupos, Inscripciones y Horarios.");
            instrucciones.createRow(5).createCell(0).setCellValue(
                    "Usa catálogos existentes para nivel, modalidad, carrera y facilitador. Periodos y aulas pueden crearse al importar.");
            instrucciones.createRow(6).createCell(0).setCellValue(
                    "Los registros se importan como históricos cerrados. No se crean cargos ni comprobantes.");
            instrucciones.createRow(7).createCell(0).setCellValue(
                    "No elimines ni cambies las claves de grupos ya exportados; los campos de alumnos existentes no se sobrescriben.");
            instrucciones.createRow(8).createCell(0).setCellValue(
                    "Para grupos Intensivos marca «Sí» en Curso Intensivo y captura el nivel acreditado de cada alumno en Inscripciones.");
            instrucciones.createRow(9).createCell(0).setCellValue(
                    "Las dos columnas nuevas son opcionales al importar plantillas antiguas; no borres las columnas existentes.");
            instrucciones.setColumnWidth(0, 15000);

            Sheet alumnos = crearHoja(libro, "Alumnos", ALUMNOS, encabezado);
            Sheet grupos = crearHoja(libro, "Grupos", GRUPOS, encabezado);
            Sheet inscripciones = crearHoja(libro, "Inscripciones", INSCRIPCIONES, encabezado);
            Sheet horarios = crearHoja(libro, "Horarios", HORARIOS, encabezado);
            Sheet hojaHistorialInformativo = crearHoja(
                    libro, "Historial_Informativo", HISTORIAL_INFORMATIVO, encabezado);

            List<Grupo> historicos = grupoRepository.findAllConDatosHistoricos().stream()
                    .filter(g -> "Cerrada".equalsIgnoreCase(g.getEstatusActa()))
                    .sorted(Comparator.comparing((Grupo g) -> g.getPeriodo().getFechaInicio())
                            .thenComparing(g -> g.getNivel().getNumero()).thenComparing(Grupo::getClave))
                    .toList();
            var gruposHistoricos = historicos.stream().collect(
                    java.util.stream.Collectors.toMap(Grupo::getId, g -> g));
            List<Inscripcion> inscripcionesHistoricas = inscripcionRepository.findAllConDatosHistoricos().stream()
                    .filter(i -> gruposHistoricos.containsKey(i.getGrupo().getId()))
                    .sorted(Comparator.comparing((Inscripcion i) -> i.getGrupo().getPeriodo().getFechaInicio())
                            .thenComparing(i -> i.getGrupo().getNivel().getNumero())
                            .thenComparing(i -> i.getGrupo().getClave())
                            .thenComparing(i -> i.getAlumno().getNumeroControl()))
                    .toList();
            List<HistorialAcademico> registrosInformativos = historialRepository.findAllConAlumno();

            Map<String, Alumno> alumnosHistoricos = new LinkedHashMap<>();
            for (Inscripcion inscripcion : inscripcionesHistoricas) {
                alumnosHistoricos.putIfAbsent(inscripcion.getAlumno().getNumeroControl(), inscripcion.getAlumno());
            }

            for (HistorialAcademico historial : registrosInformativos) {
                alumnosHistoricos.putIfAbsent(historial.getAlumno().getNumeroControl(), historial.getAlumno());
            }
            int filaAlumno = 1;
            for (Alumno alumno : alumnosHistoricos.values()) {
                escribir(alumnos.createRow(filaAlumno++),
                        alumno.getNumeroControl(), alumno.getNombre(), alumno.getApellidoPaterno(),
                        alumno.getApellidoMaterno(), alumno.getSexo(),
                        alumno.getCarrera() == null ? "" : alumno.getCarrera().getNombre(),
                        alumno.getSemestre(), alumno.getCorreo(), alumno.isHablaLenguaMaterna() ? "Sí" : "No");
            }

            Map<ClaveGrupo, Grupo> claves = new HashMap<>();
            int filaGrupo = 1;
            int filaHorario = 1;
            for (Grupo grupo : historicos) {
                ClaveGrupo clave = ClaveGrupo.de(grupo);
                claves.put(clave, grupo);
                escribir(grupos.createRow(filaGrupo++),
                        grupo.getPeriodo().getNombre(), grupo.getPeriodo().getFechaInicio(),
                        grupo.getPeriodo().getFechaFin(), grupo.getNivel().getNombre(),
                        grupo.getModalidad().getNombre(), grupo.getClave(), grupo.getFacilitador().getCorreo(),
                        grupo.getFacilitador().nombreCompleto(), grupo.getAula().getNombre(), grupo.getCupo(),
                        grupo.getEstatusActa(), grupo.getFechaCierreActa(), grupo.getCerradaPor(),
                        grupo.getObservacionCierre(), grupo.getReferenciaHistorica(),
                        grupo.isIntensivo() ? "Sí" : "No");

                for (Horario horario : grupo.getHorarios()) {
                    escribir(horarios.createRow(filaHorario++), grupo.getPeriodo().getNombre(),
                            grupo.getNivel().getNombre(), grupo.getModalidad().getNombre(), grupo.getClave(),
                            horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFin());
                }
            }

            int filaInscripcion = 1;
            for (Inscripcion inscripcion : inscripcionesHistoricas) {
                Grupo grupo = inscripcion.getGrupo();
                escribir(inscripciones.createRow(filaInscripcion++),
                        inscripcion.getAlumno().getNumeroControl(), grupo.getPeriodo().getNombre(),
                        grupo.getNivel().getNombre(), grupo.getModalidad().getNombre(), grupo.getClave(),
                        inscripcion.getOportunidad(), inscripcion.getCalificacionFinal(),
                        inscripcion.isAvancePendiente() ? "Sí" : "No",
                        inscripcion.getNivelAcreditado() == null ? "" : inscripcion.getNivelAcreditado().getNombre());
            }
            int filaHistorial = 1;
            for (HistorialAcademico historial : registrosInformativos) {
                escribir(hojaHistorialInformativo.createRow(filaHistorial++),
                        historial.getAlumno().getNumeroControl(), historial.getNivel(), historial.getPeriodo(),
                        historial.getFacilitador(), historial.getGrupo(), historial.getHorario(),
                        historial.getAula(), historial.getCalificacion(), historial.getObservacionHistorica());
            }
            for (Sheet hoja : List.of(alumnos, grupos, inscripciones, horarios, hojaHistorialInformativo)) {
                hoja.createFreezePane(0, 1);
                hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, Math.max(0, hoja.getLastRowNum()), 0, hoja.getRow(0).getLastCellNum() - 1));
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla de histórico.", e);
        }
    }

    @Transactional
    public ResultadoCargaDTO importar(Workbook libro) {
        Sheet hojaAlumnos = libro.getSheet("Alumnos");
        Sheet hojaGrupos = libro.getSheet("Grupos");
        Sheet hojaInscripciones = libro.getSheet("Inscripciones");
        Sheet hojaHorarios = libro.getSheet("Horarios");
        Sheet hojaHistorial = libro.getSheet("Historial_Informativo");
        if (hojaHistorial == null && libro.getSheet("Intensivo_Historico") != null) {
            return importarFormatoAnterior(libro);
        }
        if (hojaAlumnos == null || hojaGrupos == null || hojaInscripciones == null || hojaHorarios == null
                || hojaHistorial == null) {
            throw new DatosInvalidosException("ALUM-025",
                    "El histórico debe incluir las hojas Alumnos, Grupos, Inscripciones, Horarios e Historial_Informativo.");
        }

        DataFormatter formatter = new DataFormatter(new Locale("es", "MX"));
        validarEncabezados(hojaAlumnos, ALUMNOS, formatter);
        validarEncabezados(hojaGrupos, java.util.Arrays.copyOf(GRUPOS, GRUPOS.length - 1), formatter);
        validarEncabezadoOpcional(hojaGrupos, GRUPOS.length - 1, GRUPOS[GRUPOS.length - 1], formatter);
        validarEncabezados(hojaInscripciones, java.util.Arrays.copyOf(INSCRIPCIONES, INSCRIPCIONES.length - 1), formatter);
        validarEncabezadoOpcional(hojaInscripciones, INSCRIPCIONES.length - 1,
                INSCRIPCIONES[INSCRIPCIONES.length - 1], formatter);
        validarEncabezados(hojaHorarios, HORARIOS, formatter);
        validarEncabezados(hojaHistorial, HISTORIAL_INFORMATIVO, formatter);

        List<FilaConError> errores = new ArrayList<>();
        Map<String, Carrera> carreras = indexarCarreras();
        Map<String, Alumno> alumnos = new HashMap<>();
        Map<String, Nivel> niveles = indexarNiveles();
        Map<String, Modalidad> modalidades = modalidadRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(m -> clave(m.getNombre()), m -> m, (a, b) -> a));
        Map<String, Periodo> periodos = periodoRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(p -> clave(p.getNombre()), p -> p, (a, b) -> a));
        Map<String, Aula> aulas = aulaRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(a -> clave(a.getNombre()), a -> a, (a, b) -> a));
        Map<String, Facilitador> facilitadoresPorCorreo = new HashMap<>();
        Map<String, List<Facilitador>> facilitadoresPorNombre = new HashMap<>();
        facilitadorRepository.findAll().forEach(f -> {
            facilitadoresPorCorreo.put(clave(f.getCorreo()), f);
            facilitadoresPorNombre.computeIfAbsent(clave(f.nombreCompleto()), k -> new ArrayList<>()).add(f);
        });
        Map<ClaveGrupo, Grupo> grupos = new HashMap<>();
        grupoRepository.findAllConDatosHistoricos().forEach(g -> grupos.put(ClaveGrupo.de(g), g));
        Map<Integer, Integer> siguientes = new HashMap<>();
        List<Nivel> catalogoNiveles = nivelRepository.findAllByOrderByNumeroAsc();
        for (int i = 0; i + 1 < catalogoNiveles.size(); i++) {
            siguientes.put(catalogoNiveles.get(i).getId(), catalogoNiveles.get(i + 1).getId());
        }

        int filas = 0;
        int alumnosCreados = 0;
        int gruposProcesados = 0;
        int inscripcionesProcesadas = 0;

        for (int i = 1; i <= hojaAlumnos.getLastRowNum(); i++) {
            Row row = hojaAlumnos.getRow(i);
            if (vacia(row, formatter)) continue;
            filas++;
            String numeroControl = texto(row, 0, formatter).toUpperCase(Locale.ROOT);
            try {
                if (!numeroControl.matches("[A-Z0-9]{1,10}")) {
                    throw new IllegalArgumentException("El número de control debe tener de 1 a 10 letras mayúsculas o dígitos.");
                }
                Alumno alumno = alumnos.get(numeroControl);
                if (alumno == null) {
                    alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl).orElse(null);
                }
                if (alumno == null) {
                    String carreraTexto = texto(row, 5, formatter);
                    Carrera carrera = carreras.get(clave(carreraTexto));
                    if (carrera == null) throw new IllegalArgumentException("La carrera «" + carreraTexto + "» no existe.");
                    String sexo = texto(row, 4, formatter).toUpperCase(Locale.ROOT);
                    if (!sexo.equals("H") && !sexo.equals("M")) throw new IllegalArgumentException("El sexo debe ser H o M.");
                    int semestre = enteroObligatorio(row, 6, formatter, "Semestre");
                    if (semestre < 1 || semestre > 12) throw new IllegalArgumentException("El semestre debe estar entre 1 y 12.");
                    RegistroAlumnoRequest solicitud = new RegistroAlumnoRequest(numeroControl,
                            textoObligatorio(row, 1, formatter, "Nombre"),
                            textoObligatorio(row, 2, formatter, "Apellido paterno"),
                            vacioANulo(texto(row, 3, formatter)), sexo, carrera.getId(), semestre,
                            vacioANulo(texto(row, 7, formatter)), esSi(texto(row, 8, formatter)));
                    var violaciones = validator.validate(solicitud);
                    if (!violaciones.isEmpty()) {
                        throw new IllegalArgumentException(violaciones.iterator().next().getMessage());
                    }
                    alumnoService.registrar(solicitud);
                    alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                            .orElseThrow(() -> new IllegalStateException("No se pudo recuperar el alumno creado."));
                    alumnosCreados++;
                }
                alumnos.put(numeroControl, alumno);
            } catch (RuntimeException e) {
                errores.add(error("Alumnos", i + 1, numeroControl, e));
            }
        }

        int historialImportado = 0;
        for (int i = 1; i <= hojaHistorial.getLastRowNum(); i++) {
            Row row = hojaHistorial.getRow(i);
            if (vacia(row, formatter)) continue;
            filas++;
            String numeroControl = texto(row, 0, formatter).toUpperCase(Locale.ROOT);
            try {
                if (!numeroControl.matches("[A-Z0-9]{1,10}")) {
                    throw new IllegalArgumentException("El número de control debe tener de 1 a 10 letras mayúsculas o dígitos.");
                }
                Alumno alumno = alumnos.get(numeroControl);
                if (alumno == null) alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl).orElse(null);
                if (alumno == null) throw new IllegalArgumentException("El alumno no existe o su fila tiene errores.");
                String nivel = textoObligatorio(row, 1, formatter, "Nivel");
                String periodo = textoObligatorio(row, 2, formatter, "Periodo");
                String grupo = texto(row, 4, formatter);
                Double calificacion = decimalOpcional(row, 7, formatter);
                validarLongitud(nivel, 50, "Nivel");
                validarLongitud(periodo, 100, "Periodo");
                validarLongitud(texto(row, 3, formatter), 150, "Facilitador");
                validarLongitud(grupo, 50, "Grupo de referencia");
                validarLongitud(texto(row, 5, formatter), 100, "Horario");
                validarLongitud(texto(row, 6, formatter), 50, "Aula");
                validarLongitud(texto(row, 8, formatter), 500, "Observación");
                List<HistorialAcademico> existentes = historialRepository.buscarPorClaveHistorica(
                        numeroControl, nivel, periodo, grupo);
                HistorialAcademico historial = existentes.isEmpty()
                        ? new HistorialAcademico() : existentes.getFirst();
                historial.setAlumno(alumno);
                historial.setNivel(nivel);
                historial.setPeriodo(periodo);
                historial.setFacilitador(texto(row, 3, formatter));
                historial.setGrupo(grupo);
                historial.setHorario(texto(row, 5, formatter));
                historial.setAula(texto(row, 6, formatter));
                historial.setCalificacion(calificacion);
                historial.setObservacionHistorica(texto(row, 8, formatter));
                historialRepository.save(historial);
                historialImportado++;
            } catch (RuntimeException e) {
                errores.add(error("Historial_Informativo", i + 1, numeroControl, e));
            }
        }

        for (int i = 1; i <= hojaGrupos.getLastRowNum(); i++) {
            Row row = hojaGrupos.getRow(i);
            if (vacia(row, formatter)) continue;
            filas++;
            try {
                String nombrePeriodo = textoObligatorio(row, 0, formatter, "Periodo");
                validarLongitud(nombrePeriodo, 30, "Periodo");
                Nivel nivel = resolverNivel(niveles, textoObligatorio(row, 3, formatter, "Nivel"));
                Modalidad modalidad = modalidades.get(clave(textoObligatorio(row, 4, formatter, "Modalidad")));
                if (modalidad == null) throw new IllegalArgumentException("La modalidad no existe en el catálogo.");
                boolean intensivo = esSi(texto(row, 15, formatter));
                if (intensivo && nivel.getNumero() != 1) {
                    throw new IllegalArgumentException("El nivel técnico de un grupo Intensivo debe ser Básico 1.");
                }
                String claveGrupo = textoObligatorio(row, 5, formatter, "Clave de grupo").toUpperCase(Locale.ROOT);
                if (!claveGrupo.matches("[A-Z]{1,2}")) throw new IllegalArgumentException("La clave debe tener una o dos letras mayúsculas.");
                int cupo = enteroObligatorio(row, 9, formatter, "Cupo");
                if (cupo < 1 || cupo > 255) throw new IllegalArgumentException("El cupo debe estar entre 1 y 255.");
                String referencia = vacioANulo(texto(row, 14, formatter));
                if (referencia != null && referencia.length() > 255) {
                    throw new IllegalArgumentException("La referencia histórica no puede superar 255 caracteres.");
                }
                ClaveGrupo key = new ClaveGrupo(clave(nombrePeriodo), nivel.getId(), modalidad.getId(), claveGrupo);
                if (!grupos.containsKey(key)) {
                    String correo = texto(row, 6, formatter);
                    Facilitador facilitador = facilitadoresPorCorreo.get(clave(correo));
                    if (facilitador == null) {
                        List<Facilitador> matches = facilitadoresPorNombre.getOrDefault(
                                clave(texto(row, 7, formatter)), List.of());
                        if (matches.size() != 1) {
                            throw new IllegalArgumentException("No se encontró un facilitador único por correo o nombre.");
                        }
                        facilitador = matches.getFirst();
                    }
                    String nombreAula = textoObligatorio(row, 8, formatter, "Aula");
                    validarLongitud(nombreAula, 30, "Aula");
                    Aula aula = aulas.get(clave(nombreAula));
                    if (intensivo != "Intensivo virtual".equalsIgnoreCase(modalidad.getNombre())
                            || intensivo != "Virtual".equalsIgnoreCase(nombreAula)) {
                        throw new IllegalArgumentException(
                                "Los grupos Intensivos deben usar la modalidad «Intensivo virtual» y el aula «Virtual».");
                    }
                    Periodo periodo = periodos.get(clave(nombrePeriodo));
                    if (periodo == null) {
                        periodo = crearPeriodo(nombrePeriodo, texto(row, 1, formatter), texto(row, 2, formatter));
                        periodo = periodoRepository.save(periodo);
                        periodos.put(clave(nombrePeriodo), periodo);
                    }
                    if (aula == null) {
                        aula = new Aula();
                        aula.setNombre(nombreAula);
                        aula = aulaRepository.save(aula);
                        aulas.put(clave(nombreAula), aula);
                    }
                    Grupo grupo = new Grupo();
                    grupo.setClave(claveGrupo);
                    grupo.setIntensivo(intensivo);
                    grupo.setReferenciaHistorica(referencia);
                    grupo.setPeriodo(periodo);
                    grupo.setNivel(nivel);
                    grupo.setModalidad(modalidad);
                    grupo.setFacilitador(facilitador);
                    grupo.setAula(aula);
                    grupo.setCupo(cupo);
                    grupo.setEstatusActa("Cerrada");
                    String fechaCierre = texto(row, 11, formatter);
                    grupo.setFechaCierreActa(fechaCierre.isBlank()
                            ? periodo.getFechaFin() : fecha(fechaCierre, "Fecha de cierre"));
                    String cerradaPor = texto(row, 12, formatter);
                    if (cerradaPor.isBlank()) cerradaPor = "Coordinación";
                    if (!cerradaPor.equals("Coordinación") && !cerradaPor.equals("Facilitador")) {
                        throw new IllegalArgumentException("«Cerrada por» debe ser Coordinación o Facilitador.");
                    }
                    grupo.setCerradaPor(cerradaPor);
                    String observacion = texto(row, 13, formatter);
                    validarLongitud(observacion, 500, "Observación de cierre");
                    grupo.setObservacionCierre(observacion.isBlank() && "Coordinación".equals(cerradaPor)
                            ? "Importado como histórico; no se generaron cargos ni comprobantes."
                            : vacioANulo(observacion));
                    grupo = grupoRepository.save(grupo);
                    grupos.put(key, grupo);
                } else {
                    Grupo grupoExistente = grupos.get(key);
                    if ((grupoExistente.getReferenciaHistorica() == null
                            || grupoExistente.getReferenciaHistorica().isBlank()) && referencia != null) {
                        if (referencia.length() > 255) {
                            throw new IllegalArgumentException("La referencia histórica no puede superar 255 caracteres.");
                        }
                        grupoExistente.setReferenciaHistorica(referencia);
                        grupoRepository.save(grupoExistente);
                    }
                }
                gruposProcesados++;
            } catch (RuntimeException e) {
                errores.add(error("Grupos", i + 1, "", e));
            }
        }

        filas += importarHorarios(hojaHorarios, formatter, grupos, niveles, modalidades, errores);

        List<FilaInscripcion> registros = new ArrayList<>();
        for (int i = 1; i <= hojaInscripciones.getLastRowNum(); i++) {
            Row row = hojaInscripciones.getRow(i);
            if (vacia(row, formatter)) continue;
            filas++;
            String numeroControl = texto(row, 0, formatter).toUpperCase(Locale.ROOT);
            try {
                String periodo = textoObligatorio(row, 1, formatter, "Periodo");
                Nivel nivel = resolverNivel(niveles, textoObligatorio(row, 2, formatter, "Nivel"));
                Modalidad modalidad = modalidades.get(clave(textoObligatorio(row, 3, formatter, "Modalidad")));
                if (modalidad == null) throw new IllegalArgumentException("La modalidad no existe en el catálogo.");
                String claveGrupo = textoObligatorio(row, 4, formatter, "Clave de grupo").toUpperCase(Locale.ROOT);
                Grupo grupo = grupos.get(new ClaveGrupo(clave(periodo), nivel.getId(), modalidad.getId(), claveGrupo));
                if (grupo == null) throw new IllegalArgumentException("No existe el grupo indicado en la hoja Grupos.");
                Alumno alumno = alumnos.get(numeroControl);
                if (alumno == null) alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl).orElse(null);
                if (alumno == null) throw new IllegalArgumentException("El alumno no existe o su fila tiene errores.");
                String oportunidad = texto(row, 5, formatter);
                if (oportunidad.isBlank()) oportunidad = "01";
                if (!oportunidad.equals("01") && !oportunidad.equalsIgnoreCase("R1")) {
                    throw new IllegalArgumentException("La oportunidad debe ser 01 o R1.");
                }
                Integer calificacion = enteroOpcional(row, 6, formatter, "Calificación");
                if (calificacion != null && (calificacion < 0 || calificacion > 100)) {
                    throw new IllegalArgumentException("La calificación debe estar entre 0 y 100.");
                }
                boolean avancePendiente = esSi(texto(row, 7, formatter));
                String nivelAcreditadoTexto = texto(row, 8, formatter);
                Nivel nivelAcreditado = nivelAcreditadoTexto.isBlank()
                        ? null : resolverNivel(niveles, nivelAcreditadoTexto);
                if (grupo.isIntensivo() && calificacion != null && !avancePendiente && nivelAcreditado == null) {
                    throw new IllegalArgumentException("Indica el nivel acreditado para la inscripción Intensiva.");
                }
                if (!grupo.isIntensivo() && nivelAcreditado != null) {
                    throw new IllegalArgumentException("El nivel acreditado solo se captura para grupos Intensivos.");
                }
                registros.add(new FilaInscripcion(i + 1, numeroControl, alumno, grupo, oportunidad,
                        calificacion, avancePendiente, nivelAcreditado));
            } catch (RuntimeException e) {
                errores.add(error("Inscripciones", i + 1, numeroControl, e));
            }
        }
        registros.sort(Comparator.comparing((FilaInscripcion r) -> r.grupo().getPeriodo().getFechaInicio())
                .thenComparing(r -> r.grupo().getNivel().getNumero())
                .thenComparing(FilaInscripcion::numeroControl));

        for (FilaInscripcion registro : registros) {
            try {
                Inscripcion inscripcion = inscripcionRepository
                        .findByAlumnoNumeroControlAndGrupoId(registro.numeroControl(), registro.grupo().getId())
                        .orElseGet(Inscripcion::new);
                boolean nueva = inscripcion.getId() == null;
                if (nueva) {
                    inscripcion.setAlumno(registro.alumno());
                    inscripcion.setGrupo(registro.grupo());
                    inscripcion.setIdComprobante(null);
                    inscripcion.setIdAutorizacion(null);
                }
                inscripcion.setOportunidad(registro.oportunidad());
                inscripcion.setCalificacionFinal(registro.calificacion());
                inscripcion.setAvancePendiente(registro.avancePendiente());
                inscripcion.setNivelAcreditado(registro.nivelAcreditado());
                inscripcionRepository.save(inscripcion);
                if (!registro.avancePendiente() && registro.calificacion() != null) {
                    actualizarAvance(registro, siguientes);
                }
                inscripcionesProcesadas++;
            } catch (RuntimeException e) {
                errores.add(error("Inscripciones", registro.fila(), registro.numeroControl(), e));
            }
        }

        if (filas == 0) {
            throw new DatosInvalidosException("ALUM-023", "El archivo no contiene registros históricos.");
        }
        return new ResultadoCargaDTO(filas, alumnosCreados, historialImportado,
                gruposProcesados, inscripcionesProcesadas, errores);
    }

    private ResultadoCargaDTO importarFormatoAnterior(Workbook origen) {
        DataFormatter formatter = new DataFormatter(new Locale("es", "MX"));
        List<FilaConError> erroresAdaptacion = new ArrayList<>();
        try (Workbook normalizado = new XSSFWorkbook()) {
            Sheet alumnos = crearHoja(normalizado, "Alumnos", ALUMNOS, estiloEncabezado(normalizado));
            Sheet grupos = crearHoja(normalizado, "Grupos", GRUPOS, estiloEncabezado(normalizado));
            Sheet inscripciones = crearHoja(
                    normalizado, "Inscripciones", INSCRIPCIONES, estiloEncabezado(normalizado));
            Sheet horarios = crearHoja(normalizado, "Horarios", HORARIOS, estiloEncabezado(normalizado));
            Sheet historial = crearHoja(
                    normalizado, "Historial_Informativo", HISTORIAL_INFORMATIVO, estiloEncabezado(normalizado));

            copiarColumnas(origen.getSheet("Alumnos"), alumnos,
                    new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 }, formatter);

            Sheet gruposAnteriores = origen.getSheet("Grupos");
            if (gruposAnteriores == null) {
                throw new DatosInvalidosException("ALUM-025", "La plantilla anterior no contiene la hoja Grupos.");
            }
            Map<String, List<FilaGrupoAnterior>> gruposPorClave = new HashMap<>();
            int filaGrupoNueva = 1;
            int filaHorarioNueva = 1;
            for (int i = 1; i <= gruposAnteriores.getLastRowNum(); i++) {
                Row fila = gruposAnteriores.getRow(i);
                if (vacia(fila, formatter)) continue;
                String claveGrupo = texto(fila, 7, formatter).toUpperCase(Locale.ROOT);
                String periodo = textoFecha(fila, 0, formatter);
                String nivel = texto(fila, 3, formatter);
                String modalidad = texto(fila, 4, formatter);
                String facilitador = texto(fila, 5, formatter);
                Row nuevo = grupos.createRow(filaGrupoNueva++);
                escribir(nuevo, periodo, textoFecha(fila, 1, formatter), textoFecha(fila, 2, formatter),
                        nivel, modalidad, claveGrupo, facilitador, facilitador, texto(fila, 6, formatter),
                        texto(fila, 9, formatter), "Cerrada", textoFecha(fila, 2, formatter),
                        "Coordinación", "Importado como histórico; no se generaron cargos ni comprobantes.",
                        texto(fila, 8, formatter));
                FilaGrupoAnterior referencia = new FilaGrupoAnterior(periodo, nivel, modalidad, claveGrupo);
                gruposPorClave.computeIfAbsent(clave(claveGrupo), k -> new ArrayList<>()).add(referencia);

                String dia = normalizarDia(texto(fila, 10, formatter));
                String inicio = texto(fila, 11, formatter);
                String fin = texto(fila, 12, formatter);
                if (!dia.isBlank() || !inicio.isBlank() || !fin.isBlank()) {
                    escribir(horarios.createRow(filaHorarioNueva++), periodo, nivel, modalidad,
                            claveGrupo, dia, inicio, fin);
                }
            }

            Sheet inscripcionesAnteriores = origen.getSheet("Inscripciones");
            if (inscripcionesAnteriores != null) {
                int filaNueva = 1;
                for (int i = 1; i <= inscripcionesAnteriores.getLastRowNum(); i++) {
                    Row fila = inscripcionesAnteriores.getRow(i);
                    if (vacia(fila, formatter)) continue;
                    String numeroControl = texto(fila, 0, formatter);
                    String claveGrupo = texto(fila, 1, formatter);
                    List<FilaGrupoAnterior> coincidencias = gruposPorClave.getOrDefault(
                            clave(claveGrupo), List.of());
                    if (coincidencias.size() != 1) {
                        erroresAdaptacion.add(new FilaConError(i + 1, numeroControl,
                                "Inscripciones: la clave de grupo «" + claveGrupo
                                        + "» no identifica un único grupo; usa la plantilla nueva con Periodo, Nivel y Modalidad."));
                        continue;
                    }
                    FilaGrupoAnterior grupo = coincidencias.getFirst();
                    escribir(inscripciones.createRow(filaNueva++), numeroControl,
                            grupo.periodo(), grupo.nivel(), grupo.modalidad(), grupo.clave(),
                            "01", texto(fila, 2, formatter), "No");
                }
            }

            Sheet intensivoAnterior = origen.getSheet("Intensivo_Historico");
            for (int i = 1; i <= intensivoAnterior.getLastRowNum(); i++) {
                Row fila = intensivoAnterior.getRow(i);
                if (vacia(fila, formatter)) continue;
                String observacion = "Liberado en fuente: " + texto(fila, 7, formatter)
                        + "; pago y aprobación confirmados: " + texto(fila, 8, formatter)
                        + "; " + texto(fila, 9, formatter);
                escribir(historial.createRow(i), texto(fila, 0, formatter), "Intensivo",
                        texto(fila, 3, formatter), texto(fila, 4, formatter), texto(fila, 5, formatter),
                        texto(fila, 6, formatter), "", texto(fila, 2, formatter), observacion);
            }

            ResultadoCargaDTO resultado = importar(normalizado);
            List<FilaConError> errores = new ArrayList<>(resultado.errores());
            errores.addAll(erroresAdaptacion);
            return new ResultadoCargaDTO(resultado.filas() + erroresAdaptacion.size(),
                    resultado.creados(), resultado.nivelesImportados(), resultado.gruposImportados(),
                    resultado.inscripcionesImportadas(), errores);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo adaptar la plantilla histórica anterior.", e);
        }
    }

    private static org.apache.poi.ss.usermodel.CellStyle estiloEncabezado(Workbook libro) {
        var estilo = libro.createCellStyle();
        var fuente = libro.createFont();
        fuente.setBold(true);
        estilo.setFont(fuente);
        estilo.setWrapText(true);
        return estilo;
    }

    private static void copiarColumnas(Sheet origen, Sheet destino, int[] columnas, DataFormatter formatter) {
        if (origen == null) return;
        int filaDestino = 1;
        for (int i = 1; i <= origen.getLastRowNum(); i++) {
            Row fila = origen.getRow(i);
            if (vacia(fila, formatter)) continue;
            Row nueva = destino.createRow(filaDestino++);
            for (int columna = 0; columna < columnas.length; columna++) {
                String valor = texto(fila, columnas[columna], formatter);
                if (!valor.isBlank()) nueva.createCell(columna).setCellValue(valor);
            }
        }
    }

    private static String textoFecha(Row fila, int columna, DataFormatter formatter) {
        if (fila == null) return "";
        Cell celda = fila.getCell(columna);
        if (celda != null && celda.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return texto(fila, columna, formatter);
    }

    private static String normalizarDia(String valor) {
        String dia = clave(valor).replace('á', 'a').replace('é', 'e').replace('í', 'i')
                .replace('ó', 'o').replace('ú', 'u');
        if (dia.isBlank()) return "";
        try {
            int numero = Integer.parseInt(dia);
            return numero >= 1 && numero <= 6 ? String.valueOf(numero) : valor;
        } catch (NumberFormatException ignorada) {
            return switch (dia) {
                case "lunes" -> "1";
                case "martes" -> "2";
                case "miercoles" -> "3";
                case "jueves" -> "4";
                case "viernes" -> "5";
                case "sabado" -> "6";
                default -> valor;
            };
        }
    }

    private int importarHorarios(Sheet hoja, DataFormatter formatter, Map<ClaveGrupo, Grupo> grupos,
            Map<String, Nivel> niveles, Map<String, Modalidad> modalidades, List<FilaConError> errores) {
        Set<ClaveGrupo> gruposConHorarios = new HashSet<>();
        grupos.forEach((clave, grupo) -> {
            if (!grupo.getHorarios().isEmpty()) gruposConHorarios.add(clave);
        });
        int filas = 0;
        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row row = hoja.getRow(i);
            if (vacia(row, formatter)) continue;
            filas++;
            try {
                String periodo = textoObligatorio(row, 0, formatter, "Periodo");
                Nivel nivel = resolverNivel(niveles, textoObligatorio(row, 1, formatter, "Nivel"));
                Modalidad modalidad = modalidades.get(clave(textoObligatorio(row, 2, formatter, "Modalidad")));
                if (modalidad == null) throw new IllegalArgumentException("La modalidad no existe.");
                String claveGrupo = textoObligatorio(row, 3, formatter, "Clave de grupo").toUpperCase(Locale.ROOT);
                ClaveGrupo clave = new ClaveGrupo(clave(periodo), nivel.getId(), modalidad.getId(), claveGrupo);
                Grupo grupo = grupos.get(clave);
                if (grupo == null) throw new IllegalArgumentException("No existe el grupo indicado en la hoja Grupos.");
                if (gruposConHorarios.contains(clave)) continue;
                int dia = enteroObligatorio(row, 4, formatter, "Día");
                if (dia < 1 || dia > 6) throw new IllegalArgumentException("El día debe estar entre 1 (lunes) y 6 (sábado).");
                Horario horario = new Horario();
                horario.setDiaSemana(dia);
                horario.setHoraInicio(hora(row, 5, formatter, "Hora inicio"));
                horario.setHoraFin(hora(row, 6, formatter, "Hora fin"));
                if (!horario.getHoraInicio().isBefore(horario.getHoraFin())) {
                    throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio.");
                }
                if (horario.getHoraInicio().isBefore(LocalTime.of(10, 0))
                        || horario.getHoraFin().isAfter(LocalTime.of(17, 0))) {
                    throw new IllegalArgumentException(
                            "El horario está fuera del rango que permite SIGCLEM (10:00 a 17:00); omítelo si es solo una referencia histórica.");
                }
                grupo.agregarHorario(horario);
                grupoRepository.save(grupo);
            } catch (RuntimeException e) {
                errores.add(error("Horarios", i + 1, "", e));
            }
        }
        return filas;
    }

    private void actualizarAvance(FilaInscripcion fila, Map<Integer, Integer> siguientes) {
        String numeroControl = fila.numeroControl();
        Nivel nivelAcreditado = fila.grupo().isIntensivo() ? fila.nivelAcreditado() : fila.grupo().getNivel();
        if (nivelAcreditado == null) return;
        Integer idNivel = nivelAcreditado.getId();
        AvanceNivel avance = avanceRepository.findByIdNumeroControlAndIdIdNivel(numeroControl, idNivel).orElse(null);
        if (avance == null) return;
        if (fila.calificacion() >= 70) {
            if (avance.getEstatus() != EstatusAvance.EXENTO) {
                avance.setEstatus(EstatusAvance.ACREDITADO);
                avance.setOrigen("Histórico importado");
            }
            Integer siguienteId = siguientes.get(idNivel);
            if (siguienteId == null) return;
            avanceRepository.findByIdNumeroControlAndIdIdNivel(numeroControl, siguienteId)
                    .filter(siguiente -> siguiente.getEstatus() == EstatusAvance.NO_PERMITIDO)
                    .ifPresent(siguiente -> siguiente.setEstatus(EstatusAvance.HABILITADO));
        } else if (!avance.getEstatus().cubreNivel() && avance.getEstatus() != EstatusAvance.CURSANDO) {
            avance.setEstatus(EstatusAvance.REPROBADO);
            avance.setOrigen("Histórico importado");
        }
    }

    private Periodo crearPeriodo(String nombre, String fechaInicio, String fechaFin) {
        validarLongitud(nombre, 30, "Periodo");
        Periodo periodo = new Periodo();
        periodo.setNombre(nombre);
        periodo.setFechaInicio(fecha(fechaInicio, "Fecha inicio"));
        periodo.setFechaFin(fecha(fechaFin, "Fecha fin"));
        if (periodo.getFechaFin().isBefore(periodo.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha fin del periodo debe ser posterior a la fecha inicio.");
        }
        return periodo;
    }

    private static Sheet crearHoja(Workbook libro, String nombre, String[] titulos, org.apache.poi.ss.usermodel.CellStyle estilo) {
        Sheet hoja = libro.createSheet(nombre);
        Row fila = hoja.createRow(0);
        for (int i = 0; i < titulos.length; i++) {
            Cell celda = fila.createCell(i);
            celda.setCellValue(titulos[i]);
            celda.setCellStyle(estilo);
            hoja.setColumnWidth(i, 6000);
        }
        return hoja;
    }

    private static void escribir(Row fila, Object... valores) {
        for (int i = 0; i < valores.length; i++) {
            Object valor = valores[i];
            if (valor == null) continue;
            Cell celda = fila.createCell(i);
            if (valor instanceof Number numero) celda.setCellValue(numero.doubleValue());
            else if (valor instanceof LocalDate fecha) celda.setCellValue(fecha.toString());
            else if (valor instanceof LocalTime hora) celda.setCellValue(hora.format(DateTimeFormatter.ofPattern("HH:mm")));
            else celda.setCellValue(valor.toString());
        }
    }

    private Map<String, Carrera> indexarCarreras() {
        Map<String, Carrera> carreras = new HashMap<>();
        for (Carrera carrera : carreraRepository.findAll()) {
            carreras.put(clave(carrera.getNombre()), carrera);
            if (carrera.getClavePlan() != null) carreras.put(clave(carrera.getClavePlan()), carrera);
        }
        return carreras;
    }

    private Map<String, Nivel> indexarNiveles() {
        Map<String, Nivel> niveles = new HashMap<>();
        for (Nivel nivel : nivelRepository.findAllByOrderByNumeroAsc()) {
            niveles.put(clave(nivel.getNombre()), nivel);
            niveles.put(clave(nivel.getClave()), nivel);
            niveles.put(String.valueOf(nivel.getNumero()), nivel);
        }
        return niveles;
    }

    private static Nivel resolverNivel(Map<String, Nivel> niveles, String nombre) {
        Nivel nivel = niveles.get(clave(nombre));
        if (nivel == null) throw new IllegalArgumentException("El nivel «" + nombre + "» no existe en el catálogo.");
        return nivel;
    }

    private static String texto(Row fila, int columna, DataFormatter formatter) {
        if (fila == null) return "";
        Cell celda = fila.getCell(columna);
        return celda == null ? "" : formatter.formatCellValue(celda).trim();
    }

    private static String textoObligatorio(Row fila, int columna, DataFormatter formatter, String campo) {
        String valor = texto(fila, columna, formatter);
        if (valor.isBlank()) throw new IllegalArgumentException("El campo «" + campo + "» es obligatorio.");
        return valor;
    }

    private static void validarLongitud(String valor, int maximo, String campo) {
        if (valor != null && valor.length() > maximo) {
            throw new IllegalArgumentException("«" + campo + "» no puede superar " + maximo + " caracteres.");
        }
    }

    private static int enteroObligatorio(Row fila, int columna, DataFormatter formatter, String campo) {
        Integer valor = enteroOpcional(fila, columna, formatter, campo);
        if (valor == null) throw new IllegalArgumentException("El campo «" + campo + "» es obligatorio.");
        return valor;
    }

    private static Integer enteroOpcional(Row fila, int columna, DataFormatter formatter, String campo) {
        String texto = texto(fila, columna, formatter);
        if (texto.isBlank()) return null;
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El campo «" + campo + "» debe ser un número entero.");
        }
    }

    private static Double decimalOpcional(Row fila, int columna, DataFormatter formatter) {
        String valor = texto(fila, columna, formatter);
        if (valor.isBlank() || valor.equalsIgnoreCase("NA") || valor.equalsIgnoreCase("N/A")) return null;
        try {
            double calificacion = Double.parseDouble(valor);
            if (calificacion < 0 || calificacion > 100) {
                throw new IllegalArgumentException("La calificación debe estar entre 0 y 100.");
            }
            return calificacion;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La calificación informativa debe ser numérica.");
        }
    }

    private static LocalDate fecha(String valor, String campo) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            for (DateTimeFormatter formato : List.of(
                    DateTimeFormatter.ofPattern("d/M/uuuu"),
                    DateTimeFormatter.ofPattern("M/d/uuuu"),
                    DateTimeFormatter.ofPattern("d/M/yy"),
                    DateTimeFormatter.ofPattern("M/d/yy"))) {
                try {
                    return LocalDate.parse(valor.trim(), formato);
                } catch (DateTimeParseException ignorada) {
                    continue;
                }
            }
            throw new IllegalArgumentException("«" + campo + "» debe tener formato AAAA-MM-DD o ser una fecha válida.");
        }
    }

    private static LocalTime hora(String valor) {
        for (DateTimeFormatter formato : List.of(
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("h:mm a", Locale.US))) {
            try {
                return LocalTime.parse(valor.trim(), formato);
            } catch (DateTimeParseException ignorada) {
                continue;
            }
        }
        throw new IllegalArgumentException("Las horas deben tener formato HH:mm.");
    }

    private static LocalTime hora(Row fila, int columna, DataFormatter formatter, String campo) {
        Cell celda = fila.getCell(columna);
        if (celda != null && celda.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalTime();
        }
        return hora(textoObligatorio(fila, columna, formatter, campo));
    }

    private static boolean vacia(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (Cell celda : row) if (!formatter.formatCellValue(celda).isBlank()) return false;
        return true;
    }

    private static void validarEncabezados(Sheet hoja, String[] esperados, DataFormatter formatter) {
        Row fila = hoja.getRow(0);
        for (int columna = 0; columna < esperados.length; columna++) {
            if (!esperados[columna].equals(texto(fila, columna, formatter))) {
                throw new DatosInvalidosException("ALUM-026",
                        "La hoja «" + hoja.getSheetName() + "» no coincide con la plantilla de histórico. "
                                + "Descarga una plantilla nueva desde SIGCLEM.");
            }
        }
    }

    private static void validarEncabezadoOpcional(Sheet hoja, int columna, String esperado, DataFormatter formatter) {
        String actual = texto(hoja.getRow(0), columna, formatter);
        if (!actual.isBlank() && !esperado.equals(actual)) {
            throw new DatosInvalidosException("ALUM-026",
                    "La hoja «" + hoja.getSheetName() + "» no coincide con la plantilla de histórico. "
                            + "Descarga una plantilla nueva desde SIGCLEM.");
        }
    }

    private static boolean esSi(String valor) {
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        return normalizado.startsWith("s") || normalizado.equals("x") || normalizado.equals("1")
                || normalizado.equals("true");
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String clave(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private static FilaConError error(String hoja, int fila, String numeroControl, RuntimeException e) {
        String mensaje = e.getMessage() == null ? "No se pudo procesar el registro." : e.getMessage();
        return new FilaConError(fila, numeroControl, hoja + ": " + mensaje);
    }

    private record ClaveGrupo(String periodo, Integer idNivel, Integer idModalidad, String clave) {
        static ClaveGrupo de(Grupo grupo) {
            return new ClaveGrupo(ImportacionHistoricaService.clave(grupo.getPeriodo().getNombre()), grupo.getNivel().getId(),
                    grupo.getModalidad().getId(), grupo.getClave().toUpperCase(Locale.ROOT));
        }
    }

    private record FilaInscripcion(int fila, String numeroControl, Alumno alumno, Grupo grupo,
            String oportunidad, Integer calificacion, boolean avancePendiente, Nivel nivelAcreditado) {
    }

    private record FilaGrupoAnterior(String periodo, String nivel, String modalidad, String clave) {
    }
}
