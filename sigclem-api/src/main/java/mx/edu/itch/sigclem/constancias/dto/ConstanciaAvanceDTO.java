package mx.edu.itch.sigclem.constancias.dto;

import java.time.LocalDate;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

/** Nivel de inglés acreditado (acta cerrada con calificación aprobatoria) que puede imprimirse como constancia. */
public record ConstanciaAvanceDTO(
        Integer idInscripcion,
        String numeroControl,
        String nombreAlumno,
        String carrera,
        Integer numeroNivel,
        String nivel,
        String periodo,
        String grupo,
        String facilitador,
        Integer calificacion,
        LocalDate fechaCierreActa) {

    public static ConstanciaAvanceDTO desde(Inscripcion inscripcion) {
        var alumno = inscripcion.getAlumno();
        var grupo = inscripcion.getGrupo();
        return new ConstanciaAvanceDTO(inscripcion.getId(), alumno.getNumeroControl(), alumno.nombreCompleto(),
                alumno.getCarrera().getNombre(), grupo.getNivel().getNumero(), grupo.getNivel().getNombre(),
                grupo.getPeriodo().getNombre(), grupo.getClave(), grupo.getFacilitador().nombreCompleto(),
                inscripcion.getCalificacionFinal(), grupo.getFechaCierreActa());
    }
}
