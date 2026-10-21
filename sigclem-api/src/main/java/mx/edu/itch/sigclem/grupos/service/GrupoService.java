package mx.edu.itch.sigclem.grupos.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivelId;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Aula;
import mx.edu.itch.sigclem.catalogos.domain.Modalidad;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.repository.AulaRepository;
import mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.grupos.domain.AjusteAvance;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Horario;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.domain.PreferenciaGrupo;
import mx.edu.itch.sigclem.grupos.dto.ActaDTO;
import mx.edu.itch.sigclem.grupos.dto.CalificacionesRequest;
import mx.edu.itch.sigclem.grupos.dto.CrearGrupoRequest;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.HorarioRequest;
import mx.edu.itch.sigclem.grupos.repository.AjusteAvanceRepository;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.HorarioRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.grupos.repository.PreferenciaGrupoRepository;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;
import mx.edu.itch.sigclem.pagos.repository.AutorizacionPagoRepository;

@Service
@RequiredArgsConstructor
public class GrupoService {

    /** Identificador que no corresponde a ningún grupo: al abrir uno nuevo no hay horarios propios que excluir. */
    private static final int SIN_GRUPO = -1;

    private final GrupoRepository grupoRepository;
    private final HorarioRepository horarioRepository;
    private final InscripcionRepository inscripcionRepository;
    private final PeriodoRepository periodoRepository;
    private final NivelRepository nivelRepository;
    private final ModalidadRepository modalidadRepository;
    private final AulaRepository aulaRepository;
    private final FacilitadorRepository facilitadorRepository;
    private final AvanceNivelRepository avanceNivelRepository;
    private final PreferenciaGrupoRepository preferenciaRepository;
    private final AutorizacionPagoRepository autorizacionRepository;
    private final AjusteAvanceRepository ajusteAvanceRepository;

    @Transactional
    public GrupoDTO abrir(CrearGrupoRequest req) {
        Grupo grupo = new Grupo();
        aplicarDatos(grupo, req, SIN_GRUPO);
        return GrupoDTO.desde(grupoRepository.save(grupo), 0);
    }

    /**
     * Edita los datos y horarios de un grupo. Con alumnos inscritos no cambian el nivel ni el periodo,
     * y el cupo no puede quedar por debajo de los inscritos.
     */
    @Transactional
    public GrupoDTO editar(Integer id, CrearGrupoRequest req) {
        Grupo grupo = buscarGrupo(id);
        verificarActaAbierta(grupo);
        long inscritos = inscripcionRepository.countByGrupoId(id);
        if (inscritos > 0 && (!grupo.getNivel().getId().equals(req.idNivel())
                || !grupo.getPeriodo().getId().equals(req.idPeriodo()))) {
            throw new ReglaNegocioException("GRU-014",
                    "El grupo ya tiene alumnos inscritos: no se puede cambiar su nivel ni su periodo.");
        }
        if (req.cupo() < inscritos) {
            throw new ReglaNegocioException("GRU-015",
                    "El cupo no puede ser menor que los " + inscritos + " alumnos inscritos.");
        }
        grupo.getHorarios().clear();
        grupoRepository.flush();
        aplicarDatos(grupo, req, id);
        return GrupoDTO.desde(grupoRepository.save(grupo), inscritos,
                preferenciaRepository.countByGrupoIdAndEstatus(id, PreferenciaGrupo.PENDIENTE));
    }

    /** Elimina un grupo sin alumnos inscritos (sus horarios y las elecciones de la encuesta se borran con él). */
    @Transactional
    public void eliminar(Integer id) {
        Grupo grupo = buscarGrupo(id);
        if (inscripcionRepository.countByGrupoId(id) > 0) {
            throw new ConflictoException("GRU-016",
                    "El grupo tiene alumnos inscritos. Quítalos primero para poder eliminarlo.");
        }
        grupoRepository.delete(grupo);
    }

