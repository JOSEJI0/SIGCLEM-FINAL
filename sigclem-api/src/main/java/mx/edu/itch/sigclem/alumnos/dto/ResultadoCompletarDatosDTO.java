package mx.edu.itch.sigclem.alumnos.dto;

import java.util.List;

public record ResultadoCompletarDatosDTO(
        int filasProcesadas,
        int camposCompletados,
        int alumnosActualizados,
        int facilitadoresActualizados,
        int gruposActualizados,
        int horariosAgregados,
        List<ResultadoCargaDTO.FilaConError> errores) {
}
