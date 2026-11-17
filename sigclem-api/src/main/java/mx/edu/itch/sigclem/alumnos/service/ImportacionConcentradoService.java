package mx.edu.itch.sigclem.alumnos.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

/**
 * Importa el concentrado histórico tal como lo lleva la coordinación (una fila por alumno con bloques de seis
 * columnas por nivel: calificación, periodo, facilitador, grupo, horario y aula) sin inventar datos:
 * <ul>
 * <li>Los campos vacíos se guardan vacíos; solo se exige número de control, nombre, apellido paterno y carrera.</li>
 * <li>La calificación se interpreta («100 (Cien)» → 100, «NA BAJA» → no acreditado) y se conserva el texto original.</li>
 * <li>Con 70 o más, el nivel queda acreditado en el avance con origen «Histórico»; un Intensivo aprobado acredita los
 * diez niveles (origen «Curso intensivo») y «Liberado = Sí» libera el idioma.</li>
 * <li>Si se adjunta la matrícula de control escolar, de ahí se toman semestre, sexo, correo y lengua indígena.</li>
 * <li>Cada fila se guarda en su propia transacción: una fila con error no deja datos a medias ni detiene la carga.</li>
 * <li>Volver a subir el archivo actualiza los mismos registros, no los duplica.</li>
 * </ul>
 */
@Service
public class ImportacionConcentradoService {

    static final String ORIGEN_HISTORICO = "Histórico";
    static final String ORIGEN_LIBERADO = "Registro histórico";

    private static final String[] NIVELES = {
            "Básico 1", "Básico 2", "Básico 3", "Básico 4", "Básico 5",
            "Intermedio 1", "Intermedio 2", "Intermedio 3", "Intermedio 4", "Intermedio 5", "Intensivo" };

    private static final Pattern NUMERO_CONTROL = Pattern.compile("[A-Z0-9]{1,10}");
    private static final Pattern NUMERO_INICIAL = Pattern.compile("^\\s*(\\d{1,3}(?:[.,]\\d+)?)");
    private static final Set<String> SIN_LENGUA = Set.of("", "no", "n", "ninguna", "ninguno", "niguna", "na", "n/a",
            "no aplica", "0", "nada");
    private static final String[] MESES = { "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
            "Septiembre", "Octubre", "Noviembre", "Diciembre" };

    private final AlumnoService alumnoService;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final HistorialAcademicoRepository historialRepository;
    private final CarreraRepository carreraRepository;
    private final NivelRepository nivelRepository;
    private final TransactionTemplate transaccionPorFila;

