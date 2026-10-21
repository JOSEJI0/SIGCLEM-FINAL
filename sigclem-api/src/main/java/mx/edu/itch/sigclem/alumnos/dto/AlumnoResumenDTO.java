package mx.edu.itch.sigclem.alumnos.dto;

import java.time.LocalDate;

/** Datos mínimos para la búsqueda administrativa de alumnos (CU-16, CU-21). */
public record AlumnoResumenDTO(
        String numeroControl,
        String nombreCompleto,
        String carrera,
        Integer semestre,
        /** "H", "M" o vacío si no se ha capturado. */
        String sexo,
        /** Última vez que el alumno confirmó su semestre; vacío si nunca lo ha hecho. */
        LocalDate fechaActualizacionSemestre,
        String correo,
        LocalDate fechaInscripcion,
        /** Número del nivel actual (1 a 10); 0 si ya acreditó el idioma. */
        Integer numeroNivelActual,
        String nivelActual,
        boolean hablaLenguaMaterna,
        /** "Activo" o "Baja". */
        String estatus) {
}
