package mx.edu.itch.sigclem.diagnosticos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResultadoDiagnosticoRequest(
        @Min(0) @Max(100) int puntaje,
        @NotBlank @Size(max = 20) String recomendacion,
        @Size(max = 10000) String observaciones) {
}
