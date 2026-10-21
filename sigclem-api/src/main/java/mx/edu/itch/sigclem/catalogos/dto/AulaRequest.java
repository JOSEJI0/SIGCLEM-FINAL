package mx.edu.itch.sigclem.catalogos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AulaRequest(
        @NotBlank(message = "El nombre del aula es obligatorio")
        @Size(max = 30, message = "El nombre del aula no puede exceder 30 caracteres")
        String nombre) {
}
