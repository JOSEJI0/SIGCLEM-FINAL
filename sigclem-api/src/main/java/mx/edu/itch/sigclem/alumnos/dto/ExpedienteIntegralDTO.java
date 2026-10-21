package mx.edu.itch.sigclem.alumnos.dto;

import java.util.List;

import mx.edu.itch.sigclem.boletas.dto.BoletaDTO;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.diagnosticos.dto.EvaluacionDiagnosticaDTO;
import mx.edu.itch.sigclem.examenes.dto.ExamenDTO;
import mx.edu.itch.sigclem.lenguasmaternas.dto.RegistroLenguaDTO;
import mx.edu.itch.sigclem.pagos.dto.AutorizacionDTO;

/** Ficha única del alumno para coordinación y supervisión: todo su expediente en una sola consulta. */
public record ExpedienteIntegralDTO(
        ExpedienteDTO expediente,
        List<BoletaDTO> historial,
        List<AutorizacionDTO> cargos,
        List<ExamenDTO> examenes,
        List<EvaluacionDiagnosticaDTO> diagnosticos,
        List<ConstanciaDTO> constancias,
        List<RegistroLenguaDTO> lenguasMaternas) {
}
