package mx.edu.itch.sigclem.reportes.dto;

import java.time.LocalDate;

public record ReporteLenguaMaternaDTO(
        String numeroControl,
        String nombreCompleto,
        String carrera,
        Integer semestre,
        String sexo,
        String estatusAlumno,
        String lengua,
        String nivelDominio,
        String estatusRegistro,
        LocalDate fechaRegistro) {
}
