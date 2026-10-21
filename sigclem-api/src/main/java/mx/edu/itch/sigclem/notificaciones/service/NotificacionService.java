package mx.edu.itch.sigclem.notificaciones.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeRequest;
import mx.edu.itch.sigclem.notificaciones.dto.NotificacionDTO;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<NotificacionDTO> listar(String nombreUsuario) {
        Usuario usuario = buscarUsuario(nombreUsuario);
        List<Notificacion> mensajes = switch (usuario.getRol().comoRolSistema()) {
            case COORDINADOR -> notificacionRepository.findTop100ByOrigenNombreUsuarioOrderByFechaEnvioDesc(nombreUsuario);
            case JEFA_DGTV -> notificacionRepository.findTop100ByDestinoNombreUsuarioOrderByFechaEnvioDesc(nombreUsuario);
            default -> throw new DatosInvalidosException(
                    "NOTI-001", "Solo coordinación y la jefatura del DGTV tienen acceso a esta mensajería.");
        };
        return mensajes.stream().map(NotificacionDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificacionDTO> listarRecibidas(String nombreUsuario) {
        Usuario usuario = buscarUsuario(nombreUsuario);
        RolSistema rol = usuario.getRol().comoRolSistema();
        if (rol != RolSistema.ALUMNO && rol != RolSistema.COORDINADOR) {
            throw new DatosInvalidosException(
                    "NOTI-005", "Solo alumnos y coordinación pueden consultar estas notificaciones.");
        }
        return notificacionRepository.findTop100ByDestinoNombreUsuarioOrderByFechaEnvioDesc(nombreUsuario)
                .stream().map(NotificacionDTO::desde).toList();
    }

    @Transactional
    public NotificacionDTO enviar(MensajeRequest solicitud, String nombreRemitente) {
        Usuario remitente = buscarUsuario(nombreRemitente);
        if (remitente.getRol().comoRolSistema() != RolSistema.COORDINADOR) {
            throw new DatosInvalidosException("NOTI-001", "Solo coordinación puede enviar mensajes a la jefatura.");
        }
        Usuario jefa = usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc(
                        RolSistema.JEFA_DGTV.nombreBd())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "NOTI-002", "No hay una cuenta activa para la jefatura del DGTV."));

        Notificacion notificacion = new Notificacion();
        notificacion.setOrigen(remitente);
        notificacion.setDestino(jefa);
        notificacion.setAsunto(solicitud.asunto().trim());
        notificacion.setMensaje(solicitud.mensaje().trim());
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setLeida(false);
        return NotificacionDTO.desde(notificacionRepository.save(notificacion));
    }

    @Transactional
    public NotificacionDTO marcarLeida(Integer id, String nombreUsuario) {
        Notificacion notificacion = notificacionRepository.findByIdAndDestinoNombreUsuario(id, nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "NOTI-003", "No se encontró el mensaje en la bandeja de este usuario."));
        notificacion.setLeida(true);
        return NotificacionDTO.desde(notificacion);
    }

    private Usuario buscarUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "NOTI-004", "No se encontró el usuario autenticado."));
    }
}
