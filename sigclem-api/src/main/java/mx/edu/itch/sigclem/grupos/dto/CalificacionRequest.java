package mx.edu.itch.sigclem.grupos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CalificacionRequest(
        @NotBlank String numeroControl,
        @Min(0) @Max(100) Integer calificacion,
        @Min(0) @Max(100) Integer calificacionSecundaria) {

    public CalificacionRequest(String numeroControl, Integer calificacion) {
        this(numeroControl, calificacion, null);
    }
}
