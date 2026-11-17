package mx.edu.itch.sigclem.reportes.dto;

import java.time.LocalDate;
import java.util.List;

public record ReporteGrupoDTO(
        Integer id,
        String clave,
        String curso,
        String modalidad,
        String aula,
        String facilitador,
        Integer cupo,
        int alumnosInscritos,
        LocalDate fechaInicioCaptura,
        LocalDate fechaFinCaptura,
        String estatusActa,
        LocalDate fechaCierreActa,
        String cerradaPor,
        String observacionCierre,
        List<String> horarios,
        List<ReporteAlumnoGrupoDTO> alumnos) {
}
