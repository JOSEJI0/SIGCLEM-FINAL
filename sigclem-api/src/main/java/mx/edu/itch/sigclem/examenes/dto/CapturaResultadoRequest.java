package mx.edu.itch.sigclem.examenes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Captura (o corrección) del resultado de un examen: el coordinador asigna directamente el nivel.
 * Ya no se usa la rúbrica por criterios ni la firma del evaluador.
 */
public record CapturaResultadoRequest(
        @NotNull(message = "El nivel asignado es obligatorio")
        Integer idNivelAsignado,
        @Min(value = 0, message = "El puntaje debe estar entre 0 y 100")
        @Max(value = 100, message = "El puntaje debe estar entre 0 y 100")
        Integer puntaje,
        @Size(max = 10000) String observaciones) {
}
