package mx.edu.itch.sigclem.examenes.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Edición de los datos generales de un examen (tipo y fecha de aplicación). */
public record EdicionExamenRequest(
        @NotBlank @Pattern(regexp = "Colocación|Cuatro habilidades",
                message = "El tipo debe ser 'Colocación' o 'Cuatro habilidades'")
        String tipo,
        @NotNull(message = "La fecha y hora de aplicación es obligatoria")
        LocalDateTime fechaHoraAplicacion) {
}
