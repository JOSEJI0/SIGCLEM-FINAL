package mx.edu.itch.sigclem.examenes.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Solicitud de examen por el alumno (CU-04). */
public record SolicitudExamenRequest(
        String numeroControl,
        @NotBlank @Pattern(regexp = "Colocación|Cuatro habilidades", message = "El tipo debe ser 'Colocación' o 'Cuatro habilidades'")
        String tipo,
        LocalDateTime fechaHoraTentativa) {
}