    public ImportacionConcentradoService(AlumnoService alumnoService, AlumnoRepository alumnoRepository,
            AvanceNivelRepository avanceRepository, HistorialAcademicoRepository historialRepository,
            CarreraRepository carreraRepository, NivelRepository nivelRepository,
            PlatformTransactionManager transactionManager) {
        this.alumnoService = alumnoService;
        this.alumnoRepository = alumnoRepository;
        this.avanceRepository = avanceRepository;
        this.historialRepository = historialRepository;
        this.carreraRepository = carreraRepository;
        this.nivelRepository = nivelRepository;
        this.transaccionPorFila = new TransactionTemplate(transactionManager);
        this.transaccionPorFila.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Datos personales tomados de la matrícula vigente de control escolar. */
    record DatosMatricula(Integer semestre, String sexo, String correo, boolean hablaLengua, String carrera) {
    }

    /** Un bloque de nivel del concentrado (columna de la calificación; las cinco siguientes son sus datos). */
    private record Bloque(String nivel, int columna) {
    }

    private record Columnas(int control, int carrera, int nombre, int paterno, int materno, int liberado,
            List<Bloque> bloques) {
    }

    /** Resultado de procesar una fila. */
    private record ResultadoFila(boolean creado, int niveles, int acreditados, boolean liberado, boolean conMatricula) {
    }

    /** ¿El libro tiene el formato de concentrado (encabezado «No. Control» y columnas de nivel)? */
    public static boolean esConcentrado(Workbook libro) {
        Sheet hoja = libro.getSheetAt(0);
        Row encabezado = hoja == null ? null : hoja.getRow(hoja.getFirstRowNum());
        if (encabezado == null) {
            return false;
        }
        Map<String, Integer> indice = indiceEncabezados(encabezado);
        return buscar(indice, "no. control", "no control", "numero de control", "num. control") >= 0
                && indice.containsKey(normalizar("Básico 1"));
    }

    public ResultadoCargaDTO importar(Workbook libro, Workbook matricula) {
        Sheet hoja = libro.getSheetAt(0);
        Row encabezado = hoja.getRow(hoja.getFirstRowNum());
        if (encabezado == null) {
            throw new DatosInvalidosException("ALUM-023", "El archivo no tiene encabezados.");
        }
        Columnas col = columnas(indiceEncabezados(encabezado));
        Map<String, DatosMatricula> datosMatricula = matricula == null ? Map.of() : leerMatricula(matricula);
        Map<String, Carrera> carreras = indexarCarreras();
        Map<String, Nivel> niveles = new HashMap<>();
        for (Nivel n : nivelRepository.findAllByOrderByNumeroAsc()) {
            niveles.put(normalizar(n.getNombre()), n);
        }
        DataFormatter formato = new DataFormatter(new Locale("es", "MX"));

        int filas = 0, creados = 0, actualizados = 0, nivelesImportados = 0, acreditados = 0, liberados = 0,
                conMatricula = 0;
        List<FilaConError> errores = new ArrayList<>();
        for (int f = hoja.getFirstRowNum() + 1; f <= hoja.getLastRowNum(); f++) {
            Row fila = hoja.getRow(f);
            if (fila == null || filaVacia(fila, col, formato)) {
                continue;
            }
            filas++;
            String numeroControl = numeroControl(texto(fila, col.control(), formato));
            try {
                ResultadoFila r = transaccionPorFila.execute(estado -> procesarFila(fila, col, formato, numeroControl,
                        datosMatricula.get(numeroControl), carreras, niveles));
                if (r.creado()) creados++; else actualizados++;
                nivelesImportados += r.niveles();
                acreditados += r.acreditados();
                if (r.liberado()) liberados++;
                if (r.conMatricula()) conMatricula++;
            } catch (RuntimeException e) {
                errores.add(new FilaConError(f + 1, numeroControl, mensaje(e)));
            }
        }
        if (filas == 0) {
            throw new DatosInvalidosException("ALUM-023", "El archivo no tiene filas de alumnos (después del encabezado).");
        }
        return new ResultadoCargaDTO(filas, creados, nivelesImportados, 0, 0, 0, errores, actualizados, acreditados,
                liberados, conMatricula);
    }

    private ResultadoFila procesarFila(Row fila, Columnas col, DataFormatter formato, String numeroControl,
            DatosMatricula matricula, Map<String, Carrera> carreras, Map<String, Nivel> niveles) {
        if (numeroControl.isEmpty()) {
            throw new IllegalArgumentException("Falta el número de control. Si es un alumno externo, asígnale un "
                    + "identificador de letras y dígitos (por ejemplo EXT0001) y vuelve a subir la fila.");
        }
        if (!NUMERO_CONTROL.matcher(numeroControl).matches()) {
            throw new IllegalArgumentException("El número de control «" + numeroControl
                    + "» debe tener de 1 a 10 letras o dígitos, sin guiones ni símbolos.");
        }
        String nombre = limpio(texto(fila, col.nombre(), formato));
        String paterno = limpio(texto(fila, col.paterno(), formato));
        String materno = col.materno() < 0 ? "" : limpio(texto(fila, col.materno(), formato));
        if (nombre.isEmpty() || paterno.isEmpty()) {
            throw new IllegalArgumentException("Faltan el nombre o el apellido paterno.");
        }
        if (nombre.length() > 60 || paterno.length() > 40 || materno.length() > 40) {
            throw new IllegalArgumentException("El nombre (60) o los apellidos (40) superan el largo permitido.");
        }
        String carreraTexto = limpio(texto(fila, col.carrera(), formato));
        Carrera carrera = carreras.get(claveCarrera(carreraTexto));
        if (carrera == null && matricula != null) {
            carrera = carreras.get(claveCarrera(matricula.carrera()));
        }
        if (carrera == null) {
            throw new IllegalArgumentException(carreraTexto.isEmpty() ? "Falta la carrera."
                    : "La carrera «" + carreraTexto + "» no existe en el catálogo.");
        }

        boolean creado = false;
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl).orElse(null);
        if (alumno == null) {
            alumnoService.registrar(new RegistroAlumnoRequest(numeroControl, nombre, paterno,
                    materno.isEmpty() ? null : materno,
                    matricula == null ? null : matricula.sexo(), carrera.getId(),
                    matricula == null ? null : matricula.semestre(),
                    matricula == null ? null : matricula.correo(),
                    matricula != null && matricula.hablaLengua()));
            alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl).orElseThrow();
            creado = true;
        } else {
            alumno.setNombre(nombre);
            alumno.setApellidoPaterno(paterno);
            alumno.setApellidoMaterno(materno.isEmpty() ? null : materno);
            alumno.setCarrera(carrera);
            if (matricula != null) {
                // La matrícula de control escolar es la fuente oficial: semestre y sexo se actualizan; el correo solo
                // se completa si estaba vacío, y la lengua materna nunca se desmarca.
                if (matricula.semestre() != null) {
                    alumno.setSemestre(matricula.semestre());
                    alumno.setFechaActualizacionSemestre(LocalDate.now());
                }
                if (matricula.sexo() != null) alumno.setSexo(matricula.sexo());
                if (alumno.getCorreo() == null && matricula.correo() != null) alumno.setCorreo(matricula.correo());
                if (matricula.hablaLengua()) alumno.setHablaLenguaMaterna(true);
            }
        }

