package mx.edu.itch.sigclem.grupos.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HorarioRequest(
        @NotNull @Min(1) @Max(6) Integer diaSemana,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin) {
}