    private void aplicarDatos(Grupo grupo, CrearGrupoRequest req, Integer idExcluido) {
        Periodo periodo = periodoRepository.findById(req.idPeriodo())
                .orElseThrow(() -> new DatosInvalidosException("GRU-001", "El periodo seleccionado no existe."));
        if (periodo.getFechaFin().isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("GRU-002", "No se pueden abrir ni editar grupos de un periodo concluido.");
        }
        Nivel nivel = nivelRepository.findById(req.idNivel())
                .orElseThrow(() -> new DatosInvalidosException("GRU-003", "El nivel seleccionado no existe."));
        Modalidad modalidad = modalidadRepository.findById(req.idModalidad())
                .orElseThrow(() -> new DatosInvalidosException("GRU-004", "La modalidad seleccionada no existe."));
        Facilitador facilitador = facilitadorRepository.findById(req.idFacilitador())
                .orElseThrow(() -> new DatosInvalidosException("GRU-005", "El facilitador seleccionado no existe."));
        if (!facilitador.getUsuario().isActivo()) {
            throw new ReglaNegocioException("GRU-006", "No se pueden asignar grupos a un facilitador inactivo.");
        }
        Aula aula = aulaRepository.findById(req.idAula())
                .orElseThrow(() -> new DatosInvalidosException("GRU-007", "El aula seleccionada no existe."));
        String clave = req.clave().trim().toUpperCase();
        boolean claveRepetida = idExcluido == SIN_GRUPO
                ? grupoRepository.existsByPeriodoIdAndNivelIdAndModalidadIdAndClave(
                        periodo.getId(), nivel.getId(), modalidad.getId(), clave)
                : grupoRepository.existsByPeriodoIdAndNivelIdAndModalidadIdAndClaveAndIdNot(
                        periodo.getId(), nivel.getId(), modalidad.getId(), clave, idExcluido);
        if (claveRepetida) {
            throw new ConflictoException("GRU-008", "Ya existe esa clave de grupo para el nivel, modalidad y periodo.");
        }
        validarHorarios(req.horarios(), facilitador, aula, periodo.getId(), idExcluido);

        grupo.setClave(clave);
        grupo.setNivel(nivel);
        grupo.setPeriodo(periodo);
        grupo.setModalidad(modalidad);
        grupo.setFacilitador(facilitador);
        grupo.setAula(aula);
        grupo.setCupo(req.cupo());
        req.horarios().forEach(h -> {
            Horario horario = new Horario();
            horario.setDiaSemana(h.diaSemana());
            horario.setHoraInicio(h.horaInicio());
            horario.setHoraFin(h.horaFin());
            grupo.agregarHorario(horario);
        });
    }

    @Transactional(readOnly = true)
    public List<GrupoDTO> listar(Integer periodoId, Authentication auth) {
        Integer idPeriodo = periodoId == null
                ? periodoRepository.findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioDesc(
                        LocalDate.now(), LocalDate.now()).map(Periodo::getId).orElse(null)
                : periodoId;
        if (idPeriodo == null) {
            return List.of();
        }
        List<Grupo> grupos;
        if (esFacilitador(auth)) {
            Facilitador facilitador = buscarFacilitador(auth.getName());
            grupos = grupoRepository.findByPeriodoIdAndFacilitadorId(idPeriodo, facilitador.getId());
        } else {
            grupos = grupoRepository.findByPeriodoId(idPeriodo);
        }
        return grupos.stream()
                .sorted(Comparator.comparing((Grupo g) -> g.getNivel().getNumero()).thenComparing(Grupo::getClave))
                .map(g -> GrupoDTO.desde(g, inscripcionRepository.countByGrupoId(g.getId()),
                        preferenciaRepository.countByGrupoIdAndEstatus(g.getId(), PreferenciaGrupo.PENDIENTE)))
                .toList();
    }

