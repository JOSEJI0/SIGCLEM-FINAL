package mx.edu.itch.sigclem.reportes.dto;

import java.util.List;

import mx.edu.itch.sigclem.catalogos.dto.PeriodoDTO;

public record ReporteAcademicoDTO(
        PeriodoDTO periodo,
        int grupos,
        int actasCerradas,
        int alumnosInscritos,
        int alumnosUnicos,
        int calificacionesPendientes,
        Double promedioPublicado,
        int acreditados,
        int noAcreditados,
        List<ReporteNivelDTO> porNivel,
        List<ReporteCarreraNivelDTO> porCarreraYNivel) {
}
