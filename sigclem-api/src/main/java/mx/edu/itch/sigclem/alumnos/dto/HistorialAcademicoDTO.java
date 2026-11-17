package mx.edu.itch.sigclem.alumnos.dto;

import mx.edu.itch.sigclem.alumnos.domain.HistorialAcademico;

/**
 * Nivel cursado según el concentrado histórico (antes de operar en el sistema). Los campos que no venían en el
 * archivo llegan nulos. {@code acreditado}: verdadero con 70 o más, falso con «NA» o menos de 70, nulo si no se sabe.
 */
public record HistorialAcademicoDTO(Long id, String nivel, String periodo, String facilitador, String grupo,
        String horario, String aula, Double calificacion, String calificacionTexto, Boolean acreditado,
        String observacion) {

    public static HistorialAcademicoDTO de(HistorialAcademico h) {
        return new HistorialAcademicoDTO(h.getId(), h.getNivel(), h.getPeriodo(), h.getFacilitador(), h.getGrupo(),
                h.getHorario(), h.getAula(), h.getCalificacion(), h.getCalificacionTexto(), h.getAcreditado(),
                h.getObservacionHistorica());
    }
}
