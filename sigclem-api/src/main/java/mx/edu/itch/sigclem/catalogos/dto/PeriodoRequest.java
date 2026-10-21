package mx.edu.itch.sigclem.catalogos.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PeriodoRequest(
        @NotBlank(message = "El nombre del periodo es obligatorio")
        @Size(max = 30, message = "El nombre del periodo no puede exceder 30 caracteres")
        String nombre,
        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,
        @NotNull(message = "La fecha de fin es obligatoria")
        LocalDate fechaFin) {
}
