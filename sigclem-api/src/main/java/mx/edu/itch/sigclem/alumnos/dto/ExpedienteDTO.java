package mx.edu.itch.sigclem.alumnos.dto;

import java.time.LocalDate;
import java.util.List;

/** Expediente digital con su avance reticular (CU-03). */
public record ExpedienteDTO(
        String numeroControl,
        String nombreCompleto,
        String carrera,
        Integer semestre,
        String sexo,
        LocalDate fechaActualizacionSemestre,
        /** true si el alumno no ha confirmado su semestre en el periodo vigente. */
        boolean semestrePorConfirmar,
        String correo,
        LocalDate fechaInscripcion,
        String nivelActual,
        int nivelesCubiertos,
        List<AvanceDTO> avance,
        boolean hablaLenguaMaterna,
        String estatus,
        String motivoBaja,
        LocalDate fechaBaja,
        /** Cómo liberó la lengua extranjera (Curso, Examen de colocación, Examen global, Lengua materna u Otra); nulo si aún no la libera. */
        String formaLiberacion) {
}
