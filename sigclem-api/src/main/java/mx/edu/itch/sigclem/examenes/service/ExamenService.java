package mx.edu.itch.sigclem.examenes.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.examenes.domain.CriterioEvaluacion;
import mx.edu.itch.sigclem.examenes.domain.Examen;
import mx.edu.itch.sigclem.examenes.dto.CapturaResultadoRequest;
import mx.edu.itch.sigclem.examenes.dto.EdicionExamenRequest;
import mx.edu.itch.sigclem.examenes.dto.ExamenDTO;
import mx.edu.itch.sigclem.examenes.dto.ProgramacionExamenRequest;
import mx.edu.itch.sigclem.examenes.dto.RegistroExamenRequest;
import mx.edu.itch.sigclem.examenes.dto.SolicitudExamenRequest;
import mx.edu.itch.sigclem.examenes.repository.CriterioEvaluacionRepository;
import mx.edu.itch.sigclem.examenes.repository.ExamenRepository;
import mx.edu.itch.sigclem.examenes.repository.ResultadoCriterioRepository;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class ExamenService {

    private static final String COLOCACION = "Colocación";

    private final ExamenRepository examenRepository;
    private final ResultadoCriterioRepository resultadoCriterioRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final NivelRepository nivelRepository;
    private final UsuarioRepository usuarioRepository;

    /** CU-04: Solicitar examen de colocación o de cuatro habilidades. */
    @Transactional
    public ExamenDTO solicitar(SolicitudExamenRequest req, String usuarioActual) {
        if (usuarioActual == null || usuarioActual.isBlank()) {
            throw new AccessDeniedException("La solicitud requiere una sesión de alumno autenticada.");
        }
        String nc = usuarioActual.trim();
        if (req.numeroControl() != null && !req.numeroControl().isBlank()
                && !nc.equals(req.numeroControl().trim())) {
            throw new AccessDeniedException("Solo puedes solicitar exámenes para tu propio expediente.");
        }

        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(nc)
                .orElseThrow(() -> new RecursoNoEncontradoException("EXAM-001",
                        "No se encontró al alumno con número de control " + nc));

        if (examenRepository.existeExamenPendiente(nc, req.tipo())) {
            throw new ConflictoException("EXAM-002",
                    "Ya existe una solicitud pendiente de examen de " + req.tipo() + " para el alumno " + nc);
        }

        LocalDateTime fechaAplicacion = req.fechaHoraTentativa() != null
                ? req.fechaHoraTentativa()
                : LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);

        Examen examen = new Examen();
        examen.setAlumno(alumno);
        examen.setTipo(req.tipo());
        examen.setFechaHoraAplicacion(fechaAplicacion);
        return mapearDTO(examenRepository.save(examen));
    }

    /** Registro directo por coordinación de un examen ya aplicado, con su nivel asignado. */
    @Transactional
    public ExamenDTO registrarDirecto(RegistroExamenRequest req, String usuarioEvaluador) {
        String nc = req.numeroControl().trim().toUpperCase();
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(nc)
                .orElseThrow(() -> new RecursoNoEncontradoException("EXAM-001",
                        "No se encontró al alumno con número de control " + nc));
        Examen examen = new Examen();
        examen.setAlumno(alumno);
        examen.setTipo(req.tipo());
        examen.setFechaHoraAplicacion(req.fechaHoraAplicacion());
        examen = examenRepository.save(examen);
        return asignarResultado(examen, new CapturaResultadoRequest(req.idNivelAsignado(), req.puntaje(),
                req.observaciones()), usuarioEvaluador);
    }

    /** CU-05: Programar examen asignando fecha, horario y evaluador. */
    @Transactional
    public ExamenDTO programar(Integer idExamen, ProgramacionExamenRequest req) {
        Examen examen = buscarVisible(idExamen);
        if (examen.estaConcluido()) {
            throw new ConflictoException("EXAM-004", "El examen ya fue evaluado; edita sus datos en lugar de reprogramarlo.");
        }
        examen.setFechaHoraAplicacion(req.fechaHoraAplicacion());
        if (req.idUsuarioEvaluador() != null) {
            Usuario evaluador = usuarioRepository.findById(req.idUsuarioEvaluador())
                    .orElseThrow(() -> new DatosInvalidosException("EXAM-005", "El usuario evaluador no existe."));
            examen.setEvaluador(evaluador);
        }
        return mapearDTO(examenRepository.save(examen));
    }

    /** Edita tipo y fecha. Si el examen ya tiene resultado y cambia el tipo, se recalcula el avance. */
    @Transactional
    public ExamenDTO editar(Integer idExamen, EdicionExamenRequest req) {
        Examen examen = buscarVisible(idExamen);
        boolean cambiaTipo = !examen.getTipo().equals(req.tipo());
        examen.setTipo(req.tipo());
        examen.setFechaHoraAplicacion(req.fechaHoraAplicacion());
        if (examen.estaConcluido() && cambiaTipo) {
            aplicarAvance(examen.getAlumno().getNumeroControl(), examen.getNivelAsignado(), examen.getTipo());
        }
        return mapearDTO(examenRepository.save(examen));
    }

    /** Borrado lógico: el examen se oculta de las consultas, pero no se elimina ni modifica el avance. */
    @Transactional
    public void ocultar(Integer idExamen) {
        Examen examen = buscarVisible(idExamen);
        examen.setOculto(true);
        examenRepository.save(examen);
    }

    /**
     * CU-06: Captura o corrige el resultado asignando directamente el nivel (RF-07). Ya no hay rúbrica,
     * firma del evaluador ni tope de tres niveles: un alumno puede quedar directamente en B1.
     */
    @Transactional
    public ExamenDTO capturarResultado(Integer idExamen, CapturaResultadoRequest req, String usuarioEvaluador) {
        return asignarResultado(buscarVisible(idExamen), req, usuarioEvaluador);
    }

    private ExamenDTO asignarResultado(Examen examen, CapturaResultadoRequest req, String usuarioEvaluador) {
        Nivel nivelAsignado = nivelRepository.findById(req.idNivelAsignado())
                .orElseThrow(() -> new DatosInvalidosException("EXAM-007", "El nivel asignado no existe."));

        // Restos de la rúbrica anterior: ya no forman parte del resultado.
        resultadoCriterioRepository.deleteByIdIdExamen(examen.getId());

        aplicarAvance(examen.getAlumno().getNumeroControl(), nivelAsignado, examen.getTipo());

        examen.setNivelAsignado(nivelAsignado);
        examen.setPuntajeTotal(req.puntaje());
        examen.setObservaciones(req.observaciones() == null || req.observaciones().isBlank()
                ? null : req.observaciones().trim());
        examen.setFirmaEvaluador(null);
        if (usuarioEvaluador != null) {
            usuarioRepository.findByNombreUsuario(usuarioEvaluador).ifPresent(examen::setEvaluador);
        }
        return mapearDTO(examenRepository.save(examen));
    }

    /**
     * Recalcula el avance reticular según el último resultado del examen (también al corregirlo):
     * <ul>
     * <li>Colocación: los niveles anteriores al asignado quedan exentos y el asignado, habilitado.</li>
     * <li>Cuatro habilidades: acredita el dominio hasta el nivel asignado inclusive (exentos).</li>
     * </ul>
     * Los niveles acreditados o en curso por un grupo se conservan. Las exenciones por examen que queden
     * por encima del nuevo resultado se retiran, y siempre se habilita el primer nivel que falte por cubrir.
     */
    void aplicarAvance(String nc, Nivel nivelAsignado, String tipo) {
        boolean colocacion = COLOCACION.equalsIgnoreCase(tipo);
        String origen = colocacion ? "Examen de colocación" : "Examen de cuatro habilidades";
        int limiteExento = colocacion ? nivelAsignado.getNumero() - 1 : nivelAsignado.getNumero();

        List<Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc();
        Map<Integer, AvanceNivel> porNivel = avanceRepository.findByIdNumeroControl(nc).stream()
                .collect(Collectors.toMap(a -> a.getId().getIdNivel(), Function.identity()));

        for (Nivel n : niveles) {
            AvanceNivel an = porNivel.computeIfAbsent(n.getId(),
                    id -> new AvanceNivel(nc, id, EstatusAvance.NO_PERMITIDO));
            EstatusAvance e = an.getEstatus();
            if (e == EstatusAvance.ACREDITADO || e == EstatusAvance.CURSANDO) {
                continue;
            }
            if (n.getNumero() <= limiteExento) {
                cambiar(an, EstatusAvance.EXENTO, origen);
            } else if (e == EstatusAvance.EXENTO || e == EstatusAvance.HABILITADO) {
                cambiar(an, EstatusAvance.NO_PERMITIDO, null);
            }
        }
        niveles.stream()
                .map(n -> porNivel.get(n.getId()))
                .filter(a -> !a.getEstatus().cubreNivel())
                .findFirst()
                .filter(a -> a.getEstatus() == EstatusAvance.NO_PERMITIDO || a.getEstatus() == EstatusAvance.REPROBADO)
                .ifPresent(a -> cambiar(a, EstatusAvance.HABILITADO, a.getOrigen()));
        avanceRepository.saveAll(porNivel.values());
    }

    private static void cambiar(AvanceNivel an, EstatusAvance estatus, String origen) {
        an.setEstatus(estatus);
        an.setOrigen(origen);
        an.setFechaRegistro(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ExamenDTO> listar(String numeroControl, String tipo, Boolean soloPendientes) {
        List<Examen> lista;
        if (numeroControl != null && !numeroControl.isBlank()) {
            lista = examenRepository.findByAlumnoNumeroControlAndOcultoFalseOrderByFechaHoraAplicacionDesc(
                    numeroControl.trim());
        } else if (Boolean.TRUE.equals(soloPendientes)) {
            lista = examenRepository.findByNivelAsignadoIsNullAndOcultoFalseOrderByFechaHoraAplicacionAsc();
        } else {
            lista = examenRepository.findByOcultoFalseOrderByFechaHoraAplicacionDesc();
        }
        if (tipo != null && !tipo.isBlank()) {
            lista = lista.stream().filter(e -> e.getTipo().equalsIgnoreCase(tipo.trim())).toList();
        }
        return lista.stream().map(this::mapearDTO).toList();
    }

    @Transactional(readOnly = true)
    public ExamenDTO obtenerPorId(Integer idExamen) {
        return mapearDTO(buscarVisible(idExamen));
    }

    @Transactional(readOnly = true)
    public List<CriterioEvaluacion> listarCriterios() {
        return criterioRepository.findAllByOrderByIdAsc();
    }

    private Examen buscarVisible(Integer idExamen) {
        return examenRepository.findConDetallesById(idExamen)
                .filter(e -> !e.isOculto())
                .orElseThrow(() -> new RecursoNoEncontradoException("EXAM-003",
                        "No se encontró el examen con identificador " + idExamen));
    }

    private ExamenDTO mapearDTO(Examen e) {
        return new ExamenDTO(
                e.getId(),
                e.getAlumno().getNumeroControl(),
                e.getAlumno().nombreCompleto(),
                e.getAlumno().getCarrera().getNombre(),
                e.getTipo(),
                e.getFechaHoraAplicacion(),
                e.getPuntajeTotal(),
                e.getObservaciones(),
                e.getEvaluador() != null ? e.getEvaluador().getNombreUsuario() : null,
                e.getNivelAsignado() != null ? e.getNivelAsignado().getId() : null,
                e.getNivelAsignado() != null ? e.getNivelAsignado().getNombre() : null,
                e.estaConcluido());
    }
}
