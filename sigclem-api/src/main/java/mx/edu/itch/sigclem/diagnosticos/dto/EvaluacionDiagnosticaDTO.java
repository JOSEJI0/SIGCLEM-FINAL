package mx.edu.itch.sigclem.diagnosticos.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import mx.edu.itch.sigclem.diagnosticos.domain.EvaluacionDiagnostica;

public record EvaluacionDiagnosticaDTO(
        Integer id,
        String numeroControl,
        String nombreAlumno,
        LocalDate fechaSolicitud,
        LocalDateTime fechaHoraAplicacion,
        String enlace,
        Integer puntaje,
        String recomendacion,
        String observaciones,
        String estatus) {

    public static EvaluacionDiagnosticaDTO desde(EvaluacionDiagnostica diagnostico) {
        return new EvaluacionDiagnosticaDTO(
                diagnostico.getId(),
                diagnostico.getAlumno().getNumeroControl(),
                diagnostico.getAlumno().nombreCompleto(),
                diagnostico.getFechaSolicitud(),
                diagnostico.getFechaHoraAplicacion(),
                diagnostico.getEnlace(),
                diagnostico.getPuntaje(),
                diagnostico.getRecomendacion(),
                diagnostico.getObservaciones(),
                diagnostico.getEstatus());
    }
}