    /** Grupos de un facilitador (de todos los periodos), del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<GrupoDTO> listarDeFacilitador(Integer idFacilitador) {
        return grupoRepository.findByFacilitadorId(idFacilitador).stream()
                .sorted(Comparator.comparing((Grupo g) -> g.getPeriodo().getFechaInicio()).reversed()
                        .thenComparing(g -> g.getNivel().getNumero()).thenComparing(Grupo::getClave))
                .map(g -> GrupoDTO.desde(g, inscripcionRepository.countByGrupoId(g.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActaDTO obtenerActa(Integer id, Authentication auth) {
        Grupo grupo = buscarGrupo(id);
        verificarPropietario(grupo, auth);
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupoId(grupo.getId());
        return ActaDTO.desde(grupo, inscripciones, cargosDe(inscripciones));
    }

    @Transactional
    public ActaDTO guardarCalificaciones(Integer id, CalificacionesRequest req, Authentication auth) {
        Grupo grupo = buscarGrupo(id);
        verificarPropietario(grupo, auth);
        verificarPeriodoEditable(grupo);
        verificarActaAbierta(grupo);
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupoId(grupo.getId());
        Map<String, Inscripcion> porAlumno = new HashMap<>();
        inscripciones.forEach(i -> porAlumno.put(i.getAlumno().getNumeroControl(), i));
        if (req.calificaciones().size() != porAlumno.size()) {
            throw new DatosInvalidosException("ACT-001", "Debes enviar la calificación de cada alumno inscrito.");
        }
        for (var calificacion : req.calificaciones()) {
            Inscripcion inscripcion = porAlumno.remove(calificacion.numeroControl());
            if (inscripcion == null) {
                throw new DatosInvalidosException("ACT-002",
                        "La matrícula " + calificacion.numeroControl() + " no pertenece a este grupo.");
            }
            inscripcion.setCalificacionFinal(calificacion.calificacion());
        }
        if (!porAlumno.isEmpty()) {
            throw new DatosInvalidosException("ACT-001", "Debes enviar la calificación de cada alumno inscrito.");
        }
        inscripcionRepository.saveAll(inscripciones);
        grupo.setEstatusActa("En captura");
        grupoRepository.save(grupo);
        return ActaDTO.desde(grupo, inscripciones, cargosDe(inscripciones));
    }

    /** Cierre por el facilitador: exige que todos los alumnos hayan liquidado su curso. */
    @Transactional
    public ActaDTO cerrarActa(Integer id, Authentication auth) {
        Grupo grupo = buscarGrupo(id);
        verificarPropietario(grupo, auth);
        verificarPeriodoEditable(grupo);
        List<Inscripcion> inscripciones = inscripcionesParaCierre(grupo);
        // Regla de negocio: ningún avance académico se registra sin el cargo del curso liquidado.
        Map<Integer, AutorizacionPago> cargos = cargosDe(inscripciones);
        List<String> sinPago = inscripciones.stream()
                .filter(i -> !cursoLiquidado(i, cargos))
                .map(i -> i.getAlumno().nombreCompleto() + " (" + i.getAlumno().getNumeroControl() + ")")
                .toList();
        if (!sinPago.isEmpty()) {
            throw new ReglaNegocioException("ACT-008",
                    "No se puede cerrar el acta: falta liquidar el curso de " + String.join(", ", sinPago)
                            + ". Coordinación puede cerrarla con una observación.");
        }
        Map<Integer, Integer> siguientes = siguienteNivelPorNivel();
        for (Inscripcion inscripcion : inscripciones) {
            actualizarAvance(inscripcion, siguientes);
        }
        marcarCerrada(grupo, "Facilitador", null);
        return ActaDTO.desde(grupo, inscripciones, cargos);
    }

    /**
     * Cierre por coordinación con observación obligatoria, aunque haya alumnos sin liquidar su curso: a quienes ya
     * pagaron se les aplica el nivel; los demás conservan su calificación con el nivel pendiente, que coordinación
     * asigna después de forma individual ({@link #asignarNivelPendiente}). No depende de las fechas del periodo.
     */
    @Transactional
    public ActaDTO cerrarActaPorCoordinacion(Integer id, String observacion, Authentication auth) {
        String texto = observacionValida(observacion);
        Grupo grupo = buscarGrupo(id);
        List<Inscripcion> inscripciones = inscripcionesParaCierre(grupo);
        Map<Integer, AutorizacionPago> cargos = cargosDe(inscripciones);
        Map<Integer, Integer> siguientes = siguienteNivelPorNivel();
        List<AjusteAvance> ajustes = new ArrayList<>();
        for (Inscripcion inscripcion : inscripciones) {
            if (cursoLiquidado(inscripcion, cargos)) {
                actualizarAvance(inscripcion, siguientes);
            } else {
                inscripcion.setAvancePendiente(true);
                ajustes.add(new AjusteAvance(inscripcion, AjusteAvance.CIERRE_SIN_PAGO, null,
                        estatusPago(inscripcion, cargos), texto, auth.getName()));
            }
        }
        inscripcionRepository.saveAll(inscripciones);
        ajusteAvanceRepository.saveAll(ajustes);
        marcarCerrada(grupo, "Coordinación", texto);
        return ActaDTO.desde(grupo, inscripciones, cargos);
    }

