package mx.edu.itch.sigclem.examenes.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registro directo por coordinación de un examen ya aplicado, con su resultado (sin solicitud del alumno). */
public record RegistroExamenRequest(
        @NotBlank String numeroControl,
        @NotBlank @Pattern(regexp = "Colocación|Cuatro habilidades",
                message = "El tipo debe ser 'Colocación' o 'Cuatro habilidades'")
        String tipo,
        @NotNull(message = "La fecha y hora de aplicación es obligatoria")
        LocalDateTime fechaHoraAplicacion,
        @NotNull(message = "El nivel asignado es obligatorio")
        Integer idNivelAsignado,
        @Min(0) @Max(100) Integer puntaje,
        @Size(max = 10000) String observaciones) {
}
