package mx.edu.itch.sigclem.notificaciones.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Contacto;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Destinatarios;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Difusion;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Enviado;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.EnvioMensaje;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Recibido;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.ResultadoEnvio;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Mensajería interna (CU-25 ampliado):
 * <ul>
 * <li>Alumno → coordinación y facilitadores de sus grupos.</li>
 * <li>Facilitador → coordinación y alumnos de sus grupos (también a todo un grupo).</li>
 * <li>Coordinación → cualquier usuario y difusiones (todos, todos los alumnos, todos los facilitadores o un grupo).</li>
 * <li>Jefa del DGTV: solo consulta (recibe y marca como leído).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MensajeriaService {

    private static final String TODOS = "TODOS";
    private static final String ALUMNOS = "ALUMNOS";
    private static final String FACILITADORES = "FACILITADORES";
    private static final String GRUPO = "GRUPO:";

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final FacilitadorRepository facilitadorRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;

    @Transactional(readOnly = true)
    public Destinatarios destinatarios(String nombreUsuario) {
        Usuario yo = buscarUsuario(nombreUsuario);
        Map<Integer, Contacto> contactos = contactosPermitidos(yo);
        List<Difusion> difusiones = difusionesPermitidas(yo).entrySet().stream()
                .map(e -> new Difusion(e.getKey(), e.getValue().etiqueta(), e.getValue().miembros().size()))
                .filter(d -> d.destinatarios() > 0)
                .toList();
        List<Contacto> ordenados = contactos.values().stream()
                .sorted(Comparator.comparing(Contacto::rol).thenComparing(Contacto::nombre))
                .toList();
        return new Destinatarios(rol(yo) != RolSistema.JEFA_DGTV, ordenados, difusiones);
    }

    @Transactional
    public ResultadoEnvio enviar(EnvioMensaje solicitud, String nombreUsuario) {
        Usuario yo = buscarUsuario(nombreUsuario);
        if (rol(yo) == RolSistema.JEFA_DGTV) {
            throw new ReglaNegocioException("MSG-001", "La jefatura del DGTV tiene acceso de solo consulta.");
        }
        Map<Integer, Contacto> permitidos = contactosPermitidos(yo);
        Set<Integer> destinos = new LinkedHashSet<>();
        if (solicitud.idsDestinatarios() != null) {
            for (Integer id : solicitud.idsDestinatarios()) {
                if (!permitidos.containsKey(id)) {
                    throw new ReglaNegocioException("MSG-002", "No puedes enviar mensajes a uno de los destinatarios elegidos.");
                }
                destinos.add(id);
            }
        }
        if (solicitud.difusion() != null && !solicitud.difusion().isBlank()) {
            var difusion = difusionesPermitidas(yo).get(solicitud.difusion());
            if (difusion == null) {
                throw new ReglaNegocioException("MSG-003", "No puedes usar esa lista de difusión.");
            }
            difusion.miembros().forEach(u -> destinos.add(u.getId()));
        }
        destinos.remove(yo.getId());
        if (destinos.isEmpty()) {
            throw new DatosInvalidosException("MSG-004", "Elige al menos un destinatario.");
        }
        String lote = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();
        List<Notificacion> mensajes = usuarioRepository.findAllById(destinos).stream().map(destino -> {
            Notificacion n = new Notificacion();
            n.setOrigen(yo);
            n.setDestino(destino);
            n.setAsunto(solicitud.asunto().trim());
            n.setMensaje(solicitud.mensaje().trim());
            n.setFechaEnvio(ahora);
            n.setLeida(false);
            n.setLote(lote);
            return n;
        }).toList();
        notificacionRepository.saveAll(mensajes);
        return new ResultadoEnvio(lote, mensajes.size());
    }

    @Transactional(readOnly = true)
    public List<Recibido> recibidos(String nombreUsuario) {
        Usuario yo = buscarUsuario(nombreUsuario);
        Map<Integer, String> nombres = nombresVisibles();
        return notificacionRepository.findTop200ByDestinoIdOrderByFechaEnvioDescIdDesc(yo.getId()).stream()
                .map(n -> new Recibido(n.getId(), n.getAsunto(), n.getMensaje(),
                        n.getOrigen() == null ? null : n.getOrigen().getId(),
                        n.getOrigen() == null ? "Aviso del sistema" : nombre(n.getOrigen(), nombres),
                        n.getOrigen() == null ? "Sistema" : etiquetaRol(rol(n.getOrigen())),
                        n.getFechaEnvio(), n.isLeida()))
                .toList();
    }

    /** Mensajes enviados; los de una misma difusión se agrupan en una sola fila. */
    @Transactional(readOnly = true)
    public List<Enviado> enviados(String nombreUsuario) {
        Usuario yo = buscarUsuario(nombreUsuario);
        Map<Integer, String> nombres = nombresVisibles();
        Map<String, List<Notificacion>> porEnvio = new LinkedHashMap<>();
        notificacionRepository.findTop500ByOrigenIdOrderByFechaEnvioDescIdDesc(yo.getId())
                .forEach(n -> porEnvio.computeIfAbsent(n.getLote() != null ? n.getLote() : "n" + n.getId(),
                        k -> new ArrayList<>()).add(n));
        return porEnvio.entrySet().stream().map(e -> {
            List<Notificacion> grupo = e.getValue();
            Notificacion primero = grupo.get(0);
            List<String> para = grupo.stream().map(n -> nombre(n.getDestino(), nombres)).toList();
            String resumen = para.size() <= 3 ? String.join(", ", para)
                    : String.join(", ", para.subList(0, 3)) + " y " + (para.size() - 3) + " más";
            return new Enviado(e.getKey(), primero.getAsunto(), primero.getMensaje(), primero.getFechaEnvio(),
                    grupo.size(), (int) grupo.stream().filter(Notificacion::isLeida).count(), resumen);
        }).toList();
    }

    @Transactional
    public void marcarLeido(Integer id, String nombreUsuario) {
        Usuario yo = buscarUsuario(nombreUsuario);
        Notificacion n = notificacionRepository.findByIdAndDestinoId(id, yo.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("MSG-005", "No se encontró el mensaje en tu bandeja."));
        n.setLeida(true);
    }

    @Transactional(readOnly = true)
    public long noLeidos(String nombreUsuario) {
        return notificacionRepository.countByDestinoIdAndLeidaFalse(buscarUsuario(nombreUsuario).getId());
    }

    // ------------------------------------------------------------------ reglas de quién escribe a quién

    private Map<Integer, Contacto> contactosPermitidos(Usuario yo) {
        Map<Integer, String> nombres = nombresVisibles();
        Map<Integer, Contacto> contactos = new LinkedHashMap<>();
        switch (rol(yo)) {
            case ALUMNO -> {
                coordinadores().forEach(u -> agregar(contactos, u, nombres, "Coordinación de lenguas"));
                alumnoRepository.findByUsuarioId(yo.getId()).ifPresent(alumno ->
                        inscripcionRepository.findByAlumnoNumeroControl(alumno.getNumeroControl()).forEach(i -> {
                            Grupo g = i.getGrupo();
                            Usuario facilitador = g.getFacilitador().getUsuario();
                            if (facilitador.isActivo()) {
                                agregar(contactos, facilitador, nombres, "Facilitador de " + g.getNivel().getNombre() + " " + g.getClave());
                            }
                        }));
            }
            case FACILITADOR -> {
                coordinadores().forEach(u -> agregar(contactos, u, nombres, "Coordinación de lenguas"));
                gruposDelFacilitador(yo).forEach(g -> inscripcionRepository.findByGrupoId(g.getId()).forEach(i ->
                        agregar(contactos, i.getAlumno().getUsuario(), nombres,
                                "Alumno de " + g.getNivel().getNombre() + " " + g.getClave())));
            }
            case COORDINADOR -> usuarioRepository.findAll().stream()
                    .filter(u -> u.isActivo() && !u.getId().equals(yo.getId()))
                    .forEach(u -> agregar(contactos, u, nombres, null));
            default -> { }
        }
        contactos.remove(yo.getId());
        return contactos;
    }

    private record ListaDifusion(String etiqueta, List<Usuario> miembros) {
    }

    private Map<String, ListaDifusion> difusionesPermitidas(Usuario yo) {
        Map<String, ListaDifusion> listas = new LinkedHashMap<>();
        switch (rol(yo)) {
            case COORDINADOR -> {
                List<Usuario> activos = usuarioRepository.findAll().stream()
                        .filter(u -> u.isActivo() && !u.getId().equals(yo.getId())).toList();
                listas.put(TODOS, new ListaDifusion("Todos los usuarios", activos));
                listas.put(ALUMNOS, new ListaDifusion("Todos los alumnos",
                        activos.stream().filter(u -> rol(u) == RolSistema.ALUMNO).toList()));
                listas.put(FACILITADORES, new ListaDifusion("Todos los facilitadores",
                        activos.stream().filter(u -> rol(u) == RolSistema.FACILITADOR).toList()));
                grupoRepository.findAll().stream()
                        .filter(g -> !g.getPeriodo().getFechaFin().isBefore(LocalDate.now()))
                        .sorted(Comparator.comparing((Grupo g) -> g.getNivel().getNumero()).thenComparing(Grupo::getClave))
                        .forEach(g -> listas.put(GRUPO + g.getId(), listaDeGrupo(g)));
            }
            case FACILITADOR -> gruposDelFacilitador(yo).forEach(g -> listas.put(GRUPO + g.getId(), listaDeGrupo(g)));
            default -> { }
        }
        return listas;
    }

    private ListaDifusion listaDeGrupo(Grupo g) {
        return new ListaDifusion("Alumnos de " + g.getNivel().getNombre() + " · Grupo " + g.getClave()
                + " (" + g.getPeriodo().getNombre() + ")",
                inscripcionRepository.findByGrupoId(g.getId()).stream().map(Inscripcion::getAlumno)
                        .map(Alumno::getUsuario).filter(Usuario::isActivo).toList());
    }

    private List<Grupo> gruposDelFacilitador(Usuario yo) {
        return facilitadorRepository.findByUsuarioId(yo.getId())
                .map(f -> grupoRepository.findByFacilitadorId(f.getId()).stream()
                        .filter(g -> !g.getPeriodo().getFechaFin().isBefore(LocalDate.now()))
                        .toList())
                .orElse(List.of());
    }

    private List<Usuario> coordinadores() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && rol(u) == RolSistema.COORDINADOR).toList();
    }

    private void agregar(Map<Integer, Contacto> contactos, Usuario u, Map<Integer, String> nombres, String detalle) {
        contactos.putIfAbsent(u.getId(), new Contacto(u.getId(), nombre(u, nombres), etiquetaRol(rol(u)),
                detalle != null ? detalle : u.getNombreUsuario()));
    }

    /** Nombre para mostrar: alumnos y facilitadores con su nombre; coordinación y jefatura con su cargo. */
    private Map<Integer, String> nombresVisibles() {
        Map<Integer, String> nombres = new HashMap<>();
        alumnoRepository.findAll().forEach(a -> nombres.put(a.getUsuario().getId(), a.nombreCompleto()));
        facilitadorRepository.findAll().forEach((Facilitador f) -> nombres.put(f.getUsuario().getId(), f.nombreCompleto()));
        return nombres;
    }

    private String nombre(Usuario u, Map<Integer, String> nombres) {
        return switch (rol(u)) {
            case COORDINADOR -> "Coordinación de lenguas";
            case JEFA_DGTV -> "Jefatura del DGTV";
            default -> nombres.getOrDefault(u.getId(), u.getNombreUsuario());
        };
    }

    private static String etiquetaRol(RolSistema rol) {
        return switch (rol) {
            case ALUMNO -> "Alumno";
            case FACILITADOR -> "Facilitador";
            case COORDINADOR -> "Coordinador";
            case JEFA_DGTV -> "Jefa del DGTV";
            case ADMIN -> "Administrador";
        };
    }

    private static RolSistema rol(Usuario u) {
        return u.getRol().comoRolSistema();
    }

    private Usuario buscarUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("MSG-006", "No se encontró el usuario autenticado."));
    }

}
