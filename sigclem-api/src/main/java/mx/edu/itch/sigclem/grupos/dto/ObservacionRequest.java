package mx.edu.itch.sigclem.grupos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Observación que deja coordinación al cerrar un acta sin todos los pagos o al asignar un nivel pendiente. */
public record ObservacionRequest(
        @NotBlank(message = "Escribe la observación")
        @Size(min = 10, max = 500, message = "La observación debe tener de 10 a 500 caracteres") String observacion) {
}
