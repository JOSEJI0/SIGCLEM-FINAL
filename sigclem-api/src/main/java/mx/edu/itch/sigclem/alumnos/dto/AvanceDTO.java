package mx.edu.itch.sigclem.alumnos.dto;

/** {@code calificacion}: calificación final del acta cerrada del nivel, o {@code null} si no la tiene. */
public record AvanceDTO(Integer numero, String nombre, String nivelMcer, String estatus, String origen,
        Integer calificacion) {
}
