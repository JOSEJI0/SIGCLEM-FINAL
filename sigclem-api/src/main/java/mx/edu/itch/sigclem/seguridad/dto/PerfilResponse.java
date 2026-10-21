package mx.edu.itch.sigclem.seguridad.dto;

/** {@code hablaLenguaMaterna}: solo para alumnos; decide si se muestra el apartado de lengua materna. */
public record PerfilResponse(Integer id, String usuario, String nombre, String rol, Boolean hablaLenguaMaterna) {
}
