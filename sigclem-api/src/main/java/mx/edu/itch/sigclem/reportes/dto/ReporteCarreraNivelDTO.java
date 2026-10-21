package mx.edu.itch.sigclem.reportes.dto;

/** RF-18: aprobación y reprobación de un nivel para los alumnos de una carrera. */
public record ReporteCarreraNivelDTO(
        String carrera,
        Integer numeroNivel,
        String nivel,
        int alumnosInscritos,
        int calificacionesPendientes,
        Double promedioPublicado,
        int acreditados,
        int noAcreditados) {
}
