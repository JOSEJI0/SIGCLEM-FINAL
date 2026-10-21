package mx.edu.itch.sigclem.pruebas.dto;

/** Cuenta disponible para usarse en la cabecera X-Usuario-Prueba. */
public record UsuarioPruebaDTO(
        Integer id,
        String usuario,
        String nombre,
        String rol,
        boolean activo) {
}
