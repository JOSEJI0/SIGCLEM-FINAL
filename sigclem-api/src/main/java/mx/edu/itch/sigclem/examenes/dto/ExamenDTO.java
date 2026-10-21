package mx.edu.itch.sigclem.examenes.dto;

import java.time.LocalDateTime;

public record ExamenDTO(
        Integer id,
        String numeroControl,
        String nombreAlumno,
        String carrera,
        String tipo,
        LocalDateTime fechaHoraAplicacion,
        Integer puntajeTotal,
        String observaciones,
        String evaluador,
        Integer idNivelAsignado,
        String nombreNivelAsignado,
        boolean concluido) {
}
