package mx.edu.itch.sigclem.grupos.dto;

import java.util.List;
import java.util.Map;

import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;

public record ActaDTO(GrupoDTO grupo, List<AlumnoActaDTO> alumnos) {

    public static ActaDTO desde(Grupo grupo, List<Inscripcion> inscripciones) {
        return desde(grupo, inscripciones, Map.of());
    }

    /** {@code cargos}: cargos del curso por id de autorización, para mostrar el estatus de pago de cada alumno. */
    public static ActaDTO desde(Grupo grupo, List<Inscripcion> inscripciones, Map<Integer, AutorizacionPago> cargos) {
        return new ActaDTO(GrupoDTO.desde(grupo, inscripciones.size()),
                inscripciones.stream()
                        .sorted(java.util.Comparator.comparing(i -> i.getAlumno().nombreCompleto()))
                        .map(i -> AlumnoActaDTO.desde(i,
                                i.getIdAutorizacion() == null ? null : cargos.get(i.getIdAutorizacion())))
                        .toList());
    }
}
