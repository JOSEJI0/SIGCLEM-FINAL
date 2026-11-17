package mx.edu.itch.sigclem.reportes.dto;

public record ReporteAlumnoGrupoDTO(
        String numeroControl,
        String nombreCompleto,
        String carrera,
        Integer semestre,
        String sexo,
        String estatusAlumno,
        Integer calificacion,
        String resultado,
        Integer calificacionSecundaria,
        String resultadoSecundario) {
}
