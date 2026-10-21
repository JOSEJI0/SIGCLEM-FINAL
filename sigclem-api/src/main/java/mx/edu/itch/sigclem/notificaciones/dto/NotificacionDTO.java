package mx.edu.itch.sigclem.notificaciones.dto;

import java.time.LocalDateTime;

import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;

public record NotificacionDTO(
        Integer id,
        String asunto,
        String mensaje,
        String remitente,
        String destinatario,
        LocalDateTime fechaEnvio,
        boolean leida) {

    public static NotificacionDTO desde(Notificacion notificacion) {
        return new NotificacionDTO(
                notificacion.getId(),
                notificacion.getAsunto(),
                notificacion.getMensaje(),
                notificacion.getOrigen() == null ? "Sistema" : notificacion.getOrigen().getNombreUsuario(),
                notificacion.getDestino().getNombreUsuario(),
                notificacion.getFechaEnvio(),
                notificacion.isLeida());
    }
}
