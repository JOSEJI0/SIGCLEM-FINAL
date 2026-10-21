package mx.edu.itch.sigclem.diagnosticos.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.diagnosticos.domain.EvaluacionDiagnostica;
import mx.edu.itch.sigclem.diagnosticos.dto.EvaluacionDiagnosticaDTO;
import mx.edu.itch.sigclem.diagnosticos.dto.ProgramarDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.dto.RegistroDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.dto.ResultadoDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.repository.EvaluacionDiagnosticaRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class EvaluacionDiagnosticaService {

    private static final List<String> ESTATUS_ACTIVOS = List.of("Solicitada", "Programada");
    private static final List<String> RECOMENDACIONES = List.of("Básico 1", "Colocación");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EvaluacionDiagnosticaRepository diagnosticoRepository;
    private final AlumnoRepository alumnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;

    @Transactional
    public EvaluacionDiagnosticaDTO solicitar(String nombreUsuario) {
        Alumno alumno = buscarAlumno(nombreUsuario);
        if (diagnosticoRepository.existsByAlumnoNumeroControlAndEstatusIn(
                alumno.getNumeroControl(), ESTATUS_ACTIVOS)) {
            throw new ConflictoException("DIAG-002",
                    "Ya existe una evaluación diagnóstica pendiente o programada para este alumno.");
        }

        EvaluacionDiagnostica diagnostico = new EvaluacionDiagnostica();
        diagnostico.setAlumno(alumno);
        diagnostico.setFechaSolicitud(LocalDate.now());
        diagnostico.setEstatus("Solicitada");
        EvaluacionDiagnostica guardado = diagnosticoRepository.save(diagnostico);
        Usuario coordinador = usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc("Coordinador")
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "DIAG-008", "No hay una cuenta activa para coordinación."));
        notificar(coordinador, guardado, "Nueva solicitud de evaluación diagnóstica",
                "El alumno " + alumno.nombreCompleto() + " (" + alumno.getNumeroControl()
                        + ") solicitó una evaluación diagnóstica.");
        return EvaluacionDiagnosticaDTO.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<EvaluacionDiagnosticaDTO> listarPropias(String nombreUsuario) {
        Alumno alumno = buscarAlumno(nombreUsuario);
        return diagnosticoRepository
                .findByAlumnoNumeroControlOrderByFechaSolicitudDescIdDesc(alumno.getNumeroControl())
                .stream().map(EvaluacionDiagnosticaDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<EvaluacionDiagnosticaDTO> listarTodas() {
        return diagnosticoRepository.findAllByOrderByFechaSolicitudDescIdDesc()
                .stream().map(EvaluacionDiagnosticaDTO::desde).toList();
    }

    @Transactional
    public EvaluacionDiagnosticaDTO programar(
            Integer id, ProgramarDiagnosticoRequest solicitud, String nombreUsuario) {
        EvaluacionDiagnostica diagnostico = buscar(id);
        if ("Concluida".equals(diagnostico.getEstatus())) {
            throw new ConflictoException("DIAG-003", "Una evaluación concluida no puede reprogramarse.");
        }
        Usuario coordinador = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "DIAG-004", "No se encontró el usuario autenticado."));
        diagnostico.setFechaHoraAplicacion(solicitud.fechaHoraAplicacion());
        diagnostico.setEnlace(solicitud.enlace().trim());
        diagnostico.setUsuarioPrograma(coordinador);
        diagnostico.setEstatus("Programada");
        EvaluacionDiagnostica guardado = diagnosticoRepository.save(diagnostico);
        notificar(diagnostico.getAlumno().getUsuario(), guardado, "Evaluación diagnóstica programada",
                "Tu evaluación está programada para "
                        + solicitud.fechaHoraAplicacion().format(FECHA_HORA)
                        + ". Enlace: " + diagnostico.getEnlace());
        return EvaluacionDiagnosticaDTO.desde(guardado);
    }

    /** Registro directo por coordinación de una evaluación ya aplicada (sin solicitud previa del alumno). */
    @Transactional
    public EvaluacionDiagnosticaDTO registrarDirecto(RegistroDiagnosticoRequest solicitud, String nombreUsuario) {
        String nc = solicitud.numeroControl().trim().toUpperCase();
        Alumno alumno = alumnoRepository.findById(nc)
                .orElseThrow(() -> new RecursoNoEncontradoException("DIAG-009",
                        "No existe un alumno con número de control " + nc + "."));
        Usuario coordinador = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "DIAG-004", "No se encontró el usuario autenticado."));
        EvaluacionDiagnostica diagnostico = new EvaluacionDiagnostica();
        diagnostico.setAlumno(alumno);
        diagnostico.setFechaSolicitud(solicitud.fechaHoraAplicacion().toLocalDate());
        diagnostico.setFechaHoraAplicacion(solicitud.fechaHoraAplicacion());
        diagnostico.setEnlace("Registro directo de coordinación");
        diagnostico.setUsuarioPrograma(coordinador);
        diagnostico.setEstatus("Programada");
        diagnosticoRepository.save(diagnostico);
        return registrarResultado(diagnostico.getId(), new ResultadoDiagnosticoRequest(
                solicitud.puntaje(), solicitud.recomendacion(), solicitud.observaciones()));
    }

    /** Registra el resultado de una evaluación programada, o lo corrige si ya estaba concluida. */
    @Transactional
    public EvaluacionDiagnosticaDTO registrarResultado(Integer id, ResultadoDiagnosticoRequest solicitud) {
        EvaluacionDiagnostica diagnostico = buscar(id);
        boolean correccion = "Concluida".equals(diagnostico.getEstatus());
        if (!"Programada".equals(diagnostico.getEstatus()) && !correccion) {
            throw new ConflictoException("DIAG-005",
                    "Primero programa la evaluación para registrar su resultado.");
        }
        if (!RECOMENDACIONES.contains(solicitud.recomendacion())) {
            throw new DatosInvalidosException("DIAG-006",
                    "La recomendación debe ser 'Básico 1' o 'Colocación'.");
        }
        diagnostico.setPuntaje(solicitud.puntaje());
        diagnostico.setRecomendacion(solicitud.recomendacion());
        diagnostico.setObservaciones(solicitud.observaciones());
        diagnostico.setEstatus("Concluida");
        EvaluacionDiagnostica guardado = diagnosticoRepository.save(diagnostico);
        String mensaje = (correccion ? "Se corrigió el resultado de tu evaluación: " : "Tu evaluación obtuvo ")
                + solicitud.puntaje() + "/100. Recomendación: " + solicitud.recomendacion();
        notificar(diagnostico.getAlumno().getUsuario(), guardado,
                correccion ? "Resultado de evaluación diagnóstica corregido" : "Resultado de evaluación diagnóstica",
                mensaje);
        return EvaluacionDiagnosticaDTO.desde(guardado);
    }

    private EvaluacionDiagnostica buscar(Integer id) {
        return diagnosticoRepository.findConAlumnoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "DIAG-007", "No se encontró la evaluación diagnóstica indicada."));
    }

    private Alumno buscarAlumno(String nombreUsuario) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "DIAG-001", "No se encontró el usuario autenticado."));
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "DIAG-001", "No se encontró el expediente del alumno autenticado."));
    }

    private void notificar(Usuario destinatario, EvaluacionDiagnostica diagnostico, String asunto, String mensaje) {
        Notificacion notificacion = new Notificacion();
        notificacion.setDestino(destinatario);
        notificacion.setDiagnostico(diagnostico);
        notificacion.setAsunto(asunto);
        notificacion.setMensaje(mensaje);
        notificacion.setFechaEnvio(java.time.LocalDateTime.now());
        notificacion.setLeida(false);
        notificacionRepository.save(notificacion);
    }
}
