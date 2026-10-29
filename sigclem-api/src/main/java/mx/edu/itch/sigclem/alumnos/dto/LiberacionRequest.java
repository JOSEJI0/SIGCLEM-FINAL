package mx.edu.itch.sigclem.alumnos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Libera la lengua extranjera de un alumno por una forma distinta a completar los diez niveles.
 * {@code forma}: «Examen global», «Lengua materna» u «Otra». {@code detalle}: referencia opcional (folio, fecha…).
 */
public record LiberacionRequest(
        @NotBlank @Size(max = 60) String forma,
        @Size(max = 200) String detalle) {
}
