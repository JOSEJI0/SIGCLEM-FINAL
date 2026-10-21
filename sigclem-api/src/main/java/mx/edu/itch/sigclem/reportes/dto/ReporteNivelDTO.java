package mx.edu.itch.sigclem.reportes.dto;

public record ReporteNivelDTO(
        Integer numeroNivel,
        String nivel,
        int grupos,
        int alumnosInscritos,
        int calificacionesPendientes,
        Double promedioPublicado,
        int acreditados,
        int noAcreditados) {
}
