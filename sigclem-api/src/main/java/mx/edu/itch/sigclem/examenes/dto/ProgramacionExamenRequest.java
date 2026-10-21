package mx.edu.itch.sigclem.examenes.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

/** Programación de fecha y hora del examen por el coordinador (CU-05). */
public record ProgramacionExamenRequest(
        @NotNull(message = "La fecha y hora de aplicación es obligatoria")
        @Future(message = "La fecha y hora debe ser en el futuro")
        LocalDateTime fechaHoraAplicacion,
        Integer idUsuarioEvaluador) {
}
