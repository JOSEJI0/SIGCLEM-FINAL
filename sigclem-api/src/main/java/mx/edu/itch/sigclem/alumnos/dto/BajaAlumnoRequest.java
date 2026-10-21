package mx.edu.itch.sigclem.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de la baja de un alumno (terminó el curso, abandonó, u otro). */
public record BajaAlumnoRequest(
        @NotBlank(message = "Indica el motivo de la baja")
        @Size(min = 3, max = 200, message = "El motivo debe tener de 3 a 200 caracteres") String motivo) {
}
