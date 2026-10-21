package mx.edu.itch.sigclem.examenes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ResultadoCriterioDTO(
        @NotNull Integer idCriterio,
        String nombreCriterio,
        @NotNull @Min(0) @Max(6) Integer puntaje,
        String nota) {
}
