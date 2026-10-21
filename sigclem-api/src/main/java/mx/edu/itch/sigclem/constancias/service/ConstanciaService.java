package mx.edu.itch.sigclem.constancias.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.constancias.domain.Constancia;
import mx.edu.itch.sigclem.constancias.dto.AutorizarConstanciaRequest;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.ElegibilidadConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.RechazarConstanciaRequest;
import mx.edu.itch.sigclem.constancias.repository.ConstanciaRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class ConstanciaService {

    private static final String IDIOMA = "Inglés";
    private static final List<String> ESTATUS_DUPLICADO = List.of("Solicitada", "Autorizada", "Emitida");
    private static final Set<String> ESTATUS_VALIDOS = Set.of("Solicitada", "Autorizada", "Rechazada", "Emitida");

    private final ConstanciaRepository constanciaRepository;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceNivelRepository;
    private final NivelRepository nivelRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;
    private final ConstanciaPdfService pdfService;
    private final ArchivoConstanciaService archivoService;

    @Transactional(readOnly = true)
    public ElegibilidadConstanciaDTO consultarElegibilidad(String nombreUsuario) {
        return elegibilidad(buscarAlumno(nombreUsuario));
    }

    @Transactional
    public ConstanciaDTO solicitar(String nombreUsuario) {
        Alumno alumno = buscarAlumno(nombreUsuario);
        Constancia guardada = crearSolicitud(alumno);
        Usuario coordinador = usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc(
                        RolSistema.COORDINADOR.nombreBd())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CON-003", "No hay una cuenta activa de coordinación para recibir la solicitud."));
        notificar(null, coordinador, guardada, "Nueva solicitud de constancia",
                alumno.nombreCompleto() + " (" + alumno.getNumeroControl()
                        + ") solicitó su constancia de acreditación de inglés.");
        return ConstanciaDTO.desde(guardada);
    }

    /**
     * Coordinación crea y emite la constancia de un alumno aunque él no la haya pedido. Se exigen los mismos
     * requisitos (diez niveles acreditados o exentos) y el folio único.
     */
    @Transactional
    public ConstanciaDTO emitirDirecta(String numeroControl, String folio, String nombreCoordinador) {
        String nc = numeroControl.trim().toUpperCase();
        Alumno alumno = alumnoRepository.findConCarreraByNumeroControl(nc)
                .orElseThrow(() -> new RecursoNoEncontradoException("CON-010", "No existe el alumno " + nc + "."));
        Constancia solicitud = crearSolicitud(alumno);
        return autorizar(solicitud.getId(), new AutorizarConstanciaRequest(folio), nombreCoordinador);
    }

    private Constancia crearSolicitud(Alumno alumno) {
        ElegibilidadConstanciaDTO progreso = elegibilidad(alumno);
        if (!progreso.elegible()) {
            throw new DatosInvalidosException("CON-001",
                    "Acredita los diez niveles de inglés para solicitar la constancia. Faltan: "
                            + String.join(", ", progreso.nivelesFaltantes()) + ".");
        }
        if (constanciaRepository.existsByAlumnoNumeroControlAndIdiomaIgnoreCaseAndEstatusIn(
                alumno.getNumeroControl(), IDIOMA, ESTATUS_DUPLICADO)) {
            throw new ConflictoException("CON-002",
                    "Ya existe una solicitud en trámite o una constancia emitida para inglés.");
        }

        Constancia constancia = new Constancia();
        constancia.setAlumno(alumno);
        constancia.setFechaSolicitud(LocalDate.now());
        constancia.setIdioma(IDIOMA);
        constancia.setNivelesAcreditados(progreso.nivelesAcreditados());
        constancia.setTotalHoras(progreso.horasAcreditadas());
        constancia.setNivelMcer(progreso.nivelMcer());
        constancia.setEstatus("Solicitada");
        try {
            return constanciaRepository.saveAndFlush(constancia);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("CON-002",
                    "Ya existe una solicitud en trámite o una constancia emitida para inglés.");
        }
    }

    @Transactional(readOnly = true)
    public List<ConstanciaDTO> listar(String estatus) {
        if (estatus != null && !estatus.isBlank()) {
            String filtro = estatus.trim();
            if (!ESTATUS_VALIDOS.contains(filtro)) {
                throw new DatosInvalidosException("CON-004", "El estatus de constancia no es válido.");
            }
            return constanciaRepository.findAllByEstatusOrderByFechaSolicitudDescIdDesc(filtro)
                    .stream().map(ConstanciaDTO::desde).toList();
        }
        return constanciaRepository.findAllByOrderByFechaSolicitudDescIdDesc()
                .stream().map(ConstanciaDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<ConstanciaDTO> listarPropias(String nombreUsuario) {
        Alumno alumno = buscarAlumno(nombreUsuario);
        return constanciaRepository.findByAlumnoNumeroControlOrderByFechaSolicitudDescIdDesc(alumno.getNumeroControl())
                .stream().map(ConstanciaDTO::desde).toList();
    }

    @Transactional
    public ConstanciaDTO autorizar(
            Integer id, AutorizarConstanciaRequest solicitud, String nombreCoordinador) {
        Constancia constancia = buscarConstanciaParaActualizar(id);
        if (!"Solicitada".equals(constancia.getEstatus())) {
            throw new ConflictoException("CON-005", "Solo se pueden autorizar solicitudes pendientes.");
        }
        String folio = solicitud.folio().trim();
        if (constanciaRepository.existsByFolioIgnoreCase(folio)) {
            throw new ConflictoException("CON-007", "El folio ya está asignado. Captura uno diferente.");
        }
        Usuario coordinador = buscarUsuario(nombreCoordinador);
        if (coordinador.getRol().comoRolSistema() != RolSistema.COORDINADOR) {
            throw new DatosInvalidosException("CON-008", "Solo coordinación puede emitir constancias.");
        }

        constancia.setFolio(folio);
        constancia.setFechaExpedicion(LocalDate.now());
        constancia.setUsuarioAutoriza(coordinador);
        constancia.setEstatus("Emitida");
        byte[] pdf;
        try {
            pdf = pdfService.generar(ConstanciaDTO.desde(constancia));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la constancia.", e);
        }

        String rutaPdf = archivoService.guardar(constancia.getId(), pdf);
        constancia.setArchivoPdf(rutaPdf);
        try {
            constanciaRepository.saveAndFlush(constancia);
        } catch (DataIntegrityViolationException e) {
            archivoService.eliminar(rutaPdf);
            throw new ConflictoException("CON-007", "El folio ya está asignado. Captura uno diferente.");
        }
        try {
            notificarAJefatura(constancia, coordinador);
        } catch (RuntimeException e) {
            archivoService.eliminar(rutaPdf);
            throw e;
        }
        return ConstanciaDTO.desde(constancia);
    }

    @Transactional
    public ConstanciaDTO rechazar(Integer id, RechazarConstanciaRequest solicitud, String nombreCoordinador) {
        Constancia constancia = buscarConstanciaParaActualizar(id);
        if (!"Solicitada".equals(constancia.getEstatus())) {
            throw new ConflictoException("CON-005", "Solo se pueden rechazar solicitudes pendientes.");
        }
        Usuario coordinador = buscarUsuario(nombreCoordinador);
        if (coordinador.getRol().comoRolSistema() != RolSistema.COORDINADOR) {
            throw new DatosInvalidosException("CON-008", "Solo coordinación puede rechazar constancias.");
        }
        String motivo = solicitud.motivo().trim();
        if (motivo.isEmpty()) {
            throw new DatosInvalidosException("CON-009", "Escribe el motivo del rechazo.");
        }
        constancia.setMotivoRechazo(motivo);
        constancia.setUsuarioAutoriza(coordinador);
        constancia.setEstatus("Rechazada");
        Constancia guardada = constanciaRepository.saveAndFlush(constancia);
        notificar(coordinador, constancia.getAlumno().getUsuario(), guardada,
                "Solicitud de constancia rechazada", "Tu solicitud de constancia fue rechazada: " + motivo);
        return ConstanciaDTO.desde(guardada);
    }

    @Transactional(readOnly = true)
    public Resource descargarPdf(Integer id, String nombreUsuario, boolean esAlumno) {
        Constancia constancia = buscarConstancia(id);
        if (esAlumno) {
            Alumno alumno = buscarAlumno(nombreUsuario);
            if (!alumno.getNumeroControl().equals(constancia.getAlumno().getNumeroControl())) {
                throw new RecursoNoEncontradoException("CON-006", "No se encontró el PDF de la constancia.");
            }
        }
        if (!"Emitida".equals(constancia.getEstatus())) {
            throw new RecursoNoEncontradoException("CON-006", "No se encontró el PDF de la constancia.");
        }
        return archivoService.cargar(constancia.getArchivoPdf());
    }

    private ElegibilidadConstanciaDTO elegibilidad(Alumno alumno) {
        Map<Integer, AvanceNivel> avances = avanceNivelRepository
                .findByIdNumeroControl(alumno.getNumeroControl()).stream()
                .collect(Collectors.toMap(avance -> avance.getId().getIdNivel(), Function.identity()));
        Map<Integer, Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc().stream()
                .collect(Collectors.toMap(Nivel::getNumero, Function.identity()));

        List<String> faltantes = IntStream.rangeClosed(1, 10)
                .filter(numero -> !nivelAcreditado(numero, niveles, avances))
                .mapToObj(numero -> niveles.containsKey(numero)
                        ? numero + ". " + niveles.get(numero).getNombre()
                        : "Nivel " + numero + " (sin configurar)")
                .toList();
        int acreditados = 10 - faltantes.size();
        int horas = IntStream.rangeClosed(1, 10)
                .filter(numero -> nivelAcreditado(numero, niveles, avances))
                .map(numero -> niveles.get(numero).getHoras())
                .sum();
        String mcer = niveles.containsKey(10) ? niveles.get(10).getNivelMcer() : null;
        return new ElegibilidadConstanciaDTO(
                IDIOMA, acreditados, 10, horas, mcer, faltantes.isEmpty(), faltantes);
    }

    private boolean nivelAcreditado(
            int numero, Map<Integer, Nivel> niveles,
            Map<Integer, AvanceNivel> avances) {
        Nivel nivel = niveles.get(numero);
        if (nivel == null) return false;
        var avance = avances.get(nivel.getId());
        return avance != null && avance.getEstatus().cubreNivel();
    }

    private Alumno buscarAlumno(String nombreUsuario) {
        Usuario usuario = buscarUsuario(nombreUsuario);
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CON-010", "No se encontró el expediente del alumno."));
    }

    private Usuario buscarUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CON-011", "No se encontró la cuenta autenticada."));
    }

    private Constancia buscarConstancia(Integer id) {
        return constanciaRepository.findConAlumnoById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CON-012", "No se encontró la solicitud de constancia."));
    }

    private Constancia buscarConstanciaParaActualizar(Integer id) {
        return constanciaRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CON-012", "No se encontró la solicitud de constancia."));
    }

    private void notificarAJefatura(Constancia constancia, Usuario coordinador) {
        Usuario jefa = usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc(
                        RolSistema.JEFA_DGTV.nombreBd())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CON-013", "No hay una cuenta activa para la jefatura del DGTV."));
        notificar(coordinador, jefa, constancia, "Constancia de acreditación emitida",
                "Se emitió la constancia " + constancia.getFolio() + " para "
                        + constancia.getAlumno().nombreCompleto() + " ("
                        + constancia.getAlumno().getNumeroControl() + ").");
    }

    private void notificar(
            Usuario origen, Usuario destino, Constancia constancia, String asunto, String mensaje) {
        Notificacion notificacion = new Notificacion();
        notificacion.setOrigen(origen);
        notificacion.setDestino(destino);
        notificacion.setConstancia(constancia);
        notificacion.setAsunto(asunto);
        notificacion.setMensaje(mensaje);
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setLeida(false);
        notificacionRepository.saveAndFlush(notificacion);
    }
}
