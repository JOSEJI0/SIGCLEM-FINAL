package mx.edu.itch.sigclem.facilitadores.dto;

import jakarta.validation.constraints.Pattern;

public record TelefonoRequest(
        @Pattern(regexp = "^$|^[0-9+() -]{7,15}$", message = "Escribe un teléfono de 7 a 15 dígitos") String telefono) {
}
