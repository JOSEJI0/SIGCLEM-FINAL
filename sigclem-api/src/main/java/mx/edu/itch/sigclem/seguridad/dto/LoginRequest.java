package mx.edu.itch.sigclem.seguridad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Ingresa tu usuario") @Size(max = 50) String usuario,
        @NotBlank(message = "Ingresa tu contraseña") @Size(max = 72) String contrasena) {
}
