package mx.edu.itch.sigclem.notificaciones.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO de la mensajería entre alumnos, facilitadores y coordinación. */
public final class MensajeriaDTOs {

    private MensajeriaDTOs() {
    }

    /** Persona a la que el usuario puede escribir. */
    public record Contacto(Integer idUsuario, String nombre, String rol, String detalle) {
    }

    /** Envío a un grupo de personas (por ejemplo, todos los alumnos de un grupo). */
    public record Difusion(String clave, String etiqueta, int destinatarios) {
    }

    public record Destinatarios(boolean puedeEnviar, List<Contacto> contactos, List<Difusion> difusiones) {
    }

    /** Se indican personas ({@code idsDestinatarios}), una difusión ({@code difusion}) o ambas. */
    public record EnvioMensaje(
            List<Integer> idsDestinatarios,
            String difusion,
            @NotBlank(message = "Escribe el asunto") @Size(max = 100) String asunto,
            @NotBlank(message = "Escribe el mensaje") @Size(max = 2000) String mensaje) {
    }

    public record ResultadoEnvio(String lote, int destinatarios) {
    }

    /** {@code idRemitente} null = aviso automático del sistema. */
    public record Recibido(Integer id, String asunto, String mensaje, Integer idRemitente, String remitente,
            String rolRemitente, LocalDateTime fechaEnvio, boolean leido) {
    }

    public record Enviado(String clave, String asunto, String mensaje, LocalDateTime fechaEnvio, int destinatarios,
            int leidos, String paraQuien) {
    }
}
