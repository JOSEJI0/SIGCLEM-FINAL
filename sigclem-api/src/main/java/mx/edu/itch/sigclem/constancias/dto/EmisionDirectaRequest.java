package mx.edu.itch.sigclem.constancias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Coordinación emite una constancia aunque el alumno no la haya solicitado. */
public record EmisionDirectaRequest(
        @NotBlank(message = "Indica el número de control del alumno") String numeroControl,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Z0-9]+",
                message = "El folio debe contener solo letras mayúsculas y números.") String folio) {
}
