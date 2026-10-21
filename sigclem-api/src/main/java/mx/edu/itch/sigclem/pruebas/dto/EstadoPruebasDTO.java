package mx.edu.itch.sigclem.pruebas.dto;

/** Indica a la SPA y a Swagger si la API acepta peticiones sin login. */
public record EstadoPruebasDTO(
        boolean modoPruebas,
        String usuarioPorOmision,
        String cabecera,
        String documentacion) {
}
