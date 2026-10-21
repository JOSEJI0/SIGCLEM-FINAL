package mx.edu.itch.sigclem.grupos.dto;

import jakarta.validation.constraints.NotBlank;

public record AsignarAlumnoRequest(@NotBlank(message = "Indica el número de control del alumno") String numeroControl) {
}