        int registros = 0;
        List<Nivel> acreditadosFila = new ArrayList<>();
        boolean intensivoAprobado = false;
        for (Bloque bloque : col.bloques()) {
            String calificacionTexto = texto(fila, bloque.columna(), formato);
            String periodo = periodo(fila.getCell(bloque.columna() + 1), formato);
            String facilitador = limpio(texto(fila, bloque.columna() + 2, formato));
            String grupo = limpio(texto(fila, bloque.columna() + 3, formato));
            String horario = limpio(texto(fila, bloque.columna() + 4, formato));
            String aula = limpio(texto(fila, bloque.columna() + 5, formato));
            if (calificacionTexto.isBlank() && periodo.isEmpty() && facilitador.isEmpty() && grupo.isEmpty()
                    && horario.isEmpty() && aula.isEmpty()) {
                continue; // El alumno no cursó este nivel.
            }
            Double calificacion = calificacion(calificacionTexto);
            Boolean acreditado = calificacion != null ? Boolean.valueOf(calificacion >= 70)
                    : (esNoAcreditado(calificacionTexto) ? Boolean.FALSE : null);

            List<HistorialAcademico> existentes = historialRepository.buscarPorClaveHistorica(numeroControl,
                    bloque.nivel(), periodo, recortar(grupo, 50));
            HistorialAcademico h = existentes.isEmpty() ? new HistorialAcademico() : existentes.getFirst();
            h.setAlumno(alumno);
            h.setNivel(bloque.nivel());
            h.setPeriodo(nulo(recortar(periodo, 100)));
            h.setFacilitador(nulo(recortar(facilitador, 150)));
            h.setGrupo(nulo(recortar(grupo, 50)));
            h.setHorario(nulo(recortar(horario, 100)));
            h.setAula(nulo(recortar(aula, 50)));
            h.setCalificacion(calificacion);
            h.setCalificacionTexto(nulo(recortar(limpio(calificacionTexto), 60)));
            h.setAcreditado(acreditado);
            historialRepository.save(h);
            registros++;

            Nivel nivel = niveles.get(normalizar(bloque.nivel()));
            if (Boolean.TRUE.equals(acreditado) && nivel != null) {
                acreditadosFila.add(nivel);
            } else if (Boolean.TRUE.equals(acreditado) && "Intensivo".equals(bloque.nivel())) {
                intensivoAprobado = true; // El curso Intensivo aprobado acredita los diez niveles.
            }
        }

