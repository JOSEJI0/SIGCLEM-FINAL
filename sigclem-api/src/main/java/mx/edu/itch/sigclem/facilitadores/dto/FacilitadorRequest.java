package mx.edu.itch.sigclem.facilitadores.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FacilitadorRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede exceder 60 caracteres")
        String nombre,
        @NotBlank(message = "El apellido paterno es obligatorio")
        @Size(max = 40, message = "El apellido paterno no puede exceder 40 caracteres")
        String apellidoPaterno,
        @Size(max = 40, message = "El apellido materno no puede exceder 40 caracteres")
        String apellidoMaterno,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 80, message = "El correo no puede exceder 80 caracteres")
        String correo,
        @Pattern(regexp = "^$|^[0-9+() -]{7,15}$", message = "El teléfono no tiene un formato válido")
        String telefono,
        /** Contraseña que asigna coordinación. Al registrar, si se omite se genera una temporal; al editar,
         *  si se omite se conserva la actual. */
        @Pattern(regexp = "^$|^.{8,72}$", message = "La contraseña debe tener entre 8 y 72 caracteres")
        String contrasena) {
}