    /**
     * Asigna el nivel de un alumno cuyo curso no estaba liquidado al cerrar el acta (por ejemplo, cuando presenta el
     * pago tiempo después): se aplica el resultado de su calificación y queda registrada la observación.
     */
    @Transactional
    public ActaDTO asignarNivelPendiente(Integer id, String numeroControl, String observacion, Authentication auth) {
        String texto = observacionValida(observacion);
        Grupo grupo = buscarGrupo(id);
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupoId(grupo.getId());
        Inscripcion inscripcion = inscripciones.stream()
                .filter(i -> i.getAlumno().getNumeroControl().equals(numeroControl))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("ACT-002",
                        "La matrícula " + numeroControl + " no pertenece a este grupo."));
        if (!inscripcion.isAvancePendiente()) {
            throw new ReglaNegocioException("ACT-010", "El nivel de este alumno ya está asignado.");
        }
        Map<Integer, AutorizacionPago> cargos = cargosDe(inscripciones);
        actualizarAvance(inscripcion, siguienteNivelPorNivel());
        inscripcion.setAvancePendiente(false);
        inscripcionRepository.save(inscripcion);
        String estatusNivel = inscripcion.getCalificacionFinal() >= 70 ? EstatusAvance.ACREDITADO.etiqueta()
                : EstatusAvance.REPROBADO.etiqueta();
        ajusteAvanceRepository.save(new AjusteAvance(inscripcion, AjusteAvance.NIVEL_ASIGNADO, estatusNivel,
                estatusPago(inscripcion, cargos), texto, auth.getName()));
        return ActaDTO.desde(grupo, inscripciones, cargos);
    }

    private List<Inscripcion> inscripcionesParaCierre(Grupo grupo) {
        verificarActaAbierta(grupo);
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupoId(grupo.getId());
        if (inscripciones.isEmpty()) {
            throw new ReglaNegocioException("ACT-003", "No se puede cerrar un acta sin alumnos inscritos.");
        }
        if (inscripciones.stream().anyMatch(i -> i.getCalificacionFinal() == null)) {
            throw new ReglaNegocioException("ACT-004", "Captura todas las calificaciones antes de cerrar el acta.");
        }
        return inscripciones;
    }

    private void marcarCerrada(Grupo grupo, String cerradaPor, String observacion) {
        grupo.setEstatusActa("Cerrada");
        grupo.setFechaCierreActa(LocalDate.now());
        grupo.setCerradaPor(cerradaPor);
        grupo.setObservacionCierre(observacion);
        grupoRepository.save(grupo);
    }

    private static String observacionValida(String observacion) {
        String texto = observacion == null ? "" : observacion.trim();
        if (texto.length() < 10 || texto.length() > 500) {
            throw new DatosInvalidosException("ACT-009",
                    "Escribe una observación de 10 a 500 caracteres que explique la situación.");
        }
        return texto;
    }

    private static boolean cursoLiquidado(Inscripcion inscripcion, Map<Integer, AutorizacionPago> cargos) {
        return "Liquidada".equals(estatusPago(inscripcion, cargos));
    }

    /** Las inscripciones anteriores a los cargos (hechas al validar el pago) cuentan como liquidadas. */
    private static String estatusPago(Inscripcion inscripcion, Map<Integer, AutorizacionPago> cargos) {
        if (inscripcion.getIdAutorizacion() == null || inscripcion.getIdComprobante() != null) {
            return "Liquidada";
        }
        AutorizacionPago cargo = cargos.get(inscripcion.getIdAutorizacion());
        return cargo == null ? "Pendiente" : cargo.getEstatus();
    }

    private Map<Integer, Integer> siguienteNivelPorNivel() {
        List<Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc();
        Map<Integer, Integer> siguientes = new HashMap<>();
        for (int index = 0; index + 1 < niveles.size(); index++) {
            siguientes.put(niveles.get(index).getId(), niveles.get(index + 1).getId());
        }
        return siguientes;
    }

    Map<Integer, AutorizacionPago> cargosDe(List<Inscripcion> inscripciones) {
        List<Integer> ids = inscripciones.stream().map(Inscripcion::getIdAutorizacion).filter(Objects::nonNull).toList();
        return ids.isEmpty() ? Map.of()
                : autorizacionRepository.findByIdIn(ids).stream()
                        .collect(Collectors.toMap(AutorizacionPago::getId, Function.identity()));
    }

    /** Valida rango y traslapes. Ya no se limita al horario institucional de 10:00 a 17:00. */
    private void validarHorarios(
            List<HorarioRequest> horarios, Facilitador facilitador, Aula aula, Integer periodoId, Integer idExcluido) {
        List<HorarioRequest> revisados = new ArrayList<>();
        for (HorarioRequest horario : horarios) {
            LocalTime inicio = horario.horaInicio();
            LocalTime fin = horario.horaFin();
            if (!inicio.isBefore(fin)) {
                throw new DatosInvalidosException("GRU-009", "Cada sesión debe terminar después de su hora de inicio.");
            }
            boolean traslapeEnSolicitud = revisados.stream().anyMatch(otro ->
                    otro.diaSemana().equals(horario.diaSemana())
                            && inicio.isBefore(otro.horaFin()) && fin.isAfter(otro.horaInicio()));
            if (traslapeEnSolicitud) {
                throw new ReglaNegocioException("GRU-010", "Las sesiones del grupo se traslapan entre sí.");
            }
            var conflicto = horarioRepository.buscarTraslape(horario.diaSemana(), inicio, fin,
                    periodoId, facilitador.getId(), aula.getId(), idExcluido);
            if (!conflicto.isEmpty()) {
                throw new ReglaNegocioException("GRU-011",
                        "El horario se traslapa con el grupo " + conflicto.get(0).getGrupo().getClave()
                                + " del facilitador o del aula seleccionada.");
            }
            revisados.add(horario);
        }
    }

    private void actualizarAvance(Inscripcion inscripcion, Map<Integer, Integer> siguienteNivelPorNivel) {
        Integer idNivel = inscripcion.getGrupo().getNivel().getId();
        String numeroControl = inscripcion.getAlumno().getNumeroControl();
        AvanceNivel avance = avanceNivelRepository.findByIdNumeroControlAndIdIdNivel(numeroControl, idNivel)
                .orElseThrow(() -> new ReglaNegocioException("ACT-005",
                        "El expediente de " + numeroControl + " no tiene el nivel inscrito en su avance."));
        if (inscripcion.getCalificacionFinal() >= 70) {
            avance.setEstatus(EstatusAvance.ACREDITADO);
            avance.setOrigen("Curso");
            Integer siguienteId = siguienteNivelPorNivel.get(idNivel);
            if (siguienteId != null) {
                AvanceNivel siguiente = avanceNivelRepository.findById(new AvanceNivelId(numeroControl, siguienteId))
                        .orElse(null);
                if (siguiente != null && siguiente.getEstatus() == EstatusAvance.NO_PERMITIDO) {
                    siguiente.setEstatus(EstatusAvance.HABILITADO);
                }
            }
        } else {
            avance.setEstatus(EstatusAvance.REPROBADO);
            avance.setOrigen("Curso");
        }
    }

    Grupo buscarGrupo(Integer id) {
        return grupoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("GRU-012",
                        "No existe el grupo con identificador " + id + "."));
    }

    void verificarActaAbierta(Grupo grupo) {
        if ("Cerrada".equals(grupo.getEstatusActa())) {
            throw new ReglaNegocioException("ACT-006", "El acta ya está cerrada y no puede modificarse.");
        }
    }

    private void verificarPeriodoEditable(Grupo grupo) {
        LocalDate hoy = LocalDate.now();
        Periodo periodo = grupo.getPeriodo();
        if (hoy.isBefore(periodo.getFechaInicio()) || hoy.isAfter(periodo.getFechaFin())) {
            throw new ReglaNegocioException("ACT-007",
                    "Los grupos de periodos anteriores o futuros solo pueden consultarse.");
        }
    }

    private void verificarPropietario(Grupo grupo, Authentication auth) {
        if (esFacilitador(auth)) {
            Facilitador facilitador = buscarFacilitador(auth.getName());
            if (!grupo.getFacilitador().getId().equals(facilitador.getId())) {
                throw new ReglaNegocioException("GRU-013", "Solo puedes consultar o modificar tus grupos asignados.");
            }
        }
    }

    private Facilitador buscarFacilitador(String correo) {
        return facilitadorRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException("FAC-002",
                        "No existe un perfil de facilitador para la cuenta autenticada."));
    }

    private boolean esFacilitador(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_FACILITADOR".equals(a.getAuthority()));
    }
}
