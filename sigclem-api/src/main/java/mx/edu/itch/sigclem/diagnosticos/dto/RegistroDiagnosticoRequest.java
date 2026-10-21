package mx.edu.itch.sigclem.diagnosticos.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Registro directo por coordinación de una evaluación diagnóstica ya aplicada, con su resultado (RF-22). */
public record RegistroDiagnosticoRequest(
        @NotBlank String numeroControl,
        @NotNull(message = "La fecha y hora de aplicación es obligatoria")
        LocalDateTime fechaHoraAplicacion,
        @NotNull @Min(0) @Max(100) Integer puntaje,
        @NotBlank @Size(max = 20) String recomendacion,
        @Size(max = 10000) String observaciones) {
}
