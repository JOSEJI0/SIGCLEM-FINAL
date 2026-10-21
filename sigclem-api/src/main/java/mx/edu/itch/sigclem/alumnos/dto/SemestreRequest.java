package mx.edu.itch.sigclem.alumnos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Semestre que cursa el alumno, capturado por él mismo. */
public record SemestreRequest(
        @NotNull(message = "Indica el semestre que cursas")
        @Min(value = 1, message = "El semestre debe estar entre 1 y 12")
        @Max(value = 12, message = "El semestre debe estar entre 1 y 12") Integer semestre) {
}
