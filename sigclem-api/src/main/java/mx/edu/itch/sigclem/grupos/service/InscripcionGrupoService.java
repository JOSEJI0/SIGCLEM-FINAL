package mx.edu.itch.sigclem.grupos.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.domain.PreferenciaGrupo;
import mx.edu.itch.sigclem.grupos.dto.AlumnoActaDTO;
import mx.edu.itch.sigclem.grupos.dto.DetalleGrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.OfertaDTO;
import mx.edu.itch.sigclem.grupos.repository.AjusteAvanceRepository;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.grupos.repository.PreferenciaGrupoRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;
import mx.edu.itch.sigclem.pagos.domain.TarifaPago;
import mx.edu.itch.sigclem.pagos.repository.AutorizacionPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.ComprobantePagoRepository;
import mx.edu.itch.sigclem.pagos.repository.TarifaPagoRepository;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Inscripción de inicio de semestre:
 * <ol>
 * <li>El alumno consulta los grupos abiertos de su siguiente nivel (no puede saltar niveles) y elige curso y horario.</li>
 * <li>Coordinación asigna a los alumnos a los grupos; al asignarlos se genera automáticamente el cargo del curso
 * (papeleta de pago) con la tarifa vigente del nivel.</li>
 * <li>El alumno paga y registra su comprobante; el acta solo se cierra con el curso liquidado.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class InscripcionGrupoService {

    private static final String CURSO = "Curso de nivel";
    /** Estatus de avance desde los que se puede inscribir un nivel (habilitado o repetir uno reprobado). */
    private static final Set<EstatusAvance> PUEDE_CURSAR = Set.of(EstatusAvance.HABILITADO, EstatusAvance.REPROBADO);
    private static final Set<String> CARGO_ABIERTO = Set.of("Pendiente", "Parcial");

    private final GrupoService grupoService;
    private final AjusteAvanceRepository ajusteAvanceRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final PreferenciaGrupoRepository preferenciaRepository;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final NivelRepository nivelRepository;
    private final TarifaPagoRepository tarifaRepository;
    private final AutorizacionPagoRepository autorizacionRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;

    // ------------------------------------------------------------------ Coordinación

    @Transactional(readOnly = true)
    public DetalleGrupoDTO detalle(Integer idGrupo) {
        Grupo grupo = grupoRepository.findConDetallesById(idGrupo)
                .orElseThrow(() -> new RecursoNoEncontradoException("GRU-012", "No existe el grupo indicado."));
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupoId(idGrupo);
        Map<Integer, AutorizacionPago> cargos = grupoService.cargosDe(inscripciones);
        List<AlumnoActaDTO> inscritos = inscripciones.stream()
                .sorted(Comparator.comparing(i -> i.getAlumno().nombreCompleto()))
                .map(i -> AlumnoActaDTO.desde(i, i.getIdAutorizacion() == null ? null : cargos.get(i.getIdAutorizacion())))
                .toList();
        List<DetalleGrupoDTO.Interesado> interesados = preferenciaRepository
                .findByGrupoIdAndEstatusOrderByFechaRegistroAsc(idGrupo, PreferenciaGrupo.PENDIENTE).stream()
                .map(p -> new DetalleGrupoDTO.Interesado(p.getAlumno().getNumeroControl(), p.getAlumno().nombreCompleto(),
                        p.getAlumno().getCarrera().getNombre(), p.getFechaRegistro()))
                .toList();
        List<DetalleGrupoDTO.Observacion> observaciones = inscripciones.isEmpty() ? List.of()
                : ajusteAvanceRepository.findByIdInscripcionInOrderByFechaAsc(
                        inscripciones.stream().map(Inscripcion::getId).toList()).stream()
                        .map(DetalleGrupoDTO.Observacion::desde).toList();
        return new DetalleGrupoDTO(GrupoDTO.desde(grupo, inscripciones.size(), interesados.size()), inscritos,
                interesados, observaciones);
    }

    /** Asigna al alumno al grupo y genera automáticamente su cargo del curso con la tarifa vigente del nivel. */
    @Transactional
    public DetalleGrupoDTO asignar(Integer idGrupo, String numeroControlTexto, String usuarioCoordinador) {
        String nc = numeroControlTexto.trim().toUpperCase();
        Grupo grupo = grupoRepository.bloquearPorId(idGrupo)
                .orElseThrow(() -> new RecursoNoEncontradoException("GRU-012", "No existe el grupo indicado."));
        grupoService.verificarActaAbierta(grupo);
        if (grupo.getPeriodo().getFechaFin().isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("INS-001", "El periodo del grupo ya concluyó.");
        }
        Alumno alumno = alumnoRepository.bloquearPorNumeroControl(nc)
                .orElseThrow(() -> new RecursoNoEncontradoException("INS-002", "No existe el alumno " + nc + "."));
        if (inscripcionRepository.existsByAlumnoNumeroControlAndGrupoId(nc, idGrupo)) {
            throw new ConflictoException("INS-003", "El alumno ya está asignado a este grupo.");
        }
        inscripcionRepository.findByAlumnoNumeroControl(nc).stream()
                .filter(i -> i.getGrupo().getPeriodo().getId().equals(grupo.getPeriodo().getId())
                        && !"Cerrada".equals(i.getGrupo().getEstatusActa()))
                .findFirst()
                .ifPresent(i -> {
                    throw new ConflictoException("INS-004", "El alumno ya está asignado al grupo "
                            + i.getGrupo().getNivel().getNombre() + " " + i.getGrupo().getClave()
                            + " en este periodo. Quítalo de ese grupo antes de cambiarlo.");
                });
        if (inscripcionRepository.countByGrupoId(idGrupo) >= grupo.getCupo()) {
            throw new ReglaNegocioException("INS-005", "El grupo ya alcanzó su cupo de " + grupo.getCupo() + " alumnos.");
        }
        AvanceNivel avance = verificarNivelPermitido(nc, grupo.getNivel());

        // Todos los niveles pagan lo mismo: se usa el monto general del concepto (o, si existiera, el del nivel).
        TarifaPago tarifa = tarifaRepository.findFirstByConceptoAndNivelIsNullAndActivaTrue(CURSO)
                .or(() -> tarifaRepository.findFirstByConceptoAndNivelIdAndActivaTrue(CURSO, grupo.getNivel().getId()))
                .orElseThrow(() -> new ReglaNegocioException("INS-007", "El concepto «" + CURSO
                        + "» no tiene un monto activo. Actívalo o asígnale un monto en Pagos."));
        Usuario coordinador = usuarioRepository.findByNombreUsuario(usuarioCoordinador)
                .orElseThrow(() -> new RecursoNoEncontradoException("INS-008", "No se encontró la cuenta de coordinación."));

        // Si coordinación ya había emitido un cargo abierto del mismo nivel y periodo, se reutiliza.
        AutorizacionPago cargo = autorizacionRepository
                .findFirstByAlumnoNumeroControlAndConceptoAndNivelIdAndPeriodoIdAndEstatusInOrderByFechaEmisionDescIdDesc(
                        nc, CURSO, grupo.getNivel().getId(), grupo.getPeriodo().getId(), CARGO_ABIERTO)
                .filter(a -> inscripcionRepository.findByIdAutorizacion(a.getId()).isEmpty())
                .orElseGet(() -> {
                    AutorizacionPago nuevo = new AutorizacionPago();
                    nuevo.setAlumno(alumno);
                    nuevo.setConcepto(CURSO);
                    nuevo.setNivel(grupo.getNivel());
                    nuevo.setPeriodo(grupo.getPeriodo());
                    nuevo.setMontoEsperado(tarifa.getMonto());
                    nuevo.setSaldoPendiente(tarifa.getMonto());
                    nuevo.setEstatus("Pendiente");
                    nuevo.setFechaEmision(LocalDate.now());
                    nuevo.setUsuarioEmite(coordinador);
                    return autorizacionRepository.save(nuevo);
                });

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setAlumno(alumno);
        inscripcion.setGrupo(grupo);
        inscripcion.setIdAutorizacion(cargo.getId());
        inscripcionRepository.save(inscripcion);

        avance.setEstatus(EstatusAvance.CURSANDO);
        avance.setOrigen("Curso");
        avance.setFechaRegistro(LocalDate.now());
        avanceRepository.save(avance);

        PreferenciaGrupo preferencia = preferenciaRepository
                .findByAlumnoNumeroControlAndPeriodoId(nc, grupo.getPeriodo().getId())
                .orElseGet(() -> {
                    PreferenciaGrupo nueva = new PreferenciaGrupo();
                    nueva.setAlumno(alumno);
                    nueva.setPeriodo(grupo.getPeriodo());
                    nueva.setFechaRegistro(LocalDateTime.now());
                    return nueva;
                });
        preferencia.setGrupo(grupo);
        preferencia.setEstatus(PreferenciaGrupo.ASIGNADA);
        preferenciaRepository.save(preferencia);

        notificar(alumno.getUsuario(), coordinador, "Asignación a grupo",
                "Quedaste asignado al grupo " + grupo.getNivel().getNombre() + " " + grupo.getClave() + " ("
                        + grupo.getPeriodo().getNombre() + "). Tu papeleta de pago por " + cargo.getMontoEsperado()
                        + " MXN ya está disponible en Pagos.");
        return detalle(idGrupo);
    }

    /** Quita al alumno del grupo: elimina su cargo si aún no tiene comprobantes y regresa el nivel a habilitado. */
    @Transactional
    public DetalleGrupoDTO quitar(Integer idGrupo, String numeroControl) {
        Grupo grupo = grupoService.buscarGrupo(idGrupo);
        grupoService.verificarActaAbierta(grupo);
        Inscripcion inscripcion = inscripcionRepository.findByAlumnoNumeroControlAndGrupoId(numeroControl, idGrupo)
                .orElseThrow(() -> new RecursoNoEncontradoException("INS-009", "El alumno no está en este grupo."));
        Integer idCargo = inscripcion.getIdAutorizacion();
        inscripcionRepository.delete(inscripcion);
        inscripcionRepository.flush();
        if (idCargo != null && !comprobanteRepository.existsByAutorizacionId(idCargo)) {
            autorizacionRepository.deleteById(idCargo);
        }
        avanceRepository.findByIdNumeroControlAndIdIdNivel(numeroControl, grupo.getNivel().getId())
                .filter(a -> a.getEstatus() == EstatusAvance.CURSANDO)
                .ifPresent(a -> {
                    a.setEstatus(EstatusAvance.HABILITADO);
                    a.setFechaRegistro(LocalDate.now());
                    avanceRepository.save(a);
                });
        preferenciaRepository.findByAlumnoNumeroControlAndPeriodoId(numeroControl, grupo.getPeriodo().getId())
                .ifPresent(p -> {
                    p.setEstatus(PreferenciaGrupo.PENDIENTE);
                    preferenciaRepository.save(p);
                });
        return detalle(idGrupo);
    }

    // ------------------------------------------------------------------ Alumno

    /** Grupos abiertos del siguiente nivel del alumno, su elección actual y los grupos a los que ya fue asignado. */
    @Transactional(readOnly = true)
    public OfertaDTO oferta(String numeroControl) {
        List<Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc();
        Map<Integer, AvanceNivel> avance = avanceRepository.findByIdNumeroControl(numeroControl).stream()
                .collect(Collectors.toMap(a -> a.getId().getIdNivel(), Function.identity()));
        Nivel siguiente = niveles.stream()
                .filter(n -> avance.get(n.getId()) == null || !avance.get(n.getId()).getEstatus().cubreNivel())
                .findFirst().orElse(null);

        LocalDate hoy = LocalDate.now();
        List<Inscripcion> vigentes = inscripcionRepository.findByAlumnoNumeroControl(numeroControl).stream()
                .filter(i -> !"Cerrada".equals(i.getGrupo().getEstatusActa())
                        && !i.getGrupo().getPeriodo().getFechaFin().isBefore(hoy))
                .toList();
        Map<Integer, AutorizacionPago> cargos = grupoService.cargosDe(vigentes);
        List<OfertaDTO.Asignacion> asignaciones = vigentes.stream().map(i -> {
            AutorizacionPago c = i.getIdAutorizacion() == null ? null : cargos.get(i.getIdAutorizacion());
            Grupo g = grupoRepository.findConDetallesById(i.getGrupo().getId()).orElseThrow();
            return new OfertaDTO.Asignacion(GrupoDTO.desde(g, inscripcionRepository.countByGrupoId(g.getId())),
                    c == null ? null : c.getId(), c == null ? null : c.getMontoEsperado(),
                    c == null ? null : c.getSaldoPendiente(),
                    c == null ? (i.getIdComprobante() != null ? "Liquidada" : "Sin cargo") : c.getEstatus());
        }).toList();

        if (siguiente == null) {
            return new OfertaDTO(null, null, "Ya cubriste los diez niveles de inglés.", List.of(), asignaciones);
        }
        AvanceNivel avanceSiguiente = avance.get(siguiente.getId());
        String mensaje = null;
        if (avanceSiguiente != null && avanceSiguiente.getEstatus() == EstatusAvance.CURSANDO) {
            mensaje = "Actualmente cursas " + siguiente.getNombre()
                    + ". Podrás elegir el siguiente nivel cuando se cierre el acta del grupo.";
        }

        List<Grupo> grupos = grupoRepository.findByNivelIdAndPeriodoFechaFinGreaterThanEqual(siguiente.getId(), hoy)
                .stream().filter(g -> !"Cerrada".equals(g.getEstatusActa()))
                .sorted(Comparator.comparing((Grupo g) -> g.getPeriodo().getFechaInicio()).thenComparing(Grupo::getClave))
                .toList();
        Map<Integer, PreferenciaGrupo> preferenciaPorGrupo = grupos.isEmpty() ? Map.of()
                : preferenciaRepository.findByAlumnoNumeroControlAndPeriodoIdIn(numeroControl,
                                grupos.stream().map(g -> g.getPeriodo().getId()).distinct().toList())
                        .stream().collect(Collectors.toMap(p -> p.getGrupo().getId(), Function.identity(), (a, b) -> a));
        List<OfertaDTO.GrupoOfertado> ofertados = grupos.stream().map(g -> {
            long inscritos = inscripcionRepository.countByGrupoId(g.getId());
            PreferenciaGrupo p = preferenciaPorGrupo.get(g.getId());
            return new OfertaDTO.GrupoOfertado(GrupoDTO.desde(g, inscritos), p != null,
                    p == null ? null : p.getEstatus(), inscritos >= g.getCupo());
        }).toList();
        return new OfertaDTO(siguiente.getNumero(), siguiente.getNombre(), mensaje, ofertados, asignaciones);
    }

    /** El alumno elige (o cambia) el grupo de su preferencia para el periodo; solo de su siguiente nivel. */
    @Transactional
    public OfertaDTO elegir(Integer idGrupo, String numeroControl) {
        Grupo grupo = grupoRepository.findConDetallesById(idGrupo)
                .orElseThrow(() -> new RecursoNoEncontradoException("GRU-012", "No existe el grupo indicado."));
        grupoService.verificarActaAbierta(grupo);
        if (grupo.getPeriodo().getFechaFin().isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("INS-001", "El periodo del grupo ya concluyó.");
        }
        verificarNivelPermitido(numeroControl, grupo.getNivel());
        Alumno alumno = alumnoRepository.findById(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("INS-002", "No existe el alumno."));
        PreferenciaGrupo preferencia = preferenciaRepository
                .findByAlumnoNumeroControlAndPeriodoId(numeroControl, grupo.getPeriodo().getId())
                .orElseGet(PreferenciaGrupo::new);
        if (PreferenciaGrupo.ASIGNADA.equals(preferencia.getEstatus()) && preferencia.getId() != null) {
            throw new ConflictoException("INS-010",
                    "Coordinación ya te asignó a un grupo de este periodo; solicita el cambio en la coordinación.");
        }
        preferencia.setAlumno(alumno);
        preferencia.setGrupo(grupo);
        preferencia.setPeriodo(grupo.getPeriodo());
        preferencia.setFechaRegistro(LocalDateTime.now());
        preferencia.setEstatus(PreferenciaGrupo.PENDIENTE);
        preferenciaRepository.save(preferencia);
        return oferta(numeroControl);
    }

    /** El alumno retira su elección de un grupo mientras coordinación no lo haya asignado. */
    @Transactional
    public OfertaDTO cancelarEleccion(Integer idGrupo, String numeroControl) {
        Grupo grupo = grupoService.buscarGrupo(idGrupo);
        preferenciaRepository.findByAlumnoNumeroControlAndPeriodoId(numeroControl, grupo.getPeriodo().getId())
                .filter(p -> p.getGrupo().getId().equals(idGrupo))
                .ifPresent(p -> {
                    if (PreferenciaGrupo.ASIGNADA.equals(p.getEstatus())) {
                        throw new ConflictoException("INS-010",
                                "Coordinación ya te asignó a este grupo; solicita el cambio en la coordinación.");
                    }
                    preferenciaRepository.delete(p);
                });
        return oferta(numeroControl);
    }

    /** RF-04: solo se puede cursar el siguiente nivel habilitado (o repetir uno reprobado). */
    private AvanceNivel verificarNivelPermitido(String nc, Nivel nivel) {
        AvanceNivel avance = avanceRepository.findByIdNumeroControlAndIdIdNivel(nc, nivel.getId())
                .orElseThrow(() -> new ReglaNegocioException("INS-006", "El expediente no contiene ese nivel."));
        if (!PUEDE_CURSAR.contains(avance.getEstatus())) {
            String motivo = switch (avance.getEstatus()) {
                case ACREDITADO, EXENTO -> "ya cubrió " + nivel.getNombre() + ".";
                case CURSANDO -> "ya está cursando " + nivel.getNombre() + ".";
                default -> "primero debe acreditar el nivel anterior a " + nivel.getNombre()
                        + " (no se permiten saltos de nivel).";
            };
            throw new ReglaNegocioException("INS-006", "No se puede inscribir: " + motivo);
        }
        return avance;
    }

    private void notificar(Usuario destino, Usuario origen, String asunto, String mensaje) {
        Notificacion n = new Notificacion();
        n.setDestino(destino);
        n.setOrigen(origen);
        n.setAsunto(asunto);
        n.setMensaje(mensaje);
        n.setFechaEnvio(LocalDateTime.now());
        n.setLeida(false);
        notificacionRepository.save(n);
    }
}