        boolean liberado = col.liberado() >= 0 && esSi(texto(fila, col.liberado(), formato));
        int nuevosAcreditados = aplicarAvance(numeroControl, acreditadosFila, intensivoAprobado, liberado);
        if (liberado) {
            alumno.setLiberado(true);
        }
        alumnoRepository.save(alumno);
        return new ResultadoFila(creado, registros, nuevosAcreditados, liberado, matricula != null);
    }

    /**
     * Acredita en el avance los niveles con 70 o más (sin tocar los ya cubiertos ni el que se está cursando),
     * habilita el primer nivel pendiente y, si el concentrado dice «Liberado», deja exentos los niveles restantes.
     */
    private int aplicarAvance(String numeroControl, List<Nivel> acreditados, boolean intensivoAprobado,
            boolean liberado) {
        List<Nivel> catalogo = nivelRepository.findAllByOrderByNumeroAsc();
        Map<Integer, AvanceNivel> avance = new HashMap<>();
        for (AvanceNivel a : avanceRepository.findByIdNumeroControl(numeroControl)) {
            avance.put(a.getId().getIdNivel(), a);
        }
        for (Nivel n : catalogo) {
            avance.computeIfAbsent(n.getId(), id -> new AvanceNivel(numeroControl, id, EstatusAvance.NO_PERMITIDO));
        }
        int nuevos = 0;
        for (Nivel n : acreditados) {
            AvanceNivel a = avance.get(n.getId());
            if (!a.getEstatus().cubreNivel() && a.getEstatus() != EstatusAvance.CURSANDO) {
                a.setEstatus(EstatusAvance.ACREDITADO);
                a.setOrigen(ORIGEN_HISTORICO);
                a.setFechaRegistro(LocalDate.now());
                nuevos++;
            }
        }
        if (intensivoAprobado) {
            for (AvanceNivel a : avance.values()) {
                if (!a.getEstatus().cubreNivel() && a.getEstatus() != EstatusAvance.CURSANDO) {
                    a.setEstatus(EstatusAvance.ACREDITADO);
                    a.setOrigen(mx.edu.itch.sigclem.grupos.service.GrupoService.ORIGEN_INTENSIVO);
                    a.setFechaRegistro(LocalDate.now());
                    nuevos++;
                }
            }
        }
        if (liberado) {
            for (AvanceNivel a : avance.values()) {
                if (!a.getEstatus().cubreNivel()) {
                    a.setEstatus(EstatusAvance.EXENTO);
                    a.setOrigen(ORIGEN_LIBERADO);
                    a.setFechaRegistro(LocalDate.now());
                }
            }
        } else {
            for (Nivel n : catalogo) {
                AvanceNivel a = avance.get(n.getId());
                if (!a.getEstatus().cubreNivel()) {
                    if (a.getEstatus() == EstatusAvance.NO_PERMITIDO) {
                        a.setEstatus(EstatusAvance.HABILITADO);
                    }
                    break;
                }
            }
        }
        avanceRepository.saveAll(avance.values());
        return nuevos;
    }

    // ---------- Matrícula de control escolar ----------

    /** Lee la matrícula (encabezados matricula, semestre, genero, email, lengua_indigena, carrera). */
    Map<String, DatosMatricula> leerMatricula(Workbook libro) {
        Sheet hoja = libro.getSheetAt(0);
        Row encabezado = hoja.getRow(hoja.getFirstRowNum());
        if (encabezado == null) {
            throw new DatosInvalidosException("ALUM-028", "La matrícula no tiene encabezados.");
        }
        Map<String, Integer> indice = indiceEncabezados(encabezado);
        int control = buscar(indice, "matricula", "numero de control", "no. control", "no control");
        if (control < 0) {
            throw new DatosInvalidosException("ALUM-028",
                    "La matrícula debe tener la columna «matricula» (número de control).");
        }
        int semestre = buscar(indice, "semestre");
        int genero = buscar(indice, "genero", "sexo");
        int correo = buscar(indice, "email", "correo");
        int lengua = buscar(indice, "lengua_indigena", "lengua indigena", "lengua materna");
        int carrera = buscar(indice, "carrera");
        DataFormatter formato = new DataFormatter(new Locale("es", "MX"));
        Map<String, DatosMatricula> datos = new HashMap<>();
        for (int f = hoja.getFirstRowNum() + 1; f <= hoja.getLastRowNum(); f++) {
            Row fila = hoja.getRow(f);
            if (fila == null) continue;
            String nc = numeroControl(texto(fila, control, formato));
            if (nc.isEmpty()) continue;
            datos.put(nc, new DatosMatricula(
                    semestre < 0 ? null : semestre(texto(fila, semestre, formato)),
                    genero < 0 ? null : sexo(texto(fila, genero, formato)),
                    correo < 0 ? null : correo(texto(fila, correo, formato)),
                    lengua >= 0 && hablaLengua(texto(fila, lengua, formato)),
                    carrera < 0 ? null : limpio(texto(fila, carrera, formato))));
        }
        return datos;
    }

    private static Integer semestre(String valor) {
        try {
            int s = (int) Double.parseDouble(valor.trim());
            return s >= 1 && s <= 15 ? s : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** En la matrícula: M = masculino (H en el sistema), F = femenino (M en el sistema). */
    static String sexo(String valor) {
        String v = normalizar(valor);
        if (v.equals("m") || v.startsWith("masc") || v.equals("h") || v.startsWith("hom")) return "H";
        if (v.equals("f") || v.startsWith("fem") || v.startsWith("muj")) return "M";
        return null;
    }

    private static String correo(String valor) {
        String v = valor.trim();
        return v.length() <= 80 && v.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? v : null;
    }

    static boolean hablaLengua(String valor) {
        String v = normalizar(valor).replaceAll("[.]+$", "");
        return !SIN_LENGUA.contains(v) && !v.startsWith("no ");
    }

    // ---------- Interpretación de celdas ----------

    static Double calificacion(String texto) {
        if (texto == null) return null;
        Matcher m = NUMERO_INICIAL.matcher(texto);
        if (!m.find()) return null;
        double valor = Double.parseDouble(m.group(1).replace(',', '.'));
        return valor >= 0 && valor <= 100 ? valor : null;
    }

    static boolean esNoAcreditado(String texto) {
        String v = normalizar(texto).replace(" ", "");
        return v.startsWith("na") || v.startsWith("n/a") || v.startsWith("n.a") || v.startsWith("noacredit")
                || v.startsWith("reprob");
    }

    /** Periodo como texto; si la celda es una fecha, se escribe como «Mes AAAA» (por ejemplo «Enero 2026»). */
    private static String periodo(Cell celda, DataFormatter formato) {
        if (celda == null) return "";
        if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            LocalDate fecha = celda.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return MESES[fecha.getMonthValue() - 1] + " " + fecha.getYear();
        }
        String texto = limpio(formato.formatCellValue(celda));
        Matcher fecha = Pattern.compile("^(\\d{4})-(\\d{2})-\\d{2}( 00:00(:00)?)?$").matcher(texto);
        if (fecha.matches()) {
            int mes = Integer.parseInt(fecha.group(2));
            if (mes >= 1 && mes <= 12) return MESES[mes - 1] + " " + fecha.group(1);
        }
        return texto.replace('–', '-').replace('—', '-').replaceAll("\\s*-\\s*", " - ");
    }

    private static String numeroControl(String valor) {
        return valor == null ? "" : valor.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    // ---------- Encabezados ----------

    private static Columnas columnas(Map<String, Integer> indice) {
        int control = buscar(indice, "no. control", "no control", "numero de control", "num. control");
        int carrera = buscar(indice, "carrera");
        int nombre = buscar(indice, "nombre", "nombre(s)");
        int paterno = buscar(indice, "apellido paterno");
        int materno = buscar(indice, "apellido materno");
        int liberado = buscar(indice, "liberado");
        List<String> faltan = new ArrayList<>();
        if (control < 0) faltan.add("No. Control");
        if (carrera < 0) faltan.add("Carrera");
        if (nombre < 0) faltan.add("Nombre");
        if (paterno < 0) faltan.add("Apellido Paterno");
        List<Bloque> bloques = new ArrayList<>();
        for (String nivel : NIVELES) {
            Integer c = indice.get(normalizar(nivel));
            if (c != null) bloques.add(new Bloque(nivel, c));
        }
        if (bloques.isEmpty()) faltan.add("columnas de nivel (Básico 1 … Intermedio 5)");
        if (!faltan.isEmpty()) {
            throw new DatosInvalidosException("ALUM-029", "Al concentrado le faltan las columnas: "
                    + String.join(", ", faltan) + ".");
        }
        return new Columnas(control, carrera, nombre, paterno, materno, liberado, bloques);
    }

    private static Map<String, Integer> indiceEncabezados(Row encabezado) {
        DataFormatter formato = new DataFormatter();
        Map<String, Integer> indice = new HashMap<>();
        for (Cell c : encabezado) {
            String clave = normalizar(formato.formatCellValue(c));
            if (!clave.isEmpty()) indice.putIfAbsent(clave, c.getColumnIndex());
        }
        return indice;
    }

    private static int buscar(Map<String, Integer> indice, String... nombres) {
        for (String n : nombres) {
            Integer c = indice.get(normalizar(n));
            if (c != null) return c;
        }
        return -1;
    }

    // ---------- Utilidades ----------

    private Map<String, Carrera> indexarCarreras() {
        Map<String, Carrera> indice = new HashMap<>();
        for (Carrera c : carreraRepository.findAll()) {
            indice.put(claveCarrera(c.getNombre()), c);
            if (c.getClavePlan() != null) indice.put(claveCarrera(c.getClavePlan()), c);
        }
        return indice;
    }

    /** «Ingeniería en gestión Empresarial », «INGENIERIA EN GESTION EMPRESARIAL virtual» → misma carrera. */
    static String claveCarrera(String valor) {
        return normalizar(valor).replaceAll("\\s+virtual$", "");
    }

    static String normalizar(String valor) {
        if (valor == null) return "";
        String sinAcentos = Normalizer.normalize(valor.replace(' ', ' '), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.replace(' ', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String texto(Row fila, int columna, DataFormatter formato) {
        if (columna < 0) return "";
        Cell celda = fila.getCell(columna);
        return celda == null ? "" : formato.formatCellValue(celda).trim();
    }

    private static boolean filaVacia(Row fila, Columnas col, DataFormatter formato) {
        return texto(fila, col.control(), formato).isEmpty() && texto(fila, col.nombre(), formato).isEmpty()
                && texto(fila, col.paterno(), formato).isEmpty();
    }

    private static boolean esSi(String valor) {
        String v = normalizar(valor);
        return v.startsWith("si") || v.equals("s") || v.equals("x") || v.equals("1") || v.startsWith("yes");
    }

    private static String recortar(String valor, int maximo) {
        return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private static String nulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static String mensaje(RuntimeException e) {
        Throwable causa = e;
        while (causa.getCause() != null && causa.getCause() != causa) causa = causa.getCause();
        String m = e.getMessage() != null && !(e instanceof org.springframework.dao.DataAccessException)
                ? e.getMessage() : causa.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
