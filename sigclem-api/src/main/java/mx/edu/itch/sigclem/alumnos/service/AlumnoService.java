package mx.edu.itch.sigclem.alumnos.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.dto.AlumnoResumenDTO;
import mx.edu.itch.sigclem.alumnos.dto.AvanceDTO;
import mx.edu.itch.sigclem.alumnos.dto.ExpedienteDTO;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.RolRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class AlumnoService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    /** Valor del filtro de nivel para los alumnos que ya cubrieron los diez niveles. */
    public static final int NIVEL_IDIOMA_ACREDITADO = 0;
    private static final String IDIOMA_ACREDITADO = "Idioma acreditado";

    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final CarreraRepository carreraRepository;
    private final NivelRepository nivelRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final InscripcionRepository inscripcionRepository;
    private final PeriodoRepository periodoRepository;

    /**
     * CU-16/CU-21: consulta paginada para coordinación y supervisión con filtros independientes por carrera,
     * número de control, nombre y nivel actual (el primer nivel que el alumno aún no cubre).
     * {@code busqueda} se conserva como búsqueda general.
     */
    @Transactional(readOnly = true)
    public Page<AlumnoResumenDTO> buscar(String busqueda, Integer idCarrera, String numeroControl, String nombre,
            Integer nivel, Integer semestre, String estatus, Pageable pageable) {
        // Por omisión solo se muestran los alumnos activos; "Baja" o "Todos" incluyen a los dados de baja.
        String estatusFiltro = estatus == null || estatus.isBlank() ? "Activo" : estatus.trim();
        Pageable pagina = pageable.getPageSize() > TAMANO_MAXIMO_PAGINA
                ? PageRequest.of(pageable.getPageNumber(), TAMANO_MAXIMO_PAGINA)
                : pageable;
        List<Alumno> candidatos = alumnoRepository.filtrar(texto(busqueda), idCarrera, texto(numeroControl),
                texto(nombre));
        List<Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc();
        Map<String, List<AvanceNivel>> avancePorAlumno = candidatos.isEmpty() ? Map.of()
                : avanceRepository.findByIdNumeroControlIn(candidatos.stream().map(Alumno::getNumeroControl).toList())
                        .stream().collect(Collectors.groupingBy(a -> a.getId().getNumeroControl()));

        List<AlumnoResumenDTO> filtrados = candidatos.stream()
                .map(a -> {
                    Nivel actual = nivelActual(niveles, avancePorAlumno.getOrDefault(a.getNumeroControl(), List.of()));
                    return new AlumnoResumenDTO(a.getNumeroControl(), a.nombreCompleto(), a.getCarrera().getNombre(),
                            a.getSemestre(), a.getSexo(), a.getFechaActualizacionSemestre(), a.getCorreo(),
                            a.getFechaInscripcion(),
                            actual == null ? NIVEL_IDIOMA_ACREDITADO : actual.getNumero(),
                            actual == null ? IDIOMA_ACREDITADO : actual.getNombre(),
                            a.isHablaLenguaMaterna(), a.getEstatus());
                })
                .filter(dto -> "Todos".equalsIgnoreCase(estatusFiltro) || dto.estatus().equalsIgnoreCase(estatusFiltro))
                .filter(dto -> nivel == null || dto.numeroNivelActual().equals(nivel))
                .filter(dto -> semestre == null || dto.semestre().equals(semestre))
                .toList();

        int desde = (int) Math.min((long) pagina.getPageNumber() * pagina.getPageSize(), filtrados.size());
        int hasta = Math.min(desde + pagina.getPageSize(), filtrados.size());
        return new PageImpl<>(filtrados.subList(desde, hasta), pagina, filtrados.size());
    }

    /**
     * CU-02: crea la cuenta (siempre con rol Alumno), el expediente y el avance reticular inicial:
     * Básico 1 habilitado y los demás niveles no permitidos (RF-03, RF-04).
     */
    @Transactional
    public ExpedienteDTO registrar(RegistroAlumnoRequest req) {
        String nc = req.numeroControl().trim();
        if (alumnoRepository.existsById(nc) || usuarioRepository.existsByNombreUsuario(nc)) {
            throw new ConflictoException("ALUM-001", "El número de control " + nc + " ya está registrado.");
        }
        Carrera carrera = carreraRepository.findById(req.idCarrera())
                .orElseThrow(() -> new DatosInvalidosException("ALUM-002", "La carrera seleccionada no existe."));
        Rol rolAlumno = rolRepository.findByNombre(RolSistema.ALUMNO.nombreBd())
                .orElseThrow(() -> new IllegalStateException("Falta el rol Alumno en el catálogo"));

        // Contraseña inicial = número de control; el coordinador la cambia después en «Cuentas y contraseñas».
        Usuario usuario = usuarioRepository.save(
                new Usuario(nc, passwordEncoder.encode(nc), rolAlumno));

        Alumno alumno = new Alumno();
        alumno.setNumeroControl(nc);
        alumno.setUsuario(usuario);
        alumno.setNombre(req.nombre().trim());
        alumno.setApellidoPaterno(req.apellidoPaterno().trim());
        alumno.setApellidoMaterno(req.apellidoMaterno() == null || req.apellidoMaterno().isBlank()
                ? null : req.apellidoMaterno().trim());
        alumno.setSexo(req.sexo());
        alumno.setCarrera(carrera);
        alumno.setSemestre(req.semestre());
        alumno.setFechaActualizacionSemestre(LocalDate.now());
        alumno.setCorreo(req.correo());
        alumno.setFechaInscripcion(LocalDate.now());
        alumno.setHablaLenguaMaterna(Boolean.TRUE.equals(req.hablaLenguaMaterna()));
        alumnoRepository.save(alumno);

        List<AvanceNivel> avance = nivelRepository.findAllByOrderByNumeroAsc().stream()
                .map(n -> new AvanceNivel(nc, n.getId(),
                        n.getNumero() == 1 ? EstatusAvance.HABILITADO : EstatusAvance.NO_PERMITIDO))
                .toList();
        avanceRepository.saveAll(avance);

        return armarExpediente(alumno, avance);
    }

    @Transactional(readOnly = true)
    public ExpedienteDTO expediente(String numeroControl) {
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("ALUM-003",
                        "No existe un alumno con número de control " + numeroControl + "."));
        return armarExpediente(alumno, avanceRepository.findByIdNumeroControl(numeroControl));
    }

    @Transactional(readOnly = true)
    public List<AvanceDTO> avance(String numeroControl) {
        if (!alumnoRepository.existsById(numeroControl)) {
            throw new RecursoNoEncontradoException("ALUM-003",
                    "No existe un alumno con número de control " + numeroControl + ".");
        }
        return detalleAvance(numeroControl, avanceRepository.findByIdNumeroControl(numeroControl),
                nivelRepository.findAllByOrderByNumeroAsc());
    }

    private ExpedienteDTO armarExpediente(Alumno alumno, List<AvanceNivel> avance) {
        List<Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc();
        List<AvanceDTO> detalle = detalleAvance(alumno.getNumeroControl(), avance, niveles);
        int cubiertos = (int) avance.stream().filter(a -> a.getEstatus().cubreNivel()).count();
        Nivel actual = nivelActual(niveles, avance);
        String formaLiberacion = actual == null ? formaLiberacion(avance) : null;
        return new ExpedienteDTO(alumno.getNumeroControl(), alumno.nombreCompleto(), alumno.getCarrera().getNombre(),
                alumno.getSemestre(), alumno.getSexo(), alumno.getFechaActualizacionSemestre(),
                semestrePorConfirmar(alumno), alumno.getCorreo(), alumno.getFechaInscripcion(),
                actual == null ? IDIOMA_ACREDITADO : actual.getNombre(), cubiertos, detalle,
                alumno.isHablaLenguaMaterna(), alumno.getEstatus(), alumno.getMotivoBaja(), alumno.getFechaBaja(),
                formaLiberacion);
    }

    /** Deduce con qué forma el alumno liberó la lengua extranjera, a partir del origen de los niveles cubiertos. */
    private static String formaLiberacion(List<AvanceNivel> avance) {
        java.util.Set<String> origenes = avance.stream()
                .filter(a -> a.getEstatus().cubreNivel())
                .map(AvanceNivel::getOrigen)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (String forma : List.of("Examen global", "Lengua materna", "Otra")) {
            if (origenes.contains(forma)) {
                return forma;
            }
        }
        if (origenes.stream().anyMatch(o -> o.startsWith("Examen"))) {
            return "Examen de colocación";
        }
        return "Curso";
    }

    /**
     * Libera la lengua extranjera por una forma distinta a completar los diez niveles (examen global, lengua materna
     * u otra): deja exentos todos los niveles que aún no están acreditados o exentos, con ese origen, de modo que el
     * expediente muestra «Idioma acreditado» y la forma de liberación.
     */
    @Transactional
    public ExpedienteDTO liberarIdioma(String numeroControl, String forma, String detalle) {
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("ALUM-003",
                        "No existe un alumno con número de control " + numeroControl + "."));
        List<AvanceNivel> avance = avanceRepository.findByIdNumeroControl(numeroControl);
        if (avance.stream().allMatch(a -> a.getEstatus().cubreNivel())) {
            throw new DatosInvalidosException("ALUM-013", "El alumno ya tiene la lengua extranjera acreditada.");
        }
        // El origen guarda la forma (cabe en 30 caracteres); el detalle queda en la bitácora de auditoría.
        avance.stream()
                .filter(a -> !a.getEstatus().cubreNivel())
                .forEach(a -> {
                    a.setEstatus(EstatusAvance.EXENTO);
                    a.setOrigen(forma);
                });
        avanceRepository.saveAll(avance);
        return armarExpediente(alumno, avance);
    }

    /**
     * Baja manual de un alumno (terminó el curso, abandonó, u otro motivo): queda fuera de las listas y los reportes
     * activos, su cuenta se desactiva (ya no puede iniciar sesión) y se conserva todo su historial. Es reversible.
     */
    @Transactional
    public ExpedienteDTO darDeBaja(String numeroControl, String motivo) {
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("ALUM-003",
                        "No existe un alumno con número de control " + numeroControl + "."));
        if (alumno.estaDeBaja()) {
            throw new DatosInvalidosException("ALUM-011", "El alumno ya está dado de baja.");
        }
        alumno.setEstatus("Baja");
        alumno.setMotivoBaja(motivo.trim());
        alumno.setFechaBaja(LocalDate.now());
        if (alumno.getUsuario() != null) {
            alumno.getUsuario().setActivo(false);
        }
        alumnoRepository.save(alumno);
        return armarExpediente(alumno, avanceRepository.findByIdNumeroControl(numeroControl));
    }

    /** Reactiva a un alumno dado de baja: vuelve a las listas activas y su cuenta puede iniciar sesión de nuevo. */
    @Transactional
    public ExpedienteDTO reactivar(String numeroControl) {
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("ALUM-003",
                        "No existe un alumno con número de control " + numeroControl + "."));
        if (!alumno.estaDeBaja()) {
            throw new DatosInvalidosException("ALUM-012", "El alumno ya está activo.");
        }
        alumno.setEstatus("Activo");
        alumno.setMotivoBaja(null);
        alumno.setFechaBaja(null);
        if (alumno.getUsuario() != null) {
            alumno.getUsuario().setActivo(true);
        }
        alumnoRepository.save(alumno);
        return armarExpediente(alumno, avanceRepository.findByIdNumeroControl(numeroControl));
    }

    /**
     * El alumno actualiza su semestre (la coordinación no tiene conexión con control escolar): queda registrada la
     * fecha para saber si ya lo confirmó en el periodo vigente.
     */
    @Transactional
    public ExpedienteDTO actualizarSemestre(String numeroControl, Integer semestre) {
        if (semestre == null || semestre < 1 || semestre > 12) {
            throw new DatosInvalidosException("ALUM-010", "El semestre debe estar entre 1 y 12.");
        }
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("ALUM-003",
                        "No existe un alumno con número de control " + numeroControl + "."));
        alumno.setSemestre(semestre);
        alumno.setFechaActualizacionSemestre(LocalDate.now());
        alumnoRepository.save(alumno);
        return armarExpediente(alumno, avanceRepository.findByIdNumeroControl(numeroControl));
    }

    /** Sin confirmación, o confirmada antes de que iniciara el periodo vigente. */
    private boolean semestrePorConfirmar(Alumno alumno) {
        LocalDate fecha = alumno.getFechaActualizacionSemestre();
        if (fecha == null) {
            return true;
        }
        LocalDate hoy = LocalDate.now();
        return periodoRepository.findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioDesc(hoy, hoy)
                .map(p -> fecha.isBefore(p.getFechaInicio()))
                .orElse(false);
    }

    /** Detalle de los diez niveles con la calificación final de las actas cerradas (se muestra al pasar el mouse). */
    private List<AvanceDTO> detalleAvance(String numeroControl, List<AvanceNivel> avance, List<Nivel> niveles) {
        Map<Integer, AvanceNivel> porNivel = avance.stream()
                .collect(Collectors.toMap(a -> a.getId().getIdNivel(), Function.identity()));
        Map<Integer, Integer> calificacionPorNivel = new HashMap<>();
        inscripcionRepository.findByAlumnoNumeroControl(numeroControl).stream()
                .filter(i -> "Cerrada".equals(i.getGrupo().getEstatusActa()) && i.getCalificacionFinal() != null)
                .sorted(Comparator.comparing((Inscripcion i) -> i.getGrupo().getFechaCierreActa()))
                // La más reciente prevalece (por ejemplo, si reprobó y volvió a cursar el nivel).
                .forEach(i -> calificacionPorNivel.put(i.getGrupo().getNivel().getId(), i.getCalificacionFinal()));

        return niveles.stream().map(n -> {
            AvanceNivel a = porNivel.get(n.getId());
            EstatusAvance e = a == null ? EstatusAvance.NO_PERMITIDO : a.getEstatus();
            return new AvanceDTO(n.getNumero(), n.getNombre(), n.getNivelMcer(), e.etiqueta(),
                    a == null ? null : a.getOrigen(), calificacionPorNivel.get(n.getId()));
        }).toList();
    }

    /** Primer nivel que el alumno aún no cubre (acreditado o exento); {@code null} si ya cubrió los diez. */
    private static Nivel nivelActual(List<Nivel> niveles, List<AvanceNivel> avance) {
        Map<Integer, AvanceNivel> porNivel = avance.stream()
                .collect(Collectors.toMap(a -> a.getId().getIdNivel(), Function.identity()));
        return niveles.stream()
                .filter(n -> {
                    AvanceNivel a = porNivel.get(n.getId());
                    return a == null || !a.getEstatus().cubreNivel();
                })
                .findFirst()
                .orElse(null);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
