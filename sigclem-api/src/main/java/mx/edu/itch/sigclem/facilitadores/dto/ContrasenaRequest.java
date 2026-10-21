package mx.edu.itch.sigclem.facilitadores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nueva contraseña asignada por coordinación. */
public record ContrasenaRequest(
        @NotBlank(message = "Escribe la nueva contraseña")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String contrasena) {
}
