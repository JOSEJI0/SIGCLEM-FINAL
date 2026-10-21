package mx.edu.itch.sigclem.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensajeRequest(
        @NotBlank @Size(max = 100) String asunto,
        @NotBlank @Size(max = 500) String mensaje) {
}
